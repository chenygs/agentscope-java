package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.ChatMessageVO;
import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.builder.saton.session.dto.ToolCallVO;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 浏览 / 重置 / 整体清空一个 agent 的所有 session 历史，并能按 sessionKey 拉取历史消息。
 *
 * <p>通过 {@link AgentStateStore} 抽象接口操作，与底层存储（Redis / JsonFile / InMemory）解耦。
 * 本项目的 {@code sessionId = "agent_" + agentDefId + "_" + sessionKey}（见
 * {@link io.agentscope.builder.saton.agent.chat.ChatService}）。
 *
 * <p>{@code lastActiveAt} 暂时返回 0 —— Redis 后端没有原生 mtime；前端如需按时间排序，
 * 后续可在 store 上加一层装饰器维护 ZSET 时间戳。
 */
@Slf4j
@Service
public class SessionService {

    /**
     * v2.0+ ReActAgent 把整段会话存在这一个 key 下（{@link AgentState}，含 {@code context} 列表）。
     * v1 兼容路径见 {@link #MEMORY_MESSAGES_KEY}。
     */
    private static final String AGENT_STATE_KEY = "agent_state";

    /** v1 legacy fallback —— 老版本把消息单独写在这里。 */
    private static final String MEMORY_MESSAGES_KEY = "memory_messages";

    private static final DateTimeFormatter MSG_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AgentStateStore stateStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionService(AgentStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public List<SessionVO> list(String ownerId, Long agentDefId) {
        String prefix = "agent_" + agentDefId + "_";
        Set<String> sessionIds = stateStore.listSessionIds(ownerId);
        List<SessionVO> out = new ArrayList<>();
        for (String sid : sessionIds) {
            if (!sid.startsWith(prefix)) continue;
            String sessionKey = sid.substring(prefix.length());
            out.add(new SessionVO(sessionKey, 0L, firstUserText(ownerId, sid)));
        }
        out.sort(Comparator.comparing(SessionVO::sessionKey));
        return out;
    }

    /**
     * 抽取一个 session 的第一条 user 文本作为标题候选。读 {@code agent_state.context}，
     * 失败 / 不存在 / 没有 user 消息时回落到空串（前端会用 sessionKey 兜底）。
     *
     * <p>不做截断 —— 前端按宽度截。
     */
    private String firstUserText(String userId, String sessionId) {
        try {
            return stateStore.get(userId, sessionId, AGENT_STATE_KEY, AgentState.class)
                    .map(AgentState::getContext)
                    .stream()
                    .flatMap(List::stream)
                    .filter(m -> m.getRole() == MsgRole.USER)
                    .findFirst()
                    .map(this::concatTextBlocks)
                    .orElse("");
        } catch (Exception e) {
            log.warn("failed to read title for session {}/{}: {}", userId, sessionId, e.getMessage());
            return "";
        }
    }

    private String concatTextBlocks(Msg m) {
        StringBuilder sb = new StringBuilder();
        for (TextBlock tb : m.getContentBlocks(TextBlock.class)) {
            if (tb.getText() != null) sb.append(tb.getText());
        }
        return sb.toString();
    }

    public boolean reset(String ownerId, Long agentDefId, String sessionKey) {
        String sessionId = "agent_" + agentDefId + "_" + sessionKey;
        if (!stateStore.exists(ownerId, sessionId)) return false;
        stateStore.delete(ownerId, sessionId);
        return true;
    }

    public void purgeAgent(String ownerId, Long agentDefId) {
        String prefix = "agent_" + agentDefId + "_";
        Set<String> sessionIds = stateStore.listSessionIds(ownerId);
        for (String sid : sessionIds) {
            if (sid.startsWith(prefix)) {
                stateStore.delete(ownerId, sid);
            }
        }
    }

    /**
     * 拉取一个 session 的历史消息，扁平化成前端 {@link ChatMessageVO}。
     *
     * <p>主路径读 {@code agent_state}：v2.0+ ReActAgent 把整段对话存进 AgentState.context（一个
     * {@code List<Msg>}）。如果该 key 拿不到，回退到 v1 legacy 的 {@code memory_messages} list key。
     *
     * <p>Msg 列表里的 ToolUseBlock 与对应的 ToolResultBlock 会被合并到产生它们的那条 assistant
     * 消息上（按 callId 配对）。无 TextBlock 但有 ToolUseBlock 的消息也会被保留（text 为空）。
     */
    public List<ChatMessageVO> getMessages(String ownerId, Long agentDefId, String sessionKey) {
        String sessionId = "agent_" + agentDefId + "_" + sessionKey;

        // v2.0+ 主路径：AgentState.context
        List<Msg> raw = stateStore.get(ownerId, sessionId, AGENT_STATE_KEY, AgentState.class)
                .map(AgentState::getContext)
                .orElse(null);

        // v1 legacy 回退
        if (raw == null || raw.isEmpty()) {
            raw = stateStore.getList(ownerId, sessionId, MEMORY_MESSAGES_KEY, Msg.class);
        }
        if (raw == null || raw.isEmpty()) return List.of();

        // 第一遍：把 callId → result 的映射建好，便于把 ToolResultBlock 贴回到产生它的消息上
        Map<String, ToolResultBlock> resultsByCallId = new HashMap<>();
        for (Msg m : raw) {
            for (ToolResultBlock tr : m.getContentBlocks(ToolResultBlock.class)) {
                if (tr.getId() != null) resultsByCallId.put(tr.getId(), tr);
            }
        }

        List<ChatMessageVO> out = new ArrayList<>();
        for (Msg m : raw) {
            // ToolResultBlock 是模型上一轮 tool call 的"回执"，它独立成一条 Msg（role=USER 或 SYSTEM）
            // 我们已经把它合并到 assistant 消息上，这条本身就不再展示。
            List<ToolResultBlock> results = m.getContentBlocks(ToolResultBlock.class);
            List<TextBlock> texts = m.getContentBlocks(TextBlock.class);
            List<ToolUseBlock> toolUses = m.getContentBlocks(ToolUseBlock.class);
            if (texts.isEmpty() && toolUses.isEmpty() && !results.isEmpty()) {
                continue;
            }

            String role = mapRole(m.getRole());
            StringBuilder text = new StringBuilder();
            for (TextBlock tb : texts) {
                if (tb.getText() != null) text.append(tb.getText());
            }

            List<ToolCallVO> calls = new ArrayList<>();
            for (ToolUseBlock tu : toolUses) {
                ToolResultBlock paired = resultsByCallId.get(tu.getId());
                calls.add(new ToolCallVO(
                        tu.getId(),
                        tu.getName(),
                        paired != null ? "done" : "running",
                        serializeArgs(tu.getInput()),
                        paired != null ? extractResultText(paired) : ""));
            }

            out.add(new ChatMessageVO(
                    m.getId(),
                    role,
                    text.toString(),
                    parseTimestamp(m.getTimestamp()),
                    calls));
        }
        return out;
    }

    private String mapRole(MsgRole role) {
        if (role == null) return "assistant";
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
    }

    private String serializeArgs(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            log.warn("failed to serialize tool args: {}", e.getMessage());
            return "";
        }
    }

    private String extractResultText(ToolResultBlock result) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock cb : result.getOutput()) {
            if (cb instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /** Msg.timestamp 形如 "2026-06-15 13:54:10.588"，解析失败回落 0。 */
    private long parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return 0L;
        try {
            return LocalDateTime.parse(ts, MSG_TS_FORMAT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
