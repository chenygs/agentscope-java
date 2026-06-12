package io.agentscope.builder.saton.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentAclServiceTest {

    @Autowired AgentAclService aclService;
    @Autowired AgentDefinitionRepository agentRepo;
    @Autowired AgentShareRepository shareRepo;

    @Test
    void ownerGetsEdit() {
        AgentDefinitionEntity agent = seedAgent("acl-owner");
        Tier tier = aclService.tierFor("acl-owner", agent.getId());
        assertEquals(Tier.EDIT, tier);
    }

    @Test
    void granteeGetsRun() {
        AgentDefinitionEntity agent = seedAgent("acl-owner2");
        AgentShareEntity share = new AgentShareEntity();
        share.setAgentDefId(agent.getId());
        share.setGranteeType("USER");
        share.setGranteeId("grantee-user");
        share.setTier("RUN");
        share.setCreatedBy("acl-owner2");
        share.setCreatedAt(System.currentTimeMillis());
        shareRepo.save(share);

        Tier tier = aclService.tierFor("grantee-user", agent.getId());
        assertEquals(Tier.RUN, tier);
    }

    @Test
    void noShareReturnsNull() {
        AgentDefinitionEntity agent = seedAgent("acl-owner3");
        Tier tier = aclService.tierFor("stranger", agent.getId());
        assertNull(tier);
    }

    private AgentDefinitionEntity seedAgent(String ownerId) {
        AgentDefinitionEntity a = new AgentDefinitionEntity();
        a.setOwnerId(ownerId);
        a.setAgentId("agent-" + ownerId);
        a.setName("Test Agent for " + ownerId);
        a.setAgentType("react");
        a.setDefaultModelProviderId(1L);
        a.setMaxIters(10);
        a.setSysPrompt("test");
        a.setCreatedAt(System.currentTimeMillis());
        a.setUpdatedAt(System.currentTimeMillis());
        return agentRepo.save(a);
    }
}
