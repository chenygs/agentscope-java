package io.agentscope.builder.saton.session.orm.dto;

/**
 * 还原给前端的工具调用对，字段对齐 frontend 的 {@code ToolCallInfo}：
 *
 * <ul>
 *   <li>{@code callId}   — ToolUseBlock.id</li>
 *   <li>{@code toolName} — ToolUseBlock.name</li>
 *   <li>{@code status}   — {@code "done"} 表示有 result，{@code "running"} 表示有 use 但没找到对应 result</li>
 *   <li>{@code args}     — ToolUseBlock.input 序列化后的 JSON</li>
 *   <li>{@code result}   — ToolResultBlock 的输出文本（拼接所有 text 部分）</li>
 * </ul>
 */
public record ToolCallVO(
        String callId,
        String toolName,
        String status,
        String args,
        String result) {}
