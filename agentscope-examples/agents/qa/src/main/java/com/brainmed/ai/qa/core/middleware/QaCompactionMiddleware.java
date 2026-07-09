package com.brainmed.ai.qa.core.middleware;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import io.agentscope.harness.agent.memory.compaction.TokenCounterUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Flux;

/**
 * 上下文压缩中间件，仿照 AgentScope harness 的
 * {@code io.agentscope.harness.agent.middleware.CompactionMiddleware}，
 * 但去掉了文件系统依赖，适配本项目的 Web / Redis / 多租户架构。
 *
 * <p><b>与官方实现的差异：</b>
 * <ul>
 *   <li>官方构造器强制依赖 {@code WorkspaceManager}（管理本地 {@code memory/}、{@code sessions/}
 *       目录），用于把长期记忆与全量原始对话写到磁盘文件。本类完全不碰文件系统。</li>
 *   <li>复用官方 {@link ConversationCompactor}（prune、参数截断、安全切分、LLM 摘要逻辑逐字一致），
 *       但构造时传入 {@code null} 作为 {@code MemoryFlushManager}：仅当
 *       {@code flushBeforeCompact}/{@code offloadBeforeCompact} 为 true 时才会解引用它，
 *       而本类把两个开关都关掉，故传 null 安全。</li>
 *   <li>压缩结果（{@code [摘要] + 保留尾部}）写回 {@link AgentState} 的 context，本项目由
 *       {@code RedisAgentStateStore} 持久化，因此天然落到 Redis，而非磁盘文件。</li>
 * </ul>
 *
 * <p><b>触发时机：</b>在每次 LLM 推理（{@link #onReasoning}）前，估算当前会话 token 数，
 * 超过 {@code 模型上下文窗口 × triggerRatio}（默认 0.75）即触发压缩。模型若不报告窗口大小
 * （DeepSeek 的 {@code getContextWindowSize()} 返回 0），则退回固定阈值
 * {@link #FALLBACK_TRIGGER_TOKENS}。
 */
