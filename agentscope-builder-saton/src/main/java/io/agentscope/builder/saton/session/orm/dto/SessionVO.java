package io.agentscope.builder.saton.session.orm.dto;

/**
 * 一个 session 的简要视图。
 *
 * @param sessionKey   会话标识；同一 agent 下唯一
 * @param lastActiveAt 最近活跃毫秒时间戳；当前 Redis 后端固定 0
 * @param title        从 {@code agent_state.context} 抽取的第一条 user 消息文本，未截断；
 *                     无消息时为空字符串。前端按 UI 宽度自行截断。
 */
public record SessionVO(String sessionKey, long lastActiveAt, String title) { }
