package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.memory.LongTermMemoryProvider;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 长期记忆中间件：通过 {@link LongTermMemoryProvider} 策略接口实现跨会话记忆。
 *
 * <p>推理前：调用 {@link LongTermMemoryProvider#search} 检索记忆，注入到消息列表中。
 * <p>推理后：调用 {@link LongTermMemoryProvider#record} 异步记录用户消息。
 *
 * <p>具体的记忆后端（Mem0 / Viking 记忆库等）由 Provider 实现类决定，
 * 中间件本身不关心底层实现。通过 application.yml 的 {@code memory.provider} 配置切换。
 */
@Order(300)
public class QaLongTermMemoryMiddleware implements MiddlewareBase {

    /** RuntimeContext 中存储 agentId 的 key，与 QaAgentAdapter 约定一致。 */
    public static final String AGENT_ID_CONTEXT_KEY = "agentId";

    private static final Logger log = LoggerFactory.getLogger(QaLongTermMemoryMiddleware.class);

    private final LongTermMemoryProvider provider;
    private final boolean enabled;

    public QaLongTermMemoryMiddleware(LongTermMemoryProvider provider, boolean enabled) {
        this.provider = provider;
        this.enabled = enabled;
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        if (!enabled) {
            return next.apply(input);
        }

        return Flux.deferContextual(cv -> {
            RuntimeContext rc = resolveContext(ctx, cv);
            String userId = rc.getUserId();
            if (userId == null || userId.isBlank()) {
                return next.apply(input);
            }
            String agentId = (String) rc.get(AGENT_ID_CONTEXT_KEY);

            Msg lastUserMsg = extractLastUserMsg(input.msgs());
            if (lastUserMsg == null) {
                return next.apply(input);
            }

            log.info("[长期记忆][{}] 开始检索 userId={}, agentId={}, query={}",
                    provider.name(), userId, agentId, lastUserMsg.getTextContent());

            return provider.search(lastUserMsg, userId, agentId)
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(text -> log.info("[长期记忆][{}] 检索结果 userId={}, agentId={}, length={}, preview={}",
                            provider.name(), userId, agentId, text.length(),
                            text.length() > 100 ? text.substring(0, 100) : text))
                    .filter(text -> !text.isBlank())
                    .map(memoryText -> {
                        Msg memoryMsg = Msg.builder()
                                .role(MsgRole.USER)
                                .name("long_term_memory")
                                .content(TextBlock.builder()
                                        .text(wrapMemory(memoryText))
                                        .build())
                                .build();
                        List<Msg> enhanced = new ArrayList<>(input.msgs());
                        enhanced.add(memoryMsg);
                        return new AgentInput(enhanced);
                    })
                    .defaultIfEmpty(input)
                    .publishOn(Schedulers.boundedElastic())
                    .flatMapMany(enrichedInput -> {
                        if (enrichedInput.msgs().size() <= input.msgs().size()) {
                            log.info("[长期记忆][{}] 无记忆 userId={}", provider.name(), userId);
                        }

                        List<Msg> userMsgs = input.msgs() != null
                                ? input.msgs().stream()
                                  .filter(m -> m.getRole() == MsgRole.USER)
                                  .toList()
                                : List.of();

                        // 捕获 Agent 最终回复文本
                        AtomicReference<String> assistantTextRef = new AtomicReference<>("");

                        return next.apply(enrichedInput)
                                .doOnNext(event -> {
                                    if (event instanceof AgentResultEvent resultEvent) {
                                        Msg result = resultEvent.getResult();
                                        String text = extractText(result);
                                        if (!text.isBlank()) {
                                            assistantTextRef.set(text);
                                        }
                                    }
                                })
                                .doOnComplete(() -> {
                                    String assistantText = assistantTextRef.get();
                                    if (!userMsgs.isEmpty() && !assistantText.isBlank()) {
                                        recordAsync(userId, userMsgs, assistantText, agentId);
                                    } else {
                                        log.info("[长期记忆][{}] 跳过记录 userId={}: userMsgs={}, assistantText blank={}",
                                                provider.name(), userId, userMsgs.size(), assistantText.isBlank());
                                    }
                                });
                    })
                    .doOnError(e -> log.warn("[长期记忆][{}] 异常 userId={}: {}",
                            provider.name(), userId, e.getMessage()));
        });
    }

    private void recordAsync(String userId, List<Msg> userMsgs, String assistantText, String agentId) {
        provider.record(userId, userMsgs, assistantText, agentId)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.info("[长期记忆][{}] 记录完成 userId={}, agentId={}", provider.name(), userId, agentId))
                .onErrorResume(e -> {
                    log.warn("[长期记忆][{}] 记录失败 userId={}: {}", provider.name(), userId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    private static Msg extractLastUserMsg(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) return null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i).getRole() == MsgRole.USER) {
                return msgs.get(i);
            }
        }
        return null;
    }

    /** 提取 Msg 中所有 TextBlock 的文本拼接。 */
    private static String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private static String wrapMemory(String text) {
        return "Below is content retrieved from the long-term memory associated with the current"
                + " user. Please extract useful information from it in the context of the"
                + " current conversation.\n<long_term_memory>\n"
                + text
                + "\n</long_term_memory>";
    }

    private static RuntimeContext resolveContext(RuntimeContext ctx, reactor.util.context.ContextView cv) {
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx;
        }
        Object rc = cv.getOrDefault(AgentBase.RUNTIME_CONTEXT_KEY, null);
        return rc instanceof RuntimeContext real ? real : (ctx != null ? ctx : RuntimeContext.empty());
    }
}
