package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.agui.QaAgentAdapter;
import com.brainmed.ai.qa.service.ConversationMessageService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * AgentScope 事件持久化中间件：统一负责所有消息落库。
 *
 * <p>遵循 AgentScope 2.0 设计原则：单次 call 调用产出的事件序列汇聚成恰好一条 assistant Msg。
 * {@code AgentResultEvent} 在 {@code onAgent} 层可见，携带最终 assistant 消息。
 *
 * <p>中断场景：用户主动中断时，框架仍会补发一条 {@code AgentResultEvent}，但其内容是
 * "I noticed that you have interrupted me..." 确认消息，{@code generateReason=MODEL_STOP}，
 * 与正常完成同值，且终止信号都可能是 {@code cancel}（前端收完流即断开），故无法靠 generateReason
 * 或 signal 区分。解决方案：{@code onModelCall} 累积流式 {@code TextBlockDeltaEvent}，
 * {@code doFinally} 中比较"finalMsg 文本是否以累积内容为前缀"——正常完成时流式 delta 即 finalMsg
 * 的 TextBlock（前缀匹配），中断时确认消息与真实 partial 无关（不匹配）→ 据此裁决存 finalMsg 还是 partial。
 *
 * <p>三个钩子覆盖完整事件生命周期：
 * <ul>
 *   <li>{@link #onAgent} — 落库用户消息 + 暂存 AgentResultEvent，doFinally 裁决存 finalMsg/partial</li>
 *   <li>{@link #onActing} — 累积工具结果文本增量，{@code ToolResultEndEvent} 时存完整 tool result 消息</li>
 *   <li>{@link #onModelCall} — 累积流式文本 delta，供中断场景恢复 partial</li>
 * </ul>
 *
 * <p>AG-UI adapter 只做协议转换，不碰落库。
 *
 * @see <a href="../../../../../../../../docs/middleware-message-persistence.md">消息持久化设计文档</a>
 */
@Order(700)
public class QaMessagePersistenceMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(QaMessagePersistenceMiddleware.class);

//    public static final String RUN_ID_CONTEXT_KEY = "qa.runId";

    /** 引用来源在 RuntimeContext 中的 key，工具写入、中间件/adapter 读取。 */
    public static final String CITATIONS_KEY = "qa.citations";

    /** 续写模式标记 key：RuntimeContext 中存被中断消息的 messageId，非空时中间件跳过落库。 */
    public static final String CONTINUE_MESSAGE_ID_KEY = "qa.continueMessageId";

    /** 流式累积器在 RuntimeContext / Reactor Context 中的 key */
    private static final String STREAMING_ACC_KEY = "qa.streamingAcc";
    private static final String RC_STREAMING_ACC = "qa.rc.streamingAcc";

    /** 工具调用累积器在 RuntimeContext / Reactor Context 中的 key */
    private static final String TOOL_CALL_ACC_KEY = "qa.toolCallAcc";
    private static final String RC_TOOL_CALL_ACC = "qa.rc.toolCallAcc";

    private final ConversationMessageService persister;

    public QaMessagePersistenceMiddleware(ConversationMessageService persister) {
        this.persister = persister;
    }

    // ──────────────────────────────────────────────
    // onAgent：落库用户消息 + 捕获 AgentResultEvent 存 assistant 消息
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        if (persister == null) {
            return next.apply(input);
        }

        return Flux.deferContextual(
                cv -> {
                    RuntimeContext rc = resolveContext(ctx, cv);
                    // 续写模式：不落库（续写内容由 controller append 到原 partial 记录）
                    if (rc.get(CONTINUE_MESSAGE_ID_KEY, String.class) != null) {
                        return next.apply(input);
                    }
                    String userId = rc.getUserId();
                    String sessionId = rc.getSessionId();
                    String runId = rc.get(QaAgentAdapter.RUNTIME_CONTEXT_RUN_ID_KEY, String.class);
                    AtomicInteger seq = new AtomicInteger(0);
                    StreamingAccumulator streamingAcc = new StreamingAccumulator();
                    ToolCallAccumulator toolCallAcc = new ToolCallAccumulator();
                    // 暂存 AgentResultEvent 携带的 finalMsg，不立即落库；
                    // 由 doFinally 比较"finalMsg 文本是否以流式累积内容为前缀"来裁决存哪条：
                    // 正常完成存 finalMsg，中断时丢弃框架补发的确认消息、改存 partial。
                    AtomicReference<Msg> pendingResult = new AtomicReference<>();

                    // 落库用户消息（跳过中间件注入的合成消息）
                    if (input.msgs() != null) {
                        for (Msg msg : input.msgs()) {
                            if (msg.getRole() == MsgRole.USER && !isSynthetic(msg)) {
                                persister.persistAsync(
                                        userId, sessionId, runId, msg,
                                        seq.getAndIncrement(), LocalDateTime.now());
                            }
                        }
                    }

                    return next.apply(input)
                            .contextWrite(c -> {
                                // 流式累积器和工具调用累积器同时写入 Reactor Context 和 RuntimeContext，
                                // 确保 onModelCall/onActing 在任一种 context 传播方式下都能拿到
                                c = c.put(STREAMING_ACC_KEY, streamingAcc);
                                c = c.put(TOOL_CALL_ACC_KEY, toolCallAcc);
                                Object existing = c.getOrDefault(
                                        AgentBase.RUNTIME_CONTEXT_KEY, null);
                                if (existing instanceof RuntimeContext realRc) {
                                    realRc.put(RC_STREAMING_ACC, streamingAcc);
                                    realRc.put(RC_TOOL_CALL_ACC, toolCallAcc);
                                }
                                return c;
                            })
                            .doOnNext(event -> {
                                if (event instanceof AgentResultEvent resultEvent) {
                                    // 暂存，不立即落库。正常完成与中断补发的确认消息 generateReason 都是 MODEL_STOP、
                                    // 终止信号也都可能是 cancel，无法靠它们区分；改由 doFinally 用"finalMsg 文本是否
                                    // 与流式累积内容一致"来裁决。
                                    pendingResult.set(resultEvent.getResult());
                                }
                            })
                            .doFinally(signal -> {
                                // 1. 持久化工具调用（assistant + ToolUseBlock）
                                //    在文本落库之前，匹配实时事件流顺序: toolCalls 在 text 之前
                                if (toolCallAcc.hasContent()) {
                                    List<ToolUseBlock> toolCalls = toolCallAcc.drainAll();
                                    Msg toolCallMsg = Msg.builder()
                                            .role(MsgRole.ASSISTANT)
                                            .content(new ArrayList<>(toolCalls))
                                            .build();
                                    persister.persistAsync(
                                            userId, sessionId, runId, toolCallMsg,
                                            seq.getAndIncrement(), LocalDateTime.now());
                                }

                                // 2. 现有文本落库逻辑
                                Msg finalMsg = pendingResult.get();
                                String partial = streamingAcc.buildText();
                                // 裁决该存哪条：
                                // - 正常完成：流式 delta 即 finalMsg 的 TextBlock，故 finalMsg 文本以累积内容为前缀 → 存 finalMsg
                                // - 中断：框架补发 "I noticed..." 确认消息，与已累积的真实 partial 无关 → 存 partial
                                boolean persistFinal = false;
                                if (finalMsg != null && hasContent(finalMsg)) {
                                    String finalText = extractText(finalMsg);
                                    // partial 为空（极早中断/纯工具调用）时无法比对，按 finalMsg 处理
                                    persistFinal = partial.isBlank() || finalText.startsWith(partial);
                                }
                                if (persistFinal) {
                                    String citationsJson = drainCitationsJson(rc);
                                    persister.persistAsync(
                                            userId, sessionId, runId, finalMsg,
                                            seq.getAndIncrement(), LocalDateTime.now(), citationsJson);
                                } else if (!partial.isBlank()) {
                                    log.info("[持久化] 中断部分回复落库(信号={}) userId={} session={} runId={} len={}",
                                            signal, userId, sessionId, runId, partial.length());
                                    Msg partialMsg = Msg.builder()
                                            .role(MsgRole.ASSISTANT)
                                            .content(List.of(TextBlock.builder()
                                                    .text(partial).build()))
                                            .build();
                                    persister.persistAsync(
                                            userId, sessionId, runId, partialMsg,
                                            seq.getAndIncrement(), LocalDateTime.now());
                                }
                            });
                });
    }

    private static RuntimeContext resolveContext(
            RuntimeContext ctx, reactor.util.context.ContextView cv) {
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx;
        }
        Object rc = cv.getOrDefault(AgentBase.RUNTIME_CONTEXT_KEY, null);
        return rc instanceof RuntimeContext real ? real : (ctx != null ? ctx : RuntimeContext.empty());
    }

    /** 从 RuntimeContext 取本轮引用并序列化为 JSON（供落库到 finalMsg 行）。 */
    @SuppressWarnings("unchecked")
    private static String drainCitationsJson(RuntimeContext rc) {
        Object obj = rc.get(CITATIONS_KEY);
        if (!(obj instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        try {
            return new com.google.gson.Gson().toJson(list);
        } catch (Exception e) {
            log.warn("引用来源序列化失败: {}", e.toString());
            return null;
        }
    }

    // ──────────────────────────────────────────────
    // onModelCall：累积流式文本 delta + 捕获 ToolCall 事件
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        if (persister == null) {
            return next.apply(input);
        }

        return Flux.deferContextual(
                cv -> {
                    StreamingAccumulator textAcc = findStreamingAcc(ctx, cv);
                    ToolCallAccumulator toolCallAcc = findToolCallAcc(ctx, cv);
                    return next.apply(input).doOnNext(event -> {
                        textAcc.handleEvent(event);
                        toolCallAcc.handleEvent(event);
                    });
                });
    }

    @SuppressWarnings("unchecked")
    private static StreamingAccumulator findStreamingAcc(
            RuntimeContext ctx, reactor.util.context.ContextView cv) {
        // 优先从 Reactor Context 拿
        Object fromReactor = cv.getOrDefault(STREAMING_ACC_KEY, null);
        if (fromReactor instanceof StreamingAccumulator sa) {
            return sa;
        }
        // 再从 RuntimeContext 拿
        if (ctx != null) {
            Object fromRc = ctx.get(RC_STREAMING_ACC);
            if (fromRc instanceof StreamingAccumulator sa) {
                return sa;
            }
        }
        // 都没有就新建一个空的（正常完成时不会被使用）
        return new StreamingAccumulator();
    }

    @SuppressWarnings("unchecked")
    private static ToolCallAccumulator findToolCallAcc(
            RuntimeContext ctx, reactor.util.context.ContextView cv) {
        Object fromReactor = cv.getOrDefault(TOOL_CALL_ACC_KEY, null);
        if (fromReactor instanceof ToolCallAccumulator tca) {
            return tca;
        }
        if (ctx != null) {
            Object fromRc = ctx.get(RC_TOOL_CALL_ACC);
            if (fromRc instanceof ToolCallAccumulator tca) {
                return tca;
            }
        }
        return new ToolCallAccumulator();
    }

    // ──────────────────────────────────────────────
    // onActing：工具结果落库
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        if (persister == null) {
            return next.apply(input);
        }

        return Flux.deferContextual(
                cv -> {
                    RuntimeContext rc = resolveContext(ctx, cv);
                    // 续写模式：不落库工具结果
                    if (rc.get(CONTINUE_MESSAGE_ID_KEY, String.class) != null) {
                        return next.apply(input);
                    }
                    String userId = rc.getUserId();
                    String sessionId = rc.getSessionId();
                    String runId = rc.get(QaAgentAdapter.RUNTIME_CONTEXT_RUN_ID_KEY, String.class);
                    ToolResultAccumulator accumulator =
                            new ToolResultAccumulator(userId, sessionId, runId);

                    return next.apply(input).doOnNext(accumulator::handleEvent);
                });
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    private static boolean hasContent(Msg msg) {
        return msg.getContent() != null && !msg.getContent().isEmpty();
    }

    /** 提取 Msg 中所有 TextBlock 的文本拼接，用于与流式累积内容比对。 */
    private static String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 判断是否为中间件注入的合成消息（如长期记忆），不应落库。
     */
    private static boolean isSynthetic(Msg msg) {
        if ("long_term_memory".equals(msg.getName())) {
            return true;
        }
        Map<String, Object> meta = msg.getMetadata();
        return meta != null && Boolean.TRUE.equals(meta.get("agentscope_synthetic"));
    }

    // ──────────────────────────────────────────────
    // 内部类：流式文本累积器（仅用于中断场景恢复 partial）
    // ──────────────────────────────────────────────

    static final class StreamingAccumulator {
        private final List<String> textDeltas = new ArrayList<>();

        void handleEvent(AgentEvent event) {
            if (event instanceof TextBlockDeltaEvent deltaEvent) {
                String d = deltaEvent.getDelta();
                if (d != null && !d.isEmpty()) {
                    textDeltas.add(d);
                }
            }
        }

        String buildText() {
            if (textDeltas.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String d : textDeltas) {
                sb.append(d);
            }
            return sb.toString();
        }
    }

    // ──────────────────────────────────────────────
    // 内部类：工具调用累积器（ToolCall 事件 → ToolUseBlock）
    // ──────────────────────────────────────────────

    static final class ToolCallAccumulator {
        private final Map<String, String> toolCallNames = new LinkedHashMap<>();
        private final Map<String, StringBuilder> argumentDeltas = new LinkedHashMap<>();
        private final List<ToolUseBlock> completedToolCalls = new ArrayList<>();

        void handleEvent(AgentEvent event) {
            if (event instanceof ToolCallDeltaEvent delta) {
                String id = delta.getToolCallId();
                if (id == null) {
                    return;
                }
                toolCallNames.putIfAbsent(id, delta.getToolCallName());
                argumentDeltas
                        .computeIfAbsent(id, k -> new StringBuilder())
                        .append(delta.getDelta() != null ? delta.getDelta() : "");
            } else if (event instanceof ToolCallEndEvent end) {
                String id = end.getToolCallId();
                if (id == null) {
                    return;
                }
                String name = end.getToolCallName() != null
                        ? end.getToolCallName()
                        : toolCallNames.getOrDefault(id, "unknown");
                StringBuilder argsSb = argumentDeltas.get(id);
                String argsJson = argsSb != null ? argsSb.toString() : "{}";
                Map<String, Object> input = parseArgsJson(argsJson);
                completedToolCalls.add(new ToolUseBlock(id, name, input));
                argumentDeltas.remove(id);
            }
        }

        List<ToolUseBlock> drainAll() {
            List<ToolUseBlock> result = new ArrayList<>(completedToolCalls);
            completedToolCalls.clear();
            toolCallNames.clear();
            argumentDeltas.clear();
            return result;
        }

        boolean hasContent() {
            return !completedToolCalls.isEmpty();
        }

        void reset() {
            completedToolCalls.clear();
            toolCallNames.clear();
            argumentDeltas.clear();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> parseArgsJson(String json) {
            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                return Map.of();
            }
            try {
                return new com.google.gson.Gson().fromJson(json, Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        }
    }

    // ──────────────────────────────────────────────
    // 内部类：工具结果累积器
    // ──────────────────────────────────────────────

    private final class ToolResultAccumulator {
        private final String userId;
        private final String sessionId;
        private final String runId;
        private final Map<String, StringBuilder> textByToolCallId = new LinkedHashMap<>();
        private final Map<String, String> toolNameByToolCallId = new LinkedHashMap<>();

        private ToolResultAccumulator(String userId, String sessionId, String runId) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.runId = runId;
        }

        private void handleEvent(AgentEvent event) {
            if (event instanceof ToolResultTextDeltaEvent deltaEvent) {
                String toolCallId = deltaEvent.getToolCallId();
                if (toolCallId == null || toolCallId.isBlank()) {
                    return;
                }
                textByToolCallId
                        .computeIfAbsent(toolCallId, ignored -> new StringBuilder())
                        .append(deltaEvent.getDelta() != null ? deltaEvent.getDelta() : "");
                toolNameByToolCallId.putIfAbsent(toolCallId, deltaEvent.getToolCallName());
            } else if (event instanceof ToolResultEndEvent endEvent) {
                String toolCallId = endEvent.getToolCallId();
                if (toolCallId == null || toolCallId.isBlank()) {
                    return;
                }
                StringBuilder text = textByToolCallId.get(toolCallId);
                String toolName =
                        endEvent.getToolCallName() != null
                                ? endEvent.getToolCallName()
                                : toolNameByToolCallId.get(toolCallId);

                TextBlock textBlock =
                        TextBlock.builder()
                                .text(text != null ? text.toString() : "")
                                .build();
                ToolResultBlock toolResult =
                        ToolResultBlock.builder()
                                .id(toolCallId)
                                .name(toolName != null ? toolName : "unknown")
                                .output(List.of(textBlock))
                                .build();
                Msg msg =
                        Msg.builder()
                                .id(endEvent.getReplyId() != null
                                        ? endEvent.getReplyId()
                                        : toolCallId)
                                .role(MsgRole.TOOL)
                                .content(List.of(toolResult))
                                .build();
                persister.persistAsync(
                        userId, sessionId, runId, msg, 0, LocalDateTime.now());
            }
        }
    }
}
