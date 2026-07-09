package com.brainmed.ai.qa.orm.vo;

/**
 * 会话摘要：会话 id + 标题（首条 user 消息前 24 字）+ 最后活跃时间戳。
 */
public record SessionSummaryVo(
        String sessionId,
        String title,
        long updatedAt,
        boolean pinned) {
}
