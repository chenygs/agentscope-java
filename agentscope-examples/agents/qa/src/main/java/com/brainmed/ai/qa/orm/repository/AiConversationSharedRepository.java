package com.brainmed.ai.qa.orm.repository;

import com.brainmed.ai.qa.orm.entity.AiConversationShared;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 会话分享记录仓储。
 */
public interface AiConversationSharedRepository extends JpaRepository<AiConversationShared, Long> {

    /** 根据 token 查询分享记录。 */
    AiConversationShared findByToken(String token);

    /** 根据用户 id 和会话 id 查询分享记录。 */
    AiConversationShared findByUserIdAndSessionId(String userId, String sessionId);
}
