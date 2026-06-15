package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link SessionService} 经由 {@link AgentStateStore} 接口正确实现 list/reset/purge。
 *
 * <p>用 {@link InMemoryAgentStateStore} 替代生产的 RedisAgentStateStore，免起真实 Redis。
 * 由于 SessionService 只用 store 接口的方法，二者行为等价。
 */
class SessionServiceTest {

    AgentStateStore store;
    SessionService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAgentStateStore();
        service = new SessionService(store);
    }

    @Test
    void listReturnsEmptyWhenNoneExist() {
        List<SessionVO> sessions = service.list("alice", 42L);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listFindsAllSessionsForAgent() {
        // Seed: 两个 alice 的 agent_42 session，一个 alice 的不同 agent，一个 bob 的同 agent
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        seed("alice", "agent_99_x");
        seed("bob",   "agent_42_z");

        List<SessionVO> sessions = service.list("alice", 42L);
        assertEquals(2, sessions.size(), () ->
                "expected 2 sessions; got " + sessions.stream().map(SessionVO::sessionKey).toList());
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("a")));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("b")));
    }

    @Test
    void resetDeletesSessionAndReturnsTrue() {
        seed("alice", "agent_42_a");
        assertTrue(store.exists("alice", "agent_42_a"));

        boolean removed = service.reset("alice", 42L, "a");
        assertTrue(removed);
        assertFalse(store.exists("alice", "agent_42_a"));
    }

    @Test
    void resetReturnsFalseWhenSlotNotExist() {
        assertFalse(service.reset("alice", 42L, "ghost"));
    }

    @Test
    void purgeAgentDeletesAllSlotsForThatAgent() {
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        seed("alice", "agent_99_x");  // different agent — must survive

        service.purgeAgent("alice", 42L);

        assertFalse(store.exists("alice", "agent_42_a"));
        assertFalse(store.exists("alice", "agent_42_b"));
        assertTrue(store.exists("alice", "agent_99_x"));
    }

    /** 写一个 agent_state 进 store，以让 session 被 listSessionIds 看到。 */
    private void seed(String userId, String sessionId) {
        store.save(userId, sessionId, "agent_state", new MarkerState());
    }

    /** 占位 State —— 让 store 真正落下一个 entry，使 session 进入 listSessionIds。 */
    private static class MarkerState implements State {}
}
