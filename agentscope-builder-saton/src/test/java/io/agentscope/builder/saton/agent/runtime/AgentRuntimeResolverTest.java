package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentRuntimeResolverTest {

    @Autowired AgentDefinitionRepository agentRepo;
    @Autowired ModelProviderRepository modelRepo;
    @Autowired AgentRuntimeResolver resolver;

    Long agentId;
    Long modelId;

    @BeforeEach
    void setUp() {
        ModelProviderEntity mp = new ModelProviderEntity();
        mp.setOwnerId("admin");
        mp.setName("rrt-model-" + System.nanoTime());
        mp.setType("test-stub");
        mp.setPropsJson("{}");
        long now = System.currentTimeMillis();
        mp.setCreatedAt(now);
        mp.setUpdatedAt(now);
        modelRepo.save(mp);
        this.modelId = mp.getId();

        AgentDefinitionEntity a = new AgentDefinitionEntity();
        a.setOwnerId("admin");
        a.setAgentId("rrt-agent-" + System.nanoTime());
        a.setAgentType("react");
        a.setSysPrompt("hi");
        a.setDefaultModelProviderId(modelId);
        a.setMaxIters(5);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        agentRepo.save(a);
        this.agentId = a.getId();
    }

    @Test
    void resolveCachesByKey() {
        HarnessAgent a1 = resolver.resolve(agentId, modelId, "admin");
        HarnessAgent a2 = resolver.resolve(agentId, modelId, "admin");
        assertSame(a1, a2);
    }

    @Test
    void differentModelGivesDifferentAgent() {
        ModelProviderEntity mp2 = new ModelProviderEntity();
        mp2.setOwnerId("admin");
        mp2.setName("rrt-model2-" + System.nanoTime());
        mp2.setType("test-stub");
        mp2.setPropsJson("{}");
        long now = System.currentTimeMillis();
        mp2.setCreatedAt(now);
        mp2.setUpdatedAt(now);
        modelRepo.save(mp2);
        HarnessAgent a1 = resolver.resolve(agentId, modelId, "admin");
        HarnessAgent a2 = resolver.resolve(agentId, mp2.getId(), "admin");
        assertNotSame(a1, a2);
    }

    @Test
    void invalidateByAgentClearsCache() {
        resolver.resolve(agentId, modelId, "admin");
        int before = resolver.cacheSize();
        resolver.invalidateByAgent(agentId);
        int after = resolver.cacheSize();
        assertEquals(before - 1, after);
    }

    @Test
    void invalidateByModelClearsCache() {
        resolver.resolve(agentId, modelId, "admin");
        int before = resolver.cacheSize();
        resolver.invalidateByModel(modelId);
        int after = resolver.cacheSize();
        assertEquals(before - 1, after);
    }
}
