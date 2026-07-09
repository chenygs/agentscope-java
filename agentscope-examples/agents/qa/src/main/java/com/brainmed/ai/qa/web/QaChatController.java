package com.brainmed.ai.qa.web;

import com.brainmed.ai.qa.core.agui.QaAgentAdapter;
import com.brainmed.ai.qa.core.middleware.enums.SearchMode;
import com.brainmed.ai.qa.core.middleware.enums.ThinkingMode;
import com.brainmed.ai.qa.core.util.JwtTokenUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.spring.boot.agui.common.AguiProperties;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 自定义流式问答接口。
 *
 * <p>仿照 AgentScope AG-UI 的 {@code AguiRequestProcessor} + {@code AguiAgentAdapter}，
 * 自己实现请求处理与事件转换（见 {@link QaAgentAdapter}），事件序列、SSE 格式与
 * {@code /agui/run} 一致；返回 {@code Flux<ServerSentEvent<String>>} 便于后续组装数据。
 *
 * <p>与官方实现的差异：透传 {@code userId}（鉴权得到）+ {@code sessionId}（前端 threadId），
 * 使短期记忆（AgentStateStore / Redis）按用户与会话隔离，而非全部归入匿名默认 slot。
 */
@RestController
public class QaChatController {

    private static final Logger logger = LoggerFactory.getLogger(QaChatController.class);

    private final AguiAgentRegistry registry;
    private final AguiProperties props;
    private final AguiAdapterConfig config;
    private final AguiEventEncoder encoder = new AguiEventEncoder();

    public QaChatController(
            AguiAgentRegistry registry,
            AguiProperties props) {
        this.registry = registry;
        this.props = props;
        this.config =
                AguiAdapterConfig.builder()
                        .toolMergeMode(props.getDefaultToolMergeMode())
                        .runTimeout(props.getRunTimeout())
                        .emitStateEvents(props.isEmitStateEvents())
                        .emitToolCallArgs(props.isEmitToolCallArgs())
                        .enableReasoning(props.isEnableReasoning())
                        .defaultAgentId(props.getDefaultAgentId())
                        .build();
    }

    /**
     * 流式问答。
     *
     * @param input         AG-UI 运行输入
     * @param headerAgentId 请求头中的 agentId（可选）
     * @return SSE 事件流，格式与 /agui/run 一致
     */
    @PostMapping(value = "/qa/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestBody RunAgentInput input,
            @RequestHeader(value = "X-Agent-Id", required = false) String headerAgentId) {
        // TODO 鉴权：后续在此接入 sa-token，从登录态解析真实 userId
        String userId = JwtTokenUtils.resolveUserId();

        String threadId = input.getThreadId();
        String runId = input.getRunId();

        // 解析 agentId 并取 agent（仿照 AguiRequestProcessor.resolveAgentId）
        String agentId = resolveAgentId(input, headerAgentId);
        ReActAgent agent = resolveAgent(agentId);

        // 解析搜索模式：从 forwardedProps 读取，默认 AUTO（模型自主判断）
        SearchMode searchMode = resolveSearchMode(input);
        // 解析思考模式：从 forwardedProps 读取，默认 NORMAL
        ThinkingMode thinkingMode = resolveThinkingMode(input);

        // 短期记忆由 Redis（AgentStateStore）按 userId/sessionId 持久化，
        // agent 每次请求会从 Redis 加载历史，故前端只需发最后一条用户消息（与官方 server-side-memory 一致）。
        RunAgentInput effectiveInput = extractLatestUserMessage(input);

        // sessionId 用前端 threadId，userId 用鉴权结果
        QaAgentAdapter adapter = new QaAgentAdapter(agent, config);

        Flux<AguiEvent> events = adapter.run(input, userId, searchMode, thinkingMode);

        return events.map(event -> toSse(event, threadId, runId))
                .onErrorResume(
                        e -> {
                            logger.error("Error processing /qa/chat: {}", e.getMessage());
                            return Flux.just(
                                    toSse(
                                            new AguiEvent.Raw(
                                                    threadId,
                                                    runId,
                                                    Map.of("error", e.getMessage())),
                                            threadId,
                                            runId),
                                    toSse(new AguiEvent.RunFinished(threadId, runId), threadId, runId));
                        })
                .doOnCancel(
                        () -> interruptAgent(agent, userId, threadId, runId, agentId));
    }

