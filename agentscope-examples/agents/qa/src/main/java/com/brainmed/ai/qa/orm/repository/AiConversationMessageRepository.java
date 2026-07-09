package com.brainmed.ai.qa.orm.repository;

import java.util.List;

import com.brainmed.ai.qa.orm.entity.AiConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 对话消息存档仓储。
 */
public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, Long> {

    /** 按用户与会话拉取历史消息，按事件发生时间 + seq + id 排序，保证回放顺序。 */
    List<AiConversationMessage> findByUserIdAndSessionIdOrderByCreatedAtAscSeqAscIdAsc(
            String userId, String sessionId);

    /** 分页查询某会话的历史消息。 */
    Page<AiConversationMessage> findByUserIdAndSessionId(String userId, String sessionId, Pageable pageable);

    AiConversationMessage findFirstByUserIdAndSessionIdAndRunIdAndMsgIdAndMessageType(
            String userId, String sessionId, String runId, String msgId, String messageType);

    List<AiConversationMessage> findByUserIdAndSessionIdAndRunIdAndMsgId(
            String userId, String sessionId, String runId, String msgId);

    /** 拉取某用户的所有会话 id（去重）。 */
    @Query("select distinct m.sessionId from AiConversationMessage m where m.userId = ?1")
    List<String> findDistinctSessionIdsByUserId(String userId);

    /** 拉取每个会话最新一条消息的 createdAt（用于排序/显示）。 */
    @Query("select m.sessionId, max(m.createdAt) from AiConversationMessage m " +
            "where m.userId = ?1 group by m.sessionId order by max(m.createdAt) desc")
    List<Object[]> findSessionIdsWithLatestTime(String userId);

    /** 拉取某会话的第一条 user 消息（用于生成会话标题）。 */
    AiConversationMessage findFirstByUserIdAndSessionIdAndRoleOrderByCreatedAtAscIdAsc(
            String userId, String sessionId, String role);

    /** 拉取某会话最后一条指定 role 的消息（用于续写时定位被中断的 partial assistant 记录）。 */
    AiConversationMessage findFirstByUserIdAndSessionIdAndRoleOrderByCreatedAtDescIdDesc(
            String userId, String sessionId, String role);

    /** 拉取某 run 的第一条 user 消息（用于树形 parent_id 解析）。 */
    AiConversationMessage findFirstByUserIdAndSessionIdAndRunIdAndRoleOrderByCreatedAtAscIdAsc(
            String userId, String sessionId, String runId, String role);

    /** 删除某会话的所有消息。 */
    void deleteByUserIdAndSessionId(String userId, String sessionId);
}
