package io.agentscope.builder.saton.agent.service.runtime;

import io.agentscope.builder.saton.agent.orm.entity.AgentType;
import io.agentscope.builder.saton.agent.orm.entity.AgentDefinitionEntity;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.agent.service.runtime.AgentBuildOrchestrator;

@SpringBootTest
class HarnessAgentSwitchTest {

    @Autowired AgentBuildOrchestrator orchestrator;

    @Test
    void buildReturnsHarnessAgent() {
        AgentDefinitionEntity def = new AgentDefinitionEntity();
        def.setId(1L);
        def.setAgentId("switch-test-" + System.nanoTime());
        def.setName("switch");
        def.setSysPrompt("hi");
        def.setAgentType(AgentType.REACT);
        def.setMaxIters(3);

        ModelProviderEntity model = new ModelProviderEntity();
        model.setId(1L);
        model.setOwnerId("admin");
        model.setName("stub");
        model.setType("test-stub");
        model.setPropsJson("{}");

        HarnessAgent agent = orchestrator.build(def, model, "admin");
        assertNotNull(agent, "should return a HarnessAgent");
    }
}
