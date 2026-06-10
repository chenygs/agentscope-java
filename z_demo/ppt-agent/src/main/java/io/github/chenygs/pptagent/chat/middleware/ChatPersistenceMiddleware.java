package io.github.chenygs.pptagent.chat.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.github.chenygs.pptagent.chat.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 自动持久化对话消息的 Middleware。
 *
 * <p>RC2 中 {@code onAgent} 仅在 {@code streamEvents()} 路径使用，而 AG-UI 适配器走
 * {@code stream()} → {@code call()} 路径，因此改用 {@link #onReasoning} 钩子：
 * <ol>
 *   <li>首次 reasoning 时保存 user 消息（从 {@link ReasoningInput#messages()} 提取）</li>
 *   <li>通过事件流 {@code TEXT_BLOCK_DELTA} 累积 assistant 回复</li>
 *   <li>流结束时保存完整 assistant 消息</li>
 * </ol>
 */
@Slf4j
public class ChatPersistenceMiddleware implements MiddlewareBase {

    private final ChatSessionService chatSessionService;

    /**
     * 标记首次 reasoning 是否已处理 user 消息
     */
    private final ThreadLocal<Boolean> userMsgSaved = ThreadLocal.withInitial(() -> false);

    /**
     * 累积 assistant 文本内容
     */
    private final ThreadLocal<StringBuilder> assistantContent = ThreadLocal.withInitial(StringBuilder::new);

    /**
     * 标记 assistant 内容已保存，避免重复保存
     */
    private final ThreadLocal<Boolean> assistantSaved = ThreadLocal.withInitial(() -> false);

    public ChatPersistenceMiddleware(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        // 首次 reasoning：保存 user 消息并初始化状态
        if (!userMsgSaved.get()) {
            userMsgSaved.set(true);
            assistantContent.get().setLength(0);
            assistantSaved.set(false);

            saveUserMessages(agent, ctx, input);
        }

        return next.apply(input)
                .doOnNext(event -> {
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        String text = ((TextBlockDeltaEvent) event).getDelta();
                        if (text != null) {
                            assistantContent.get().append(text);
                        }
                    }
                })
                .doOnComplete(() -> {
                    if (!assistantSaved.get() && !assistantContent.get().isEmpty()) {
                        saveAssistantMessage(agent, ctx);
                    }
                })
                .doFinally(signal -> {
                    if (signal != null) {
                        ChatSessionContext.clear();
                        userMsgSaved.remove();
                        assistantContent.remove();
                        assistantSaved.remove();
                    }
                });
    }

    /**
     * 保存 user 消息
     */
    private void saveUserMessages(Agent agent, RuntimeContext ctx, ReasoningInput input) {
        Long sessionId = resolveSessionId(ctx);
        if (sessionId == null) return;

        input.messages().stream()
                .filter(msg -> {
                    try {
                        return msg.getRole() != null && MsgRole.USER.equals(msg.getRole());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .reduce((first, second) -> second) // 取最后一条 user 消息
                .ifPresent(msg -> {
                    try {
                        String text = msg.getTextContent();
                        if (text != null && !text.isEmpty()) {
                            chatSessionService.addMessage(sessionId, MsgRole.USER.name(), text);
                            log.debug("Saved user message for session {}", sessionId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to save user message", e);
                    }
                });
    }

    /**
     * 保存 assistant 消息
     */
    private void saveAssistantMessage(Agent agent, RuntimeContext ctx) {
        Long sessionId = resolveSessionId(ctx);
        if (sessionId == null) return;

        assistantSaved.set(true);
        try {
            chatSessionService.addMessage(sessionId, MsgRole.ASSISTANT.name(),
                    assistantContent.get().toString());
            log.debug("Saved assistant message for session {} ({} chars)",
                    sessionId, assistantContent.get().length());
        } catch (Exception e) {
            log.error("Failed to save assistant message", e);
        }
    }

    /**
     * 从 RuntimeContext 解析数据库 session ID
     */
    private Long resolveSessionId(RuntimeContext ctx) {
        if (ctx == null) return null;
        String sessionIdStr = ctx.getSessionId();
        if (sessionIdStr == null || sessionIdStr.isEmpty()) return null;
        try {
            return Long.parseLong(sessionIdStr);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse session id from '{}'", sessionIdStr);
            return null;
        }
    }
}
