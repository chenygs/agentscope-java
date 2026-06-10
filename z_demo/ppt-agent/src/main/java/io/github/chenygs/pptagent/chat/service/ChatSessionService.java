package io.github.chenygs.pptagent.chat.service;

import io.github.chenygs.pptagent.chat.entity.ChatSession;
import io.github.chenygs.pptagent.chat.entity.ChatSessionMessage;
import io.github.chenygs.pptagent.chat.repository.ChatSessionMessageRepository;
import io.github.chenygs.pptagent.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 前端对话 CRUD 服务 — 仅管理 {@code chat_session} / {@code chat_session_message} 两张表。
 *
 * <p>Agent 运行时状态的持久化由
 * {@link io.github.chenygs.pptagent.session.PptSessionStateStore} 负责（落 {@code ppt_session}
 * 表）。本类不再实现 {@code AgentStateStore} 接口。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepo;
    private final ChatSessionMessageRepository chatMessageRepo;

    public List<ChatSession> listSessions(Long userId) {
        return chatSessionRepo.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Optional<ChatSession> getSession(Long id) {
        return chatSessionRepo.findById(id);
    }

    @Transactional
    public ChatSession createSession(Long userId, String agentId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .agentId(agentId)
                .title("新对话")
                .isPinned(false)
                .build();
        return chatSessionRepo.save(session);
    }

    @Transactional
    public ChatSession updateTitle(Long id, String title) {
        ChatSession session = chatSessionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
        session.setTitle(title);
        return chatSessionRepo.save(session);
    }

    @Transactional
    public void deleteSession(Long id) {
        chatMessageRepo.deleteBySessionId(id);
        chatSessionRepo.deleteById(id);
    }

    public List<ChatSessionMessage> listMessages(Long sessionId) {
        return chatMessageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public ChatSessionMessage addMessage(Long sessionId, String role, String content) {
        ChatSessionMessage msg = ChatSessionMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();
        msg = chatMessageRepo.save(msg);

        chatSessionRepo.findById(sessionId).ifPresent(this::updateSessionTime);

        return msg;
    }

    @Transactional
    public ChatSessionMessage addMessageWithTitleUpdate(Long sessionId, String role, String content) {
        ChatSessionMessage msg = addMessage(sessionId, role, content);

        boolean isFirstUserMsg = "user".equals(role)
                && chatMessageRepo.countBySessionId(sessionId) <= 1;
        if (isFirstUserMsg) {
            String title = content.length() > 50 ? content.substring(0, 50) + "…" : content;
            updateTitle(sessionId, title);
        }

        return msg;
    }

    private void updateSessionTime(ChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepo.save(session);
    }
}
