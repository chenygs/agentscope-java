package com.brainmed.ai.qa.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 对话消息存档实体，映射 {@code ai_conversation_message} 表。
 *
 * <p>记录完整轨迹（user / assistant / tool 的每条消息），与 Redis 短期记忆分离：
 * Redis 是会被压缩覆盖的工作上下文，本表是永不丢失原文的完整存档。
 *
 * <p>字段为 camelCase，依赖 Spring Boot 默认的下划线命名策略映射到 snake_case 列名。
 */
@Entity
@Table(name = "ai_conversation_message")
@Getter
@Setter
@NoArgsConstructor
public class AiConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 鉴权用户 id。 */
    private String userId;

    /** 会话 id（前端 threadId）。 */
    private String sessionId;

    /** 本轮运行 id。 */
    private String runId;

    /** agentscope Msg.id。 */
    private String msgId;

    /** user / assistant / tool。 */
    private String role;

    /** text / thinking / tool_use / tool_result。 */
    private String messageType;

    /** 纯文本内容（便于查询/列表预览）。 */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** 完整 Msg 序列化 JSON（保真存档，可还原 content blocks）。 */
    @Column(columnDefinition = "LONGTEXT")
    private String contentJson;

    /** 引用来源 JSON（仅最终 assistant 文本回答行有值）。 */
    @Column(columnDefinition = "JSON")
    private String citations;

    /** 工具名（tool_use/tool_result 时）。 */
    private String toolName;

    /** 工具调用关联 id。 */
    private String toolCallId;

    /** 父消息 id（NULL=根节点）。 */
    private Long parentId;

    /** 物化路径，如 /10/11/。 */
    private String path;

    /** 深度，根=0。 */
    private Integer depth;

    /** 轮内顺序。 */
    private Integer seq;

    /** 落库时间。 */
    private LocalDateTime createdAt;
}
