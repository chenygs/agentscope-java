package io.github.chenygs.pptagent.chat.repository;

import io.github.chenygs.pptagent.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天会话 Repository
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<ChatSession> findBySessionKey(String sessionKey);

    List<ChatSession> findBySessionKeyIsNotNull();
}
