package io.agentscope.builder.saton.session.orm.dto;

import java.util.List;

/**
 * 还原给前端的历史消息。字段对应 frontend 的 {@code ChatMessage}：
 *
 * <ul>
 *   <li>{@code id}        — 消息唯一 id（取 Msg.getId()）</li>
 *   <li>{@code role}      — {@code "user"} | {@code "assistant"} | {@code "system"}</li>
 *   <li>{@code text}      — 把 Msg 里所有 TextBlock 拼起来的纯文本</li>
 *   <li>{@code timestamp} — 毫秒时间戳；解析失败回落 0</li>
 *   <li>{@code toolCalls} — 把同条 assistant 消息里的 ToolUseBlock + 配对的 ToolResultBlock 整合</li>
 * </ul>
 */
public record ChatMessageVO(
        String id,
        String role,
        String text,
        long timestamp,
        List<ToolCallVO> toolCalls) {}