@Order(600)
public class QaCompactionMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(QaCompactionMiddleware.class);

    /** 模型不报告上下文窗口时的兜底触发阈值（约 64K 窗口的 75%）。 */
    public static final int FALLBACK_TRIGGER_TOKENS = 49_152;

    /** 保留尾部的 token 预算上下限与比例（与官方动态默认一致）。 */
    private static final int KEEP_TOKENS_MIN = 2_000;
    private static final int KEEP_TOKENS_MAX = 8_000;
    private static final double KEEP_TOKENS_RATIO = 0.25;

    private final Model model;
    private final double triggerRatio;

    /** 默认按上下文窗口的 75% 触发压缩。 */
    public QaCompactionMiddleware(Model model) {
        this(model, 0.75);
    }

    /**
     * @param model        用于估算窗口大小并执行摘要的模型（与 agent 主模型共享同一实例）
     * @param triggerRatio 触发比例，相对模型上下文窗口（如 0.75 表示用满 75% 时压缩）
     */
    public QaCompactionMiddleware(Model model, double triggerRatio) {
        this.model = model;
        this.triggerRatio = triggerRatio;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        if (!(agent instanceof ReActAgent reActAgent)) {
            return next.apply(input);
        }
        final RuntimeContext rc = ctx != null ? ctx : RuntimeContext.empty();

        return Flux.defer(
                () -> {
                    List<Msg> messages = input.messages();
                    Msg systemMsg = null;
                    List<Msg> conversation;
                    if (messages != null
                            && !messages.isEmpty()
                            && messages.get(0).getRole() == MsgRole.SYSTEM) {
                        systemMsg = messages.get(0);
                        conversation = new ArrayList<>(messages.subList(1, messages.size()));
                    } else {
                        conversation = messages != null ? new ArrayList<>(messages) : List.of();
                    }

                    String agentId = agent.getName();
                    String sessionId =
                            rc.getSessionId() != null ? rc.getSessionId() : "default";

                    CompactionConfig config = buildConfig();

                    // 观测：每轮推理前打印当前会话估算 token 与触发阈值，便于确认中间件在运行及何时触发
                    // （debug 级别，生产默认不打；排查时把本类日志级别开到 DEBUG 即可）
                    if (log.isDebugEnabled()) {
                        int estTokens = TokenCounterUtil.calculateToken(conversation);
                        log.debug(
                                "[compaction] session={} ctxTokens={} trigger={}",
                                sessionId,
                                estTokens,
                                config.getTriggerTokens());
                    }

                    // flushManager 传 null：flush/offload 均已关闭，ConversationCompactor 不会解引用它
                    ConversationCompactor compactor = new ConversationCompactor(model, null);
                    final Msg sys = systemMsg;

                    return compactor
                            .compactIfNeeded(rc, conversation, config, agentId, sessionId)
                            .flatMapMany(
                                    optResult -> {
                                        if (optResult.isEmpty()) {
                                            return next.apply(input);
                                        }
                                        List<Msg> compacted = optResult.get();
                                        applyToContext(
                                                RuntimeContext.resolveAgentState(rc, reActAgent),
                                                compacted);
                                        log.debug(
                                                "Compacted to {} messages before reasoning",
                                                compacted.size());
                                        List<Msg> newMessages = new ArrayList<>();
                                        if (sys != null) {
                                            newMessages.add(sys);
                                        }
                                        newMessages.addAll(compacted);
                                        return next.apply(
                                                new ReasoningInput(
                                                        newMessages,
                                                        input.tools(),
                                                        input.options()));
                                    })
                            .onErrorResume(
                                    e -> {
                                        log.warn(
                                                "Compaction failed, continuing without compaction:"
                                                        + " {}",
                                                e.getMessage());
                                        return next.apply(input);
                                    });
                });
    }

    /**
     * 按模型上下文窗口动态计算触发阈值与保留尾部预算。
     *
     * <p>仅按 token 触发（{@code triggerMessages=0} 禁用消息数触发），符合"上下文超过 N% 才压缩"
     * 的语义；flush/offload 关闭，确保不写任何文件。
     */
    private CompactionConfig buildConfig() {
        int window = model.getContextWindowSize();

        int triggerTokens;
        int keepTokens;
        if (window > 0) {
            triggerTokens = (int) (window * triggerRatio);
            keepTokens =
                    Math.min(
                            KEEP_TOKENS_MAX,
                            Math.max(KEEP_TOKENS_MIN, (int) (window * KEEP_TOKENS_RATIO)));
        } else {
            triggerTokens = FALLBACK_TRIGGER_TOKENS;
            keepTokens = KEEP_TOKENS_MAX;
            log.debug(
                    "Model does not report context window, using fallback trigger: {}",
                    triggerTokens);
        }

        return CompactionConfig.builder()
                .triggerMessages(0) // 禁用按消息数触发，只按 token 触发
                .triggerTokens(triggerTokens)
                .keepTokens(keepTokens)
                .flushBeforeCompact(false) // 不抽取长期记忆到文件（长期记忆暂缓）
                .offloadBeforeCompact(false) // 不把原始对话备份到文件
                .build();
    }

    /** 把压缩后的消息写回 AgentState 的 context（本项目由 Redis 持久化）。 */
    private static void applyToContext(AgentState state, List<Msg> compacted) {
        if (state == null) {
            log.warn("Cannot apply compacted messages: AgentState is null");
            return;
        }
        try {
            List<Msg> ctx = state.contextMutable();
            ctx.clear();
            ctx.addAll(compacted);
            log.debug("Applied compacted messages to state ({} messages)", compacted.size());
        } catch (Exception e) {
            log.warn("Failed to apply compacted messages to state: {}", e.getMessage());
        }
    }
}
