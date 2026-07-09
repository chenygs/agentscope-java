package com.brainmed.ai.qa.orm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话主实体：每个对话一条记录，承载置顶、标题等会话级元信息。
 */
@Entity
@Table(name = "ai_conversation",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 id。 */
    private String userId;

    /** 会话 id（前端 threadId）。 */
    private String sessionId;

    /** 会话标题（自动取首条 user 消息前 24 字）。 */
    private String title;

    /** 是否置顶。 */
    private boolean pinned;

    /** 当前叶子消息 id（用于树形回溯）。 */
    private Long currentMessageId;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最后活跃时间。 */
    private LocalDateTime updatedAt;
}
