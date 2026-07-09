package com.brainmed.ai.qa.core.middleware;

import com.brainmed.ai.qa.core.agui.QaAgentAdapter;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Flux;

/**
 * 继承 AgentScope 的 OtelTracingMiddleware，增强其创建的 Span 业务属性。
 *
 * <p><b>核心原理</b>：父类各 hook 在 {@code Flux.defer} 中创建 span 并 {@code makeCurrent()}，
 * 然后在 {@code try (Scope)} 内调用 {@code next.apply(input)}。此时 OTel ThreadLocal 有效。
 * <b>必须在组装阶段（而非订阅阶段）捕获 Span 引用</b>，因为 {@code try (Scope)} 退出后
 * OTel ThreadLocal 即被清除。
 *
 * <p><b>RuntimeContext 解析</b>：旧版 {@code stream(msgs, options, context)} API 将
 * RuntimeContext 放入 Reactor Context（通过 {@code withRuntimeContext}），但传给
 * MiddlewareChain 的 ctx 参数可能为 null。因此使用 {@code Flux.deferContextual}
 * 在订阅阶段从 Reactor Context 回读。
 */
@Order(200)
public class QaOtelEnrichMiddleware extends OtelTracingMiddleware {

    private static final Logger log = LoggerFactory.getLogger(QaOtelEnrichMiddleware.class);

    // ──────────────────────────────────────────────
    // onAgent: 增强 invoke_agent span
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {

        // ★ 在组装阶段捕获 Span（此时 OTel ThreadLocal 有效）
        Function<AgentInput, Flux<AgentEvent>> enrichedNext = agentInput -> {
            Span span = Span.fromContext(Context.current());
            boolean recording = span.isRecording();
            log.info("[OtelEnrich] onAgent assembly span.recording={}", recording);

            // 使用 deferContextual 在订阅阶段读取 Reactor Context 中的 RuntimeContext
            return Flux.deferContextual(cv -> {
                RuntimeContext rc = resolveContext(ctx, cv);
                log.info("[OtelEnrich] onAgent subscribe userId={}, sessionId={}",
                        rc.getUserId(), rc.getSessionId());

                enrichBusinessAttributes(span, rc);

                String userMessage = extractLastUserText(agentInput.msgs());
                if (!userMessage.isBlank()) {
                    String truncated = userMessage.length() > 2000
                            ? userMessage.substring(0, 2000) + "..." : userMessage;
                    span.setAttribute("gen_ai.request.message", truncated);
                    span.setAttribute("gen_ai.prompt",
                            "[{\"role\":\"user\",\"content\":" + JSONUtil.toJsonStr(truncated) + "}]");
                }

                return next.apply(agentInput)
                        .doOnNext(event -> {
                            if (event instanceof AgentResultEvent resultEvent) {
                                String responseText = extractText(resultEvent.getResult());
                                if (!responseText.isBlank()) {
                                    String truncated = responseText.length() > 4000
                                            ? responseText.substring(0, 4000) + "..." : responseText;
                                    span.setAttribute("gen_ai.response.message", truncated);
                                    span.setAttribute("gen_ai.completion",
                                            "[{\"role\":\"assistant\",\"content\":" + JSONUtil.toJsonStr(truncated) + "}]");
                                }
                            }
                        });
            });
        };

        return super.onAgent(agent, ctx, input, enrichedNext);
    }

    // ──────────────────────────────────────────────
    // onModelCall: 增强 chat span（模型调用）
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {

        // ★ 在组装阶段捕获 Span
        Function<ModelCallInput, Flux<AgentEvent>> enrichedNext = modelCallInput -> {
            Span span = Span.fromContext(Context.current());
            log.info("[OtelEnrich] onModelCall assembly span.recording={}", span.isRecording());

            return Flux.deferContextual(cv -> {
                RuntimeContext rc = resolveContext(ctx, cv);
                enrichBusinessAttributes(span, rc);
                return next.apply(modelCallInput);
            });
        };

        return super.onModelCall(agent, ctx, input, enrichedNext);
    }

