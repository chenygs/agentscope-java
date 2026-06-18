package io.agentscope.builder.saton.agent.orm.dto;

/**
 * Chat 请求体。
 *
 * @param message 用户消息（必填）
 * @param overrideModelProviderId 临时切换模型（可空，默认走 agent.defaultModelProviderId）
 * @param sessionKey 会话标识（可空，默认 "default"）；同 sessionKey 多次请求共享 history
 */
public record ChatSendReq(String message,
                          Long overrideModelProviderId,
                          String sessionKey) {

    /** 兼容老调用：sessionKey 默认 null。 */
    public ChatSendReq(String message, Long overrideModelProviderId) {
        this(message, overrideModelProviderId, null);
    }
}
