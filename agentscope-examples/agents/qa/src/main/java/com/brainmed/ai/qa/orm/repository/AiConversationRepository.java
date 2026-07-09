package com.brainmed.ai.qa.orm.repository;

import com.brainmed.ai.qa.orm.entity.AiConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 会话主表仓储。
 */
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    /** 查某用户所有会话。 */
    List<AiConversation> findByUserId(String userId);

    /** 查某用户某会话。 */
    AiConversation findByUserIdAndSessionId(String userId, String sessionId);

    /** 查某用户所有置顶会话。 */
    List<AiConversation> findByUserIdAndPinnedTrue(String userId);
}
