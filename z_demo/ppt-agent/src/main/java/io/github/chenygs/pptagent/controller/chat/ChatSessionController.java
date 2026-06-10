package io.github.chenygs.pptagent.controller.chat;

import cn.dev33.satoken.stp.StpUtil;
import io.github.chenygs.pptagent.orm.entity.ChatSession;
import io.github.chenygs.pptagent.orm.entity.ChatSessionMessage;
import io.github.chenygs.pptagent.orm.service.ChatSessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天会话 REST API
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /** 获取当前登录用户ID */
    private Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }

    /** 列表当前用户的会话 */
    @GetMapping("/sessions")
    public R<List<ChatSession>> listSessions() {
        return R.ok(chatSessionService.listSessions(getCurrentUserId()));
    }

    /** 获取会话详情 */
    @GetMapping("/sessions/{id}")
    public R<ChatSession> getSession(@PathVariable Long id) {
        return R.ok(chatSessionService.getSession(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在")));
    }

    /** 创建新会话 */
    @PostMapping("/sessions")
    public R<ChatSession> createSession(@RequestBody CreateSessionReq req) {
        return R.ok(chatSessionService.createSession(
                getCurrentUserId(),
                req.getAgentId() != null ? req.getAgentId() : "ppt-agent"));
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{id}")
    public R<Void> deleteSession(@PathVariable Long id) {
        chatSessionService.deleteSession(id);
        return R.ok(null);
    }

    /** 更新会话标题 */
    @PutMapping("/sessions/{id}/title")
    public R<ChatSession> updateTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(chatSessionService.updateTitle(id, body.get("title")));
    }

    /** 获取会话消息列表 */
    @GetMapping("/sessions/{id}/messages")
    public R<List<ChatSessionMessage>> listMessages(@PathVariable Long id) {
        return R.ok(chatSessionService.listMessages(id));
    }

    /** 添加消息 */
    @PostMapping("/sessions/{id}/messages")
    public R<ChatSessionMessage> addMessage(
            @PathVariable Long id,
            @RequestBody AddMessageReq req) {
        if (req.isAutoTitle()) {
            return R.ok(chatSessionService.addMessageWithTitleUpdate(id, req.getRole(), req.getContent()));
        }
        return R.ok(chatSessionService.addMessage(id, req.getRole(), req.getContent()));
    }

    // ─────────── DTO ───────────

    @Data
    public static class CreateSessionReq {
        private String agentId;
    }

    @Data
    public static class AddMessageReq {
        private String role;
        private String content;
        private boolean autoTitle = false;
    }

    // ─────────── 通用响应 ───────────

    @Data
    public static class R<T> {
        private int code;
        private T data;
        private String message;

        public static <T> R<T> ok(T data) {
            R<T> r = new R<>();
            r.code = 0;
            r.data = data;
            r.message = "ok";
            return r;
        }
    }
}
