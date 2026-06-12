package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    @TempDir Path root;
    SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(root, new JsonFileAgentStateStore(root));
    }

    @Test
    void listReturnsEmptyWhenNoneExist() {
        List<SessionVO> sessions = service.list("alice", 42L);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listFindsAllSessionsForAgent() throws Exception {
        // Seed: <root>/alice/agent_42_a/agent_state.json + agent_42_b/agent_state.json
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        // Also: a different agent's session must NOT show
        seed("alice", "agent_99_x");
        // Also: a different user's session must NOT show
        seed("bob",   "agent_42_z");

        List<SessionVO> sessions = service.list("alice", 42L);
        assertEquals(2, sessions.size(), () ->
                "expected 2 sessions; got " + sessions.stream().map(SessionVO::sessionKey).toList());
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("a")));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("b")));
        for (SessionVO s : sessions) {
            assertTrue(s.lastActiveAt() > 0, "lastActiveAt should be > 0");
        }
    }

    @Test
    void resetDeletesSlotDirectoryAndReturnsTrue() throws Exception {
        seed("alice", "agent_42_a");
        assertTrue(Files.exists(root.resolve("alice").resolve("agent_42_a")));

        boolean removed = service.reset("alice", 42L, "a");
        assertTrue(removed);
        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_a")));
    }

    @Test
    void resetReturnsFalseWhenSlotNotExist() {
        assertFalse(service.reset("alice", 42L, "ghost"));
    }

    @Test
    void purgeAgentDeletesAllSlotsForThatAgent() throws Exception {
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        seed("alice", "agent_99_x");  // different agent — must survive

        service.purgeAgent("alice", 42L);

        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_a")));
        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_b")));
        assertTrue(Files.exists(root.resolve("alice").resolve("agent_99_x")));
    }

    private void seed(String userId, String sessionId) throws Exception {
        Path dir = root.resolve(userId).resolve(sessionId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("agent_state.json"), "{}");
    }
}