    // ──────────────────────────────────────────────
    // onActing: 增强 execute_tool span（工具输入/输出）
    // ──────────────────────────────────────────────

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {

        // ★ 在组装阶段捕获 Span
        Function<ActingInput, Flux<AgentEvent>> enrichedNext = actingInput -> {
            Span span = Span.fromContext(Context.current());

            String toolNames = actingInput.toolCalls() != null
                    ? actingInput.toolCalls().stream()
                      .map(ToolUseBlock::getName)
                      .reduce((a, b) -> a + "," + b).orElse("unknown")
                    : "unknown";

            log.info("[OtelEnrich] onActing assembly span.recording={}, tools={}",
                    span.isRecording(), toolNames);

            return Flux.deferContextual(cv -> {
                RuntimeContext rc = resolveContext(ctx, cv);
                enrichBusinessAttributes(span, rc);

                // 写入工具输入参数
                boolean hasInput = false;
                if (actingInput.toolCalls() != null) {
                    for (ToolUseBlock toolCall : actingInput.toolCalls()) {
                        if (toolCall.getInput() != null && !toolCall.getInput().isEmpty()) {
                            String inputJson;
                            try {
                                inputJson = JSONUtil.toJsonStr(toolCall.getInput());
                            } catch (Exception e) {
                                inputJson = toolCall.getInput().toString();
                            }
                            span.setAttribute("gen_ai.tool.input", inputJson);
                            span.setAttribute("gen_ai.prompt", inputJson);
                            hasInput = true;
                        }
                    }
                }
                if (!hasInput) {
                    span.setAttribute("gen_ai.prompt", "{}");
                }

                // 累积工具输出结果
                Map<String, StringBuilder> resultAcc = new HashMap<>();

                return next.apply(actingInput)
                        .doOnNext(event -> {
                            if (event instanceof ToolResultTextDeltaEvent deltaEvent) {
                                String id = deltaEvent.getToolCallId();
                                if (id != null) {
                                    resultAcc.computeIfAbsent(id, k -> new StringBuilder())
                                            .append(deltaEvent.getDelta() != null
                                                    ? deltaEvent.getDelta() : "");
                                }
                            } else if (event instanceof ToolResultEndEvent endEvent) {
                                StringBuilder allOutput = new StringBuilder();
                                resultAcc.forEach((id, sb) -> {
                                    if (!allOutput.isEmpty()) allOutput.append("\n---\n");
                                    allOutput.append(sb);
                                });
                                if (!allOutput.isEmpty()) {
                                    String output = allOutput.toString();
                                    if (output.length() > 8000) {
                                        output = output.substring(0, 8000) + "...(truncated)";
                                    }
                                    span.setAttribute("gen_ai.tool.output", output);
                                    span.setAttribute("gen_ai.completion", output);
                                }
                            }
                        });
            });
        };

        return super.onActing(agent, ctx, input, enrichedNext);
    }

    // ──────────────────────────────────────────────
    // RuntimeContext 解析
    // ──────────────────────────────────────────────

    private static RuntimeContext resolveContext(
            RuntimeContext ctx, reactor.util.context.ContextView cv) {
        // 优先使用直接传入的 ctx（新版 API / streamEvents / onModelCall / onActing 路径）
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx;
        }
        // 回退：从 Reactor Context 读取（旧版 stream API 通过 withRuntimeContext 放入）
        Object rc = cv.getOrDefault(AgentBase.RUNTIME_CONTEXT_KEY, null);
        if (rc instanceof RuntimeContext real) {
            return real;
        }
        return ctx != null ? ctx : RuntimeContext.empty();
    }

    // ──────────────────────────────────────────────
    // 公共业务属性：写入每个 span
    // ──────────────────────────────────────────────

    private static void enrichBusinessAttributes(Span span, RuntimeContext rc) {
        if (rc == null) return;

        String userId = rc.getUserId();
        if (userId != null && !userId.isBlank()) {
            span.setAttribute("user.id", userId);
            span.setAttribute("langfuse.user.id", userId);
        }

        String sessionId = rc.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            span.setAttribute("session.id", sessionId);
            span.setAttribute("langfuse.session.id", sessionId);
        }

        Object agentIdObj = rc.get(QaLongTermMemoryMiddleware.AGENT_ID_CONTEXT_KEY);
        if (agentIdObj instanceof String agentId && !agentId.isBlank()) {
            span.setAttribute("gen_ai.agent.id", agentId);
        }

        Object searchModeObj = rc.get(SearchModeMiddleware.SEARCH_MODE_KEY);
        if (searchModeObj instanceof String searchMode && !searchMode.isBlank()) {
            span.setAttribute("app.search_mode", searchMode);
        }

        Object runIdObj = rc.get(QaAgentAdapter.RUNTIME_CONTEXT_RUN_ID_KEY);
        if (runIdObj instanceof String runId && !runId.isBlank()) {
            span.setAttribute("app.run_id", runId);
        }
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    private static String extractLastUserText(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) return "";
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg msg = msgs.get(i);
            if (msg.getRole() == MsgRole.USER
                    && !"long_term_memory".equals(msg.getName())) {
                String text = msg.getTextContent();
                return text != null ? text : "";
            }
        }
        return "";
    }

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
}
