package io.agentscope.builder.saton.resource.marketplace;

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
class SkillMarketplaceRepositoryTest {

    @Autowired SkillMarketplaceRepository repo;

    @Test
    void crudByOwner() {
        SkillMarketplaceEntity a = new SkillMarketplaceEntity();
        a.setOwnerId("alice");
        a.setMarketplaceId("alice-skills");
        a.setType("git");
        a.setPropsJson("{\"url\":\"git@github.com:alice/x.git\",\"token\":\"tok-alice\"}");
        long now = System.currentTimeMillis();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        repo.save(a);

        SkillMarketplaceEntity b = new SkillMarketplaceEntity();
        b.setOwnerId("bob");
        b.setMarketplaceId("bob-skills");
        b.setType("git");
        b.setPropsJson("{\"url\":\"git@github.com:bob/x.git\",\"token\":\"tok-bob\"}");
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        repo.save(b);

        List<SkillMarketplaceEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-skills", aliceList.get(0).getMarketplaceId());
        assertTrue(aliceList.get(0).getPropsJson().contains("\"tok-alice\""));

        assertTrue(repo.existsByOwnerIdAndMarketplaceId("alice", "alice-skills"));
        assertFalse(repo.existsByOwnerIdAndMarketplaceId("alice", "bob-skills"));

        assertTrue(repo.findByIdAndOwnerId(a.getId(), "bob").isEmpty());
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isPresent());
    }
}
