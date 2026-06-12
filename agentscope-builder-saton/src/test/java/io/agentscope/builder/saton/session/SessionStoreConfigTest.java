package io.agentscope.builder.saton.session;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionStoreConfigTest {

    @Autowired
    AgentStateStore stateStore;

    @Test
    void stateStoreIsJsonFileBacked() {
        assertNotNull(stateStore, "AgentStateStore bean should be present");
        assertTrue(stateStore instanceof JsonFileAgentStateStore,
                "expected JsonFileAgentStateStore; got " + stateStore.getClass());
    }
}
