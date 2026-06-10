package io.github.chenygs.pptagent.chat.repository;

import io.github.chenygs.pptagent.chat.entity.ChatSessionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 聊天消息 Repository
 */
@Repository
public interface ChatSessionMessageRepository extends JpaRepository<ChatSessionMessage, Integer> {

    List<ChatSessionMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatSessionMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, String role);

    Optional<ChatSessionMessage> findFirstBySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, String role);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndRole(Long sessionId, String role);

    @Modifying
    @Transactional
    long deleteBySessionIdAndRole(Long sessionId, String role);

    @Modifying
    @Transactional
    long deleteBySessionId(Long sessionId);
}
