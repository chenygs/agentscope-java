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
 * 会话分享记录：一个会话可被分享一次，生成唯一 token 供公开只读访问。
 */
@Entity
@Table(name = "ai_conversation_shared",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AiConversationShared {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分享唯一标识（UUID），用于构造公开链接。 */
    private String token;

    /** 分享者用户 id。 */
    private String userId;

    /** 被分享的会话 id。 */
    private String sessionId;

    /** 分享时间。 */
    private LocalDateTime createdAt;
}
