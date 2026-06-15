package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.AgentStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 浏览 / 重置 / 整体清空一个 agent 的所有 session 历史。
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

    private final AgentStateStore stateStore;

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
            out.add(new SessionVO(sessionKey, 0L));
        }
        out.sort(Comparator.comparing(SessionVO::sessionKey));
        return out;
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
}