    /**
     * 主动停止当前流式回答。
     */
    @PostMapping(value = "/qa/chat/interrupt", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<QaChatInterruptData> interrupt(@RequestBody QaChatInterruptRequest request) {
        String userId = JwtTokenUtils.resolveUserId();
        String threadId = request != null ? request.threadId() : null;
        String runId = request != null ? request.runId() : null;

        if (!StringUtils.hasText(threadId)) {
            return ApiResult.fail("threadId is required");
        }

        String agentId =
                request != null && StringUtils.hasText(request.agentId())
                        ? request.agentId()
                        : config.getDefaultAgentId() != null ? config.getDefaultAgentId() : "default";

        ReActAgent agent;
        try {
            agent = resolveAgent(agentId);
        } catch (Exception e) {
            logger.warn(
                    "Failed to resolve agent for interrupt, userId={}, threadId={}, runId={}, agentId={}: {}",
                    userId,
                    threadId,
                    runId,
                    agentId,
                    e.getMessage(),
                    e);
            return ApiResult.fail("agent not found: " + agentId);
        }

        boolean interrupted = interruptAgent(agent, userId, threadId, runId, agentId);
        if (!interrupted) {
            return ApiResult.fail("interrupt failed");
        }

        return ApiResult.success(
                new QaChatInterruptData(threadId, runId, true), "interrupted");
    }

    /**
     * 解析搜索模式：从 forwardedProps.searchMode 读取，不区分大小写，无效值回退 AUTO。
     * <ul>
     *   <li>{@code AUTO}（默认）— 模型自主判断是否搜索、用哪个工具</li>
     *   <li>{@code WEB} — 仅联网搜索</li>
     *   <li>{@code KNOWLEDGE} — 仅知识库搜索</li>
     *   <li>{@code NONE} — 禁用所有搜索</li>
     * </ul>
     */
    private SearchMode resolveSearchMode(RunAgentInput input) {
        Object prop = input.getForwardedProp("searchMode");
        if (prop == null) {
            return SearchMode.AUTO;
        }
        return SearchMode.fromString(prop.toString());
    }

    /**
     * 解析思考模式：从 forwardedProps.thinkingMode 读取，不区分大小写，无效值回退 NORMAL。
     */
    private ThinkingMode resolveThinkingMode(RunAgentInput input) {
        Object prop = input.getForwardedProp("thinkingMode");
        if (prop == null) {
            return ThinkingMode.NORMAL;
        }
        return ThinkingMode.fromString(prop.toString());
    }

    /**
     * 解析 agentId（仿照 AguiRequestProcessor.resolveAgentId，去掉 pathAgentId 分支）。
     * 优先级：HTTP header > forwardedProps.agentId > config 默认 > "default"。
     */
    private String resolveAgentId(RunAgentInput input, String headerAgentId) {
        if (headerAgentId != null && !headerAgentId.isEmpty()) {
            return headerAgentId;
        }
        Object agentIdProp = input.getForwardedProp("agentId");
        if (agentIdProp != null) {
            return agentIdProp.toString();
        }
        if (config.getDefaultAgentId() != null) {
            return config.getDefaultAgentId();
        }
        return "default";
    }

    /**
     * 仅取最后一条用户消息（仿照 AguiRequestProcessor.extractLatestUserMessage）。
     * 历史已由 Redis 短期记忆维护，故只需发最新一条。
     */
    private RunAgentInput extractLatestUserMessage(RunAgentInput input) {
        List<AguiMessage> messages = input.getMessages();
        if (messages == null || messages.isEmpty()) {
            return input;
        }
        AguiMessage lastUserMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AguiMessage msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                lastUserMessage = msg;
                break;
            }
        }
        if (lastUserMessage == null) {
            return input;
        }
        return RunAgentInput.builder()
                .threadId(input.getThreadId())
                .runId(input.getRunId())
                .messages(List.of(lastUserMessage))
                .tools(input.getTools())
                .context(input.getContext())
                .forwardedProps(input.getForwardedProps())
                .build();
    }

    public record ApiResult<T>(int code, T data, String msg) {
        public static <T> ApiResult<T> success(T data, String msg) {
            return new ApiResult<>(200, data, msg);
        }

        public static <T> ApiResult<T> fail(String msg) {
            return new ApiResult<>(500, null, msg);
        }
    }

    public record QaChatInterruptRequest(String threadId, String runId, String agentId) {
    }

    public record QaChatInterruptData(String threadId, String runId, boolean interrupted) {
    }

    private ReActAgent resolveAgent(String agentId) {
        return (ReActAgent)
                registry.getAgent(agentId)
                        .orElseThrow(
                                () -> new IllegalStateException("Agent not found: " + agentId));
    }

    private boolean interruptAgent(
            ReActAgent agent, String userId, String threadId, String runId, String agentId) {
        if (!StringUtils.hasText(threadId)) {
            logger.info(
                    "Skip interrupt because threadId is blank, userId={}, runId={}, agentId={}",
                    userId,
                    runId,
                    agentId);
            return false;
        }
        try {
            agent.interrupt(userId, threadId);
//            agent.handleInterrupt(InterruptContext.builder().build()).block();
            logger.info(
                    "Interrupted agent, userId={}, threadId={}, runId={}, agentId={}",
                    userId,
                    threadId,
                    runId,
                    agentId);
            return true;
        } catch (Exception e) {
            logger.warn(
                    "Failed to interrupt agent, userId={}, threadId={}, runId={}, agentId={}: {}",
                    userId,
                    threadId,
                    runId,
                    agentId,
                    e.getMessage(),
                    e);
            return false;
        }
    }

    /**
     * 把单个 AG-UI 事件编码为 {@link ServerSentEvent}。
     *
     * <p>后续如需在每个事件上追加自定义字段，可在此处组装。
     */
    private ServerSentEvent<String> toSse(AguiEvent event, String threadId, String runId) {
        // encoder.encodeToJson 返回带前导空格的 JSON，与 starter 的 /agui/run 输出一致
        return ServerSentEvent.<String>builder().data(encoder.encodeToJson(event).trim()).build();
    }
}
