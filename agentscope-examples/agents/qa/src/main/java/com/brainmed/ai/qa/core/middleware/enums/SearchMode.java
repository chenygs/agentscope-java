package com.brainmed.ai.qa.core.middleware.enums;

/**
 * 搜索模式枚举，控制 Agent 在对话中可以使用哪些搜索工具。
 *
 * <ul>
 *   <li>{@link #AUTO} — 模型自主判断是否搜索、用哪个工具</li>
 *   <li>{@link #WEB} — 仅允许联网搜索（web_search）</li>
 *   <li>{@link #KNOWLEDGE} — 仅允许知识库搜索（knowledge_search）</li>
 *   <li>{@link #NONE} — 禁用所有搜索工具</li>
 * </ul>
 */
public enum SearchMode {
    /** 模型自主判断 */
    AUTO,
    /** 仅联网搜索 */
    WEB,
    /** 仅知识库搜索 */
    KNOWLEDGE,
    /** 禁用搜索 */
    NONE;

    /**
     * 安全解析：不区分大小写，无效值回退 {@link #AUTO}。
     */
    public static SearchMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
