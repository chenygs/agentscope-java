package com.brainmed.ai.qa.orm.vo;

import java.util.List;

/**
 * 历史消息的 AG-UI 表示，字段与前端 {@code @ag-ui/client} 的 Message 对齐
 * （role / content / toolCalls / toolCallId / id），供前端 {@code agent.setMessages} 直接注入。
 *
 * <p>一条 agentscope {@code Msg} 可能拆成多条本对象：
 * 思考 → {@code role="reasoning"}（id 追加 {@code -r} 后缀，避免与 assistant 同 id 被去重）；
 * 文本 + 工具调用 → {@code role="assistant"}；工具结果 → {@code role="tool"}。
 */
public record ChatMessageVo(
        String id,
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        /** 工具函数名，从 {@code ToolResultBlock.getName()} 提取 */
        String toolName,
        String citations) {

    /** AG-UI 协议中的 "reasoning" 角色，MsgRole 枚举中不存在，单独定义。 */
    public static final String ROLE_REASONING = "reasoning";

    public record ToolCall(String id, String type, FunctionCall function) {}

    public record FunctionCall(String name, String arguments) {}
}
