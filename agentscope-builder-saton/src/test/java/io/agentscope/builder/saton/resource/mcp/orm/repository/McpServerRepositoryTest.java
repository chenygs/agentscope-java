package io.agentscope.builder.saton.resource.mcp.orm.repository;

import io.agentscope.builder.saton.common.crypto.AesGcmCipher;
import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import io.agentscope.builder.saton.common.crypto.SecretKeyHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.resource.mcp.orm.entity.McpServerEntity;
import io.agentscope.builder.saton.resource.mcp.orm.repository.McpServerRepository;

@DataJpaTest
@Import({EncryptedJsonConverter.class, AesGcmCipher.class, SecretKeyHolder.class})
class McpServerRepositoryTest {

    @Autowired McpServerRepository repo;

    @Test
    void crudByOwner() {
        McpServerEntity a = new McpServerEntity();
        a.setOwnerId("alice");
        a.setName("alice-playwright");
        a.setType("stdio");
        a.setPropsJson("{\"command\":\"npx playwright-mcp\",\"token\":\"tok-alice\"}");
        long now = System.currentTimeMillis();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        repo.save(a);

        McpServerEntity b = new McpServerEntity();
        b.setOwnerId("bob");
        b.setName("bob-playwright");
        b.setType("stdio");
        b.setPropsJson("{\"command\":\"npx playwright-mcp\",\"token\":\"tok-bob\"}");
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        repo.save(b);

        List<McpServerEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-playwright", aliceList.get(0).getName());
        assertTrue(aliceList.get(0).getPropsJson().contains("\"tok-alice\""));

        assertTrue(repo.existsByOwnerIdAndName("alice", "alice-playwright"));
        assertFalse(repo.existsByOwnerIdAndName("alice", "bob-playwright"));

        assertTrue(repo.findByIdAndOwnerId(a.getId(), "bob").isEmpty());
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isPresent());
    }
}
