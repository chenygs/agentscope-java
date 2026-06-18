package io.agentscope.builder.saton.agent.service.runtime;

import io.agentscope.builder.saton.agent.orm.entity.AgentType;
import io.agentscope.builder.saton.agent.orm.entity.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.orm.repository.AgentDefinitionRepository;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.orm.repository.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.agent.service.runtime.AgentBuildOrchestrator;

@SpringBootTest
@Transactional
class SubagentTreeTest {

    @Autowired AgentDefinitionRepository agentRepo;
    @Autowired ModelProviderRepository modelRepo;
    @Autowired AgentBuildOrchestrator orchestrator;

    @Test
    void buildParentWithChildSubagentRef() {
        ModelProviderEntity model = new ModelProviderEntity();
        model.setOwnerId("admin");
        model.setName("stub-" + System.nanoTime());
        model.setType("test-stub");
        model.setPropsJson("{}");
        model.setCreatedAt(System.currentTimeMillis());
        model.setUpdatedAt(System.currentTimeMillis());
        model = modelRepo.save(model);

        AgentDefinitionEntity child = new AgentDefinitionEntity();
        child.setOwnerId("admin");
        child.setAgentId("child-" + System.nanoTime());
        child.setName("child");
        child.setSysPrompt("I am child");
        child.setAgentType(AgentType.REACT);
        child.setDefaultModelProviderId(model.getId());
        child.setMaxIters(3);
        child.setCreatedAt(System.currentTimeMillis());
        child.setUpdatedAt(System.currentTimeMillis());
        child = agentRepo.save(child);

        AgentDefinitionEntity parent = new AgentDefinitionEntity();
        parent.setOwnerId("admin");
        parent.setAgentId("parent-" + System.nanoTime());
        parent.setName("parent");
        parent.setSysPrompt("I am parent");
        parent.setAgentType(AgentType.REACT);
        parent.setDefaultModelProviderId(model.getId());
        parent.setMaxIters(3);
        parent.setSubagentRefsJson("[\"" + child.getAgentId() + "\"]");
        parent.setCreatedAt(System.currentTimeMillis());
        parent.setUpdatedAt(System.currentTimeMillis());
        parent = agentRepo.save(parent);

        HarnessAgent parentAgent = orchestrator.build(parent, model, "admin");
        assertNotNull(parentAgent, "parent should build successfully with subagent factory wired");
    }
}
