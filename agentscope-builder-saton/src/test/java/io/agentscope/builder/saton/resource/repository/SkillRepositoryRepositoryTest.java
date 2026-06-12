package io.agentscope.builder.saton.resource.repository;

import io.agentscope.builder.saton.common.crypto.AesGcmCipher;
import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import io.agentscope.builder.saton.common.crypto.SecretKeyHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({EncryptedJsonConverter.class, AesGcmCipher.class, SecretKeyHolder.class})
class SkillRepositoryRepositoryTest {

    @Autowired SkillRepositoryRepository repo;

    @Test
    void crudByOwner() {
        SkillRepositoryEntity a = new SkillRepositoryEntity();
        a.setOwnerId("alice");
        a.setName("alice-overlay");
        a.setType("git");
        a.setPropsJson("{\"url\":\"git@github.com:alice/skills.git\",\"token\":\"tok-alice\"}");
        long now = System.currentTimeMillis();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        repo.save(a);

        SkillRepositoryEntity b = new SkillRepositoryEntity();
        b.setOwnerId("bob");
        b.setName("bob-overlay");
        b.setType("git");
        b.setPropsJson("{\"url\":\"git@github.com:bob/skills.git\",\"token\":\"tok-bob\"}");
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        repo.save(b);

        List<SkillRepositoryEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-overlay", aliceList.get(0).getName());
        assertTrue(aliceList.get(0).getPropsJson().contains("\"tok-alice\""));

        assertTrue(repo.existsByOwnerIdAndName("alice", "alice-overlay"));
        assertFalse(repo.existsByOwnerIdAndName("alice", "bob-overlay"));

        assertTrue(repo.findByIdAndOwnerId(a.getId(), "bob").isEmpty());
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isPresent());
    }
}
