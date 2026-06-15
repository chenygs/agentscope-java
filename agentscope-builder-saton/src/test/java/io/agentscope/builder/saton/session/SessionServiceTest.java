package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.ChatMessageVO;
import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
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
    void listIncludesFirstUserMessageAsTitle() {
        seedWithUserMsg("alice", "agent_42_a", "你是谁，介绍一下自己");

        List<SessionVO> sessions = service.list("alice", 42L);

        assertEquals(1, sessions.size());
        assertEquals("你是谁，介绍一下自己", sessions.get(0).title());
    }

    @Test
    void listReturnsEmptyTitleWhenNoUserMessage() {
        // 只有 agent_state 没有 user 消息（只有 system / assistant），title 应为空串
        seed("alice", "agent_42_a");

        List<SessionVO> sessions = service.list("alice", 42L);

        assertEquals(1, sessions.size());
        assertEquals("", sessions.get(0).title());
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

    @Test
    void getMessagesReadsFromAgentStateContext() {
        // 模拟 ReActAgent 的实际持久化方式：把 messages 放进 AgentState.context，整体存为 agent_state
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .content(TextBlock.builder().text("你是谁").build())
                .build();
        Msg replyMsg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .name("ppt-agent")
                .content(TextBlock.builder().text("我是 ppt-agent").build())
                .build();
        AgentState state = AgentState.builder()
                .sessionId("agent_42_a")
                .userId("alice")
                .context(List.of(userMsg, replyMsg))
                .build();
        store.save("alice", "agent_42_a", "agent_state", state);

        List<ChatMessageVO> msgs = service.getMessages("alice", 42L, "a");

        assertEquals(2, msgs.size());
        assertEquals("user", msgs.get(0).role());
        assertEquals("你是谁", msgs.get(0).text());
        assertEquals("assistant", msgs.get(1).role());
        assertEquals("我是 ppt-agent", msgs.get(1).text());
    }

    @Test
    void getMessagesFallsBackToLegacyMemoryMessages() {
        // v1 兼容：消息直接存在 memory_messages list key 里
        Msg msg = Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .content(TextBlock.builder().text("legacy hello").build())
                .build();
        store.save("alice", "agent_42_a", "memory_messages", List.of(msg));

        List<ChatMessageVO> msgs = service.getMessages("alice", 42L, "a");

        assertEquals(1, msgs.size());
        assertEquals("legacy hello", msgs.get(0).text());
    }

    @Test
    void getMessagesEmptyWhenSessionAbsent() {
        List<ChatMessageVO> msgs = service.getMessages("alice", 42L, "ghost");
        assertTrue(msgs.isEmpty());
    }

    /** 写一个空的 AgentState 进 store —— session 进入 listSessionIds，但 title 为空。 */
    private void seed(String userId, String sessionId) {
        AgentState empty = AgentState.builder().sessionId(sessionId).userId(userId).build();
        store.save(userId, sessionId, "agent_state", empty);
    }

    /** 写一个带一条 user 消息的 AgentState —— 用于验证 title 抽取。 */
    private void seedWithUserMsg(String userId, String sessionId, String text) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .name("user")
                .content(TextBlock.builder().text(text).build())
                .build();
        AgentState state = AgentState.builder()
                .sessionId(sessionId)
                .userId(userId)
                .context(List.of(userMsg))
                .build();
        store.save(userId, sessionId, "agent_state", state);
    }
}
