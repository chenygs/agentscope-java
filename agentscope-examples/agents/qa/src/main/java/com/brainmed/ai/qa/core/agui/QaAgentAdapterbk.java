//package com.brainmed.ai.qa.core.agui;
//
//import cn.hutool.core.util.StrUtil;
//import com.brainmed.ai.qa.core.middleware.QaLongTermMemoryMiddleware;
//import com.brainmed.ai.qa.core.middleware.QaMessagePersistenceMiddleware;
//import com.brainmed.ai.qa.core.middleware.SearchModeMiddleware;
//import com.brainmed.ai.qa.core.middleware.ThinkingModeMiddleware;
//import com.brainmed.ai.qa.core.middleware.enums.SearchMode;
//import com.brainmed.ai.qa.core.middleware.enums.ThinkingMode;
//import com.brainmed.ai.qa.core.tool.SearchResult;
//import io.agentscope.core.ReActAgent;
//import io.agentscope.core.agent.RuntimeContext;
//import io.agentscope.core.agui.adapter.AguiAdapterConfig;
//import io.agentscope.core.agui.converter.AguiMessageConverter;
//import io.agentscope.core.agui.event.AguiEvent;
//import io.agentscope.core.agui.model.RunAgentInput;
//import io.agentscope.core.event.*;
//import io.agentscope.core.message.Msg;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import reactor.core.publisher.Flux;
//import reactor.core.scheduler.Schedulers;
//
//import java.util.*;
//
///**
// * 自定义 AG-UI 适配器，使用新版 {@code streamEvents} API（{@link AgentEvent} 块级事件流）。
// *
// * <p>相比旧版 {@code agent.stream(msgs, options, context)} 返回 {@code Flux<Event>}，
// * 新版 {@code agent.streamEvents(msgs, context)} 直接返回 {@code Flux<AgentEvent>}，
// * 且 RuntimeContext 直接传给 MiddlewareChain（无需通过 Reactor Context 回读）。
// *
// * <p>事件转换映射：
// * <ul>
// *   <li>{@code TextBlockStart/Delta/End} → TextMessageStart/Content/End</li>
// *   <li>{@code ThinkingBlockStart/Delta/End} → ReasoningMessageStart/Content/End</li>
// *   <li>{@code ToolCallStart/Delta/End} → ToolCallStart/Args/End</li>
// *   <li>{@code ToolResultStart/TextDelta/End} → ToolCallResult</li>
// * </ul>
// */
//public class QaAgentAdapterbk {
//
//    private static final Logger log = LoggerFactory.getLogger(QaAgentAdapterbk.class);
//
//    private final ReActAgent agent;
//    private final AguiAdapterConfig config;
//    private final AguiMessageConverter messageConverter;
//    /** 续写模式累积的追加文本，供 controller 在流结束后 append 到原 partial 记录。 */
//    private final StringBuilder continuedText = new StringBuilder();
//
//    public QaAgentAdapterbk(ReActAgent agent, AguiAdapterConfig config) {
//        this.agent = Objects.requireNonNull(agent, "agent cannot be null");
//        this.config = Objects.requireNonNull(config, "config cannot be null");
//        this.messageConverter = new AguiMessageConverter();
//    }
//
//    /** 运行 agent 并产出 AG-UI 事件流（默认 AUTO 搜索 + NORMAL 思考）。 */
//    public Flux<AguiEvent> run(RunAgentInput input, String userId, String sessionId) {
//        return run(input, userId, sessionId, SearchMode.AUTO, ThinkingMode.NORMAL);
//    }
//
//    /** 运行 agent 并产出 AG-UI 事件流，支持指定搜索模式。 */
//    public Flux<AguiEvent> run(RunAgentInput input, String userId, String sessionId, SearchMode searchMode) {
//        return run(input, userId, sessionId, searchMode, ThinkingMode.NORMAL);
//    }
//
//    /**
//     * 运行 agent 并产出 AG-UI 事件流，支持指定搜索模式和思考模式。
//     */
//    public Flux<AguiEvent> run(RunAgentInput input, String userId, String sessionId,
//                               SearchMode searchMode, ThinkingMode thinkingMode) {
//        return run(input, userId, sessionId, searchMode, thinkingMode, null);
//    }
//
//    /**
//     * 运行 agent 并产出 AG-UI 事件流，支持指定搜索模式、思考模式与续写模式。
//     *
//     * <p>续写模式（{@code continueMessageId != null}）用于"继续生成"：前端被中断的
//     * assistant 消息已存在，故不发射 {@code TextMessageStart/End}，仅以该 messageId
//     * 发射 {@code TextMessageContent}（追加 delta）。累积的追加文本存入
//     * {@link #continuedText}，供 controller 在流结束后 append 到原 partial 记录。
//     */
//    public Flux<AguiEvent> run(RunAgentInput input, String userId, String sessionId,
//                               SearchMode searchMode, ThinkingMode thinkingMode,
//                               String continueMessageId) {
//        return Flux.defer(() -> runWithMsgs(
//                messageConverter.toMsgList(input.getMessages()),
//                userId, sessionId, input.getThreadId(), input.getRunId(),
//                searchMode, thinkingMode, continueMessageId));
//    }
//
//    /**
//     * 直接以 {@link Msg} 列表运行（绕过 {@code RunAgentInput}/{@code AguiMessageConverter}），
//     * 供续写场景 controller 直接传入从 DB 取出的 partial Msg。
//     */
//    public Flux<AguiEvent> runWithMsgs(List<Msg> msgs, String userId, String sessionId,
//                                       String threadId, String runId,
//                                       SearchMode searchMode, ThinkingMode thinkingMode,
//                                       String continueMessageId) {
//        return Flux.defer(() -> {
//            // 透传 userId/sessionId/runId/searchMode/thinkingMode/agentId
//            RuntimeContext context = RuntimeContext.builder()
//                    .userId(userId)
//                    .sessionId(sessionId)
//                    .put(QaLongTermMemoryMiddleware.AGENT_ID_CONTEXT_KEY,
//                            StrUtil.isBlank(agent.getName()) ? "" : agent.getName())
//                    .put(QaMessagePersistenceMiddleware.RUN_ID_CONTEXT_KEY, runId)
//                    .put(SearchModeMiddleware.SEARCH_MODE_KEY, searchMode)
//                    .put(ThinkingModeMiddleware.THINKING_MODE_KEY, thinkingMode)
//                    .build();
//
//            // 续写模式标记：messagePersistence 中间件据此跳过落库
//            if (continueMessageId != null) {
//                context.put(QaMessagePersistenceMiddleware.CONTINUE_MESSAGE_ID_KEY, continueMessageId);
//            }
//
//            EventConversionState state = new EventConversionState(threadId, runId);
//            state.continueMessageId = continueMessageId;
//
//            return Flux.concat(
//                            Flux.just(new AguiEvent.RunStarted(threadId, runId)),
//                            // streamEvents 新版 API：直接传 context，返回 Flux<AgentEvent>
//                            agent.streamEvents(msgs, context)
//                                    .subscribeOn(Schedulers.boundedElastic())
//                                    .concatMapIterable(event -> convertEvent(event, state)),
//                            Flux.defer(() -> finishRun(state, context)))
//                    .onErrorResume(error -> {
//                        String errorMessage = error.getMessage() != null
//                                ? error.getMessage()
//                                : error.getClass().getSimpleName();
//                        return Flux.just(
//                                new AguiEvent.Raw(threadId, runId, Map.of("error", errorMessage)),
//                                new AguiEvent.RunFinished(threadId, runId));
//                    });
//        });
//    }
//
//    // ──────────────────────────────────────────────
//    // 事件转换：AgentEvent → AguiEvent
//    // ──────────────────────────────────────────────
//
//    private List<AguiEvent> convertEvent(AgentEvent event, EventConversionState state) {
//        List<AguiEvent> events = new ArrayList<>();
//
//        switch (event) {
//            // ── 文本块 ──
//            case TextBlockStartEvent e -> {
//                String msgId = e.getReplyId();
//                log.info("[AGUI] TextBlockStart replyId={}, blockId={}, id={}", e.getReplyId(), e.getBlockId(), e.getId());
//                if (msgId != null && !state.hasStartedText(msgId)) {
//                    // 开始新文本块前，关闭活跃的 reasoning
//                    closeActiveReasoning(state, events);
//                    // 续写模式：前端消息已存在，不发 Start（否则会重建气泡）
//                    if (state.continueMessageId == null) {
//                        events.add(new AguiEvent.TextMessageStart(
//                                state.threadId, state.runId, msgId, "assistant"));
//                    }
//                    state.startText(msgId);
//                }
//            }
//            case TextBlockDeltaEvent e -> {
//                String delta = e.getDelta();
//                if (delta != null && !delta.isEmpty()) {
//                    String msgId = e.getReplyId();
//                    if (msgId != null) {
//                        // 续写模式：用被中断消息的 messageId 发射，前端追加到原气泡
//                        String emitId = state.continueMessageId != null
//                                ? state.continueMessageId : msgId;
//                        events.add(new AguiEvent.TextMessageContent(
//                                state.threadId, state.runId, emitId, delta));
//                        if (state.continueMessageId != null) {
//                            continuedText.append(delta);
//                        }
//                    }
//                }
//            }
//            case TextBlockEndEvent e -> {
//                String msgId = e.getReplyId();
//                if (msgId != null && state.hasStartedText(msgId) && !state.hasEndedText(msgId)) {
//                    // 续写模式：不发 End（前端消息保持追加态，由 RunFinished 收尾）
//                    if (state.continueMessageId == null) {
//                        events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, msgId));
//                    }
//                    state.endText(msgId);
//                }
//            }
//
//            // ── 思考块 ──
//            case ThinkingBlockStartEvent e -> {
//                if (config.isEnableReasoning()) {
//                    String msgId = "think_" + e.getReplyId();
//                    if (!state.hasStartedReasoning(msgId)) {
//                        // 开始新思考块前，关闭活跃的文本消息
//                        closeActiveText(state, events);
//                        events.add(new AguiEvent.ReasoningMessageStart(
//                                state.threadId, state.runId, msgId, "reasoning"));
//                        state.startReasoning(msgId);
//                    }
//                }
//            }
//            case ThinkingBlockDeltaEvent e -> {
//                if (config.isEnableReasoning()) {
//                    String delta = e.getDelta();
//                    if (delta != null && !delta.isEmpty()) {
//                        events.add(new AguiEvent.ReasoningMessageContent(
//                                state.threadId, state.runId,
//                                "think_" + e.getReplyId(), delta));
//                    }
//                }
//            }
//            case ThinkingBlockEndEvent e -> {
//                if (config.isEnableReasoning()) {
//                    String msgId = "think_" + e.getReplyId();
//                    if (state.hasStartedReasoning(msgId) && !state.hasEndedReasoning(msgId)) {
//                        events.add(new AguiEvent.ReasoningMessageEnd(
//                                state.threadId, state.runId, msgId));
//                        state.endReasoning(msgId);
//                    }
//                }
//            }
//
//            // ── 工具调用 ──
//            case ToolCallStartEvent e -> {
//                String toolCallId = e.getToolCallId();
//                if (toolCallId != null && !state.hasStartedToolCall(toolCallId)) {
//                    // 关闭活跃消息
//                    closeActiveText(state, events);
//                    closeActiveReasoning(state, events);
//                    events.add(new AguiEvent.ToolCallStart(
//                            state.threadId, state.runId,
//                            toolCallId, e.getToolCallName()));
//                    state.startToolCall(toolCallId);
//                }
//            }
//            case ToolCallDeltaEvent e -> {
//                if (config.isEmitToolCallArgs()) {
//                    String delta = e.getDelta();
//                    if (delta != null && !delta.isEmpty()) {
//                        events.add(new AguiEvent.ToolCallArgs(
//                                state.threadId, state.runId,
//                                e.getToolCallId(), delta));
//                    }
//                }
//            }
//            case ToolCallEndEvent e -> {
//                // ToolCallEnd 仅在 ToolCallResult 时由我们统一发射
//                // 这里不做处理，等 ToolResultStartEvent 时再关闭
//            }
//
//            // ── 工具结果 ──
//            case ToolResultStartEvent e -> {
//                String toolCallId = e.getToolCallId();
//                if (toolCallId != null) {
//                    // 确保 ToolCallStart 已发射（防御性）
//                    if (!state.hasStartedToolCall(toolCallId)) {
//                        String toolName = e.getToolCallName() != null
//                                ? e.getToolCallName() : "unknown";
//                        events.add(new AguiEvent.ToolCallStart(
//                                state.threadId, state.runId, toolCallId, toolName));
//                        state.startToolCall(toolCallId);
//                    }
//                    // 确保 ToolCallEnd 已发射（关闭参数流式阶段）
//                    if (!state.hasEndedToolCall(toolCallId)) {
//                        events.add(new AguiEvent.ToolCallEnd(
//                                state.threadId, state.runId, toolCallId));
//                        state.endToolCall(toolCallId);
//                    }
//                    // 重置工具结果累积器
//                    state.resetToolResultAcc();
//                }
//            }
//            case ToolResultTextDeltaEvent e -> {
//                String delta = e.getDelta();
//                if (delta != null) {
//                    state.appendToolResult(delta);
//                }
//            }
//            case ToolResultEndEvent e -> {
//                String toolCallId = e.getToolCallId();
//                if (toolCallId != null) {
//                    String result = state.getToolResultAccumulated();
//                    events.add(new AguiEvent.ToolCallResult(
//                            state.threadId, state.runId,
//                            toolCallId, result, "tool", e.getReplyId()));
//                }
//            }
//
//            // ── 其他事件忽略 ──
//            default -> { }
//        }
//
//        return events;
//    }
//
//    // ──────────────────────────────────────────────
//    // 辅助：关闭活跃消息
//    // ──────────────────────────────────────────────
//
//    private void closeActiveText(EventConversionState state, List<AguiEvent> events) {
//        String activeId = state.getActiveTextId();
//        if (activeId != null && !state.hasEndedText(activeId)) {
//            events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, activeId));
//            state.endText(activeId);
//        }
//    }
//
//    private void closeActiveReasoning(EventConversionState state, List<AguiEvent> events) {
//        String activeId = state.getActiveReasoningId();
//        if (activeId != null && !state.hasEndedReasoning(activeId)) {
//            events.add(new AguiEvent.ReasoningMessageEnd(
//                    state.threadId, state.runId, activeId));
//            state.endReasoning(activeId);
//        }
//    }
//
//    // ──────────────────────────────────────────────
//    // 收尾：关闭所有未结束的事件 + CITATIONS + RUN_FINISHED
//    // ──────────────────────────────────────────────
//
//    private Flux<AguiEvent> finishRun(EventConversionState state, RuntimeContext context) {
//        List<AguiEvent> events = new ArrayList<>();
//
//        // 关闭未结束的文本消息（续写模式跳过：前端消息不收 End）
//        for (String msgId : state.getStartedTexts()) {
//            if (!state.hasEndedText(msgId) && state.continueMessageId == null) {
//                events.add(new AguiEvent.TextMessageEnd(state.threadId, state.runId, msgId));
//            }
//        }
//
//        // 关闭未结束的工具调用
//        for (String toolCallId : state.getStartedToolCalls()) {
//            if (!state.hasEndedToolCall(toolCallId)) {
//                events.add(new AguiEvent.ToolCallEnd(state.threadId, state.runId, toolCallId));
//            }
//        }
//
//        // 关闭未结束的推理消息
//        for (String msgId : state.getStartedReasonings()) {
//            if (!state.hasEndedReasoning(msgId)) {
//                events.add(new AguiEvent.ReasoningMessageEnd(
//                        state.threadId, state.runId, msgId));
//            }
//        }
//
//        // 从 RuntimeContext 读本轮引用（工具累加），发 CUSTOM CITATIONS 给前端
//        @SuppressWarnings("unchecked")
//        List<SearchResult.Citation> ctxCitations = context.get(
//                QaMessagePersistenceMiddleware.CITATIONS_KEY, List.class);
//        List<SearchResult.Citation> citations = ctxCitations != null
//                ? (List<SearchResult.Citation>) ctxCitations
//                : java.util.Collections.emptyList();
//        if (!citations.isEmpty()) {
//            log.info("Emitting CUSTOM CITATIONS event with {} citations", citations.size());
//            events.add(new AguiEvent.Custom(
//                    state.threadId, state.runId, "CITATIONS", citations));
//        } else {
//            log.info("No citations accumulated, skipping CITATIONS event");
//        }
//
//        events.add(new AguiEvent.RunFinished(state.threadId, state.runId));
//        return Flux.fromIterable(events);
//    }
//
//    /** 返回续写模式累积的追加文本（流结束后供 controller append 到原 partial 记录）。 */
//    public String getContinuedText() {
//        return continuedText.toString();
//    }
//
//    // ──────────────────────────────────────────────
//    // 事件转换状态追踪
//    // ──────────────────────────────────────────────
//
//    private static class EventConversionState {
//        final String threadId;
//        final String runId;
//
//        /** 续写模式：被中断 assistant 消息的 messageId；非 null 时跳过 TextMessageStart/End。 */
//        String continueMessageId;
//
//        // 文本消息（blockId → started/ended）
//        private final Set<String> startedTexts = new LinkedHashSet<>();
//        private final Set<String> endedTexts = new LinkedHashSet<>();
//        private String activeTextId;
//
//        // 推理消息（"think_" + blockId → started/ended）
//        private final Set<String> startedReasonings = new LinkedHashSet<>();
//        private final Set<String> endedReasonings = new LinkedHashSet<>();
//        private String activeReasoningId;
//
//        // 工具调用（toolCallId → started/ended）
//        private final Set<String> startedToolCalls = new LinkedHashSet<>();
//        private final Set<String> endedToolCalls = new LinkedHashSet<>();
//
//        // 工具结果文本累积
//        private final StringBuilder toolResultAcc = new StringBuilder();
//
//        EventConversionState(String threadId, String runId) {
//            this.threadId = threadId;
//            this.runId = runId;
//        }
//
//        // ── 文本 ──
//        boolean hasStartedText(String id) { return startedTexts.contains(id); }
//        boolean hasEndedText(String id) { return endedTexts.contains(id); }
//        String getActiveTextId() { return activeTextId; }
//        Set<String> getStartedTexts() { return startedTexts; }
//
//        void startText(String id) {
//            startedTexts.add(id);
//            activeTextId = id;
//        }
//
//        void endText(String id) {
//            endedTexts.add(id);
//            if (Objects.equals(id, activeTextId)) activeTextId = null;
//        }
//
//        // ── 推理 ──
//        boolean hasStartedReasoning(String id) { return startedReasonings.contains(id); }
//        boolean hasEndedReasoning(String id) { return endedReasonings.contains(id); }
//        String getActiveReasoningId() { return activeReasoningId; }
//        Set<String> getStartedReasonings() { return startedReasonings; }
//
//        void startReasoning(String id) {
//            startedReasonings.add(id);
//            activeReasoningId = id;
//        }
//
//        void endReasoning(String id) {
//            endedReasonings.add(id);
//            if (Objects.equals(id, activeReasoningId)) activeReasoningId = null;
//        }
//
//        // ── 工具调用 ──
//        boolean hasStartedToolCall(String id) { return startedToolCalls.contains(id); }
//        boolean hasEndedToolCall(String id) { return endedToolCalls.contains(id); }
//        Set<String> getStartedToolCalls() { return startedToolCalls; }
//
//        void startToolCall(String id) { startedToolCalls.add(id); }
//        void endToolCall(String id) { endedToolCalls.add(id); }
//
//        // ── 工具结果累积 ──
//        void resetToolResultAcc() { toolResultAcc.setLength(0); }
//        void appendToolResult(String delta) { toolResultAcc.append(delta); }
//        String getToolResultAccumulated() {
//            return toolResultAcc.isEmpty() ? null : toolResultAcc.toString();
//        }
//    }
//}
