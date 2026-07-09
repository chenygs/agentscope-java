package com.brainmed.ai.qa.core.middleware.enums;

/**
 * 思考模式枚举，控制模型在对话中的推理深度。
 *
 * <ul>
 *   <li>{@link #NORMAL} — 普通模式，快速响应</li>
 *   <li>{@link #DEEP} — 深度思考，更深入的推理分析</li>
 *   <li>{@link #AUTO} — 自动选择（预留，暂未实现）</li>
 * </ul>
 */
public enum ThinkingMode {
    /** 普通模式 */
    NORMAL,
    /** 深度思考 */
    DEEP,
    /** 自动（预留） */
    AUTO;

    /**
     * 安全解析：不区分大小写，无效值回退 {@link #NORMAL}。
     */
    public static ThinkingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
