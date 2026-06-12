package io.agentscope.builder.saton.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AgentShareRepositoryTest {

    @Autowired AgentShareRepository shareRepo;

    @Test
    void crudByAgentDefId() {
        AgentShareEntity share = new AgentShareEntity();
        share.setAgentDefId(99L);
        share.setGranteeType("USER");
        share.setGranteeId("user-x");
        share.setTier("RUN");
        share.setCreatedBy("owner");
        share.setCreatedAt(System.currentTimeMillis());
        shareRepo.save(share);

        List<AgentShareEntity> found = shareRepo.findByGranteeId("user-x");
        assertEquals(1, found.size());
        assertEquals("RUN", found.get(0).getTier());

        long deleted = shareRepo.deleteByAgentDefId(99L);
        assertEquals(1, deleted);
        assertTrue(shareRepo.findByGranteeId("user-x").isEmpty());
    }
}
