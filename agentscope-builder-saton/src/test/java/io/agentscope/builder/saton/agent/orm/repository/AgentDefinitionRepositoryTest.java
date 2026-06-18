package io.agentscope.builder.saton.agent.orm.repository;

import io.agentscope.builder.saton.agent.orm.entity.AgentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.agent.orm.repository.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.orm.entity.AgentDefinitionEntity;

@DataJpaTest
class AgentDefinitionRepositoryTest {

    @Autowired AgentDefinitionRepository repo;

    @Test
    void crudByOwner() {
        AgentDefinitionEntity a = newDef("alice", "alice-bot");
        repo.save(a);
        AgentDefinitionEntity b = newDef("bob", "bob-bot");
        repo.save(b);

        List<AgentDefinitionEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-bot", aliceList.get(0).getAgentId());

        assertTrue(repo.existsByOwnerIdAndAgentId("alice", "alice-bot"));
        assertFalse(repo.existsByOwnerIdAndAgentId("alice", "bob-bot"));

        Optional<AgentDefinitionEntity> crossOwner = repo.findByIdAndOwnerId(a.getId(), "bob");
        assertTrue(crossOwner.isEmpty());

        Optional<AgentDefinitionEntity> mine = repo.findByIdAndOwnerId(a.getId(), "alice");
        assertTrue(mine.isPresent());
        assertEquals(7L, mine.get().getDefaultModelProviderId());
    }

    @Test
    void deleteByIdAndOwnerReturnsCount() {
        AgentDefinitionEntity a = newDef("alice", "to-delete");
        repo.save(a);
        long deleted = repo.deleteByIdAndOwnerId(a.getId(), "alice");
        assertEquals(1, deleted);
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isEmpty());
    }

    private AgentDefinitionEntity newDef(String owner, String agentId) {
        AgentDefinitionEntity e = new AgentDefinitionEntity();
        e.setOwnerId(owner);
        e.setAgentId(agentId);
        e.setName(agentId);
        e.setSysPrompt("you are a helpful assistant");
        e.setAgentType(AgentType.REACT);
        e.setDefaultModelProviderId(7L);
        e.setMaxIters(10);
        long now = System.currentTimeMillis();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}
