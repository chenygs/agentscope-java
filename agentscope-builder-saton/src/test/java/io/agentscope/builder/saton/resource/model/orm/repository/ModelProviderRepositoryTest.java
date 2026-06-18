package io.agentscope.builder.saton.resource.model.orm.repository;

import io.agentscope.builder.saton.common.crypto.AesGcmCipher;
import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import io.agentscope.builder.saton.common.crypto.SecretKeyHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.resource.model.orm.repository.ModelProviderRepository;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;

@DataJpaTest
@Import({EncryptedJsonConverter.class, AesGcmCipher.class, SecretKeyHolder.class})
class ModelProviderRepositoryTest {

    @Autowired ModelProviderRepository repo;

    @Test
    void crudByOwner() {
        ModelProviderEntity a = new ModelProviderEntity();
        a.setOwnerId("alice");
        a.setName("alice-qwen");
        a.setType("dashscope");
        a.setPropsJson("{\"apiKey\":\"sk-alice\"}");
        long now = System.currentTimeMillis();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        repo.save(a);

        ModelProviderEntity b = new ModelProviderEntity();
        b.setOwnerId("bob");
        b.setName("bob-qwen");
        b.setType("dashscope");
        b.setPropsJson("{\"apiKey\":\"sk-bob\"}");
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        repo.save(b);

        List<ModelProviderEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-qwen", aliceList.get(0).getName());
        assertTrue(aliceList.get(0).getPropsJson().contains("\"sk-alice\""));

        assertTrue(repo.existsByOwnerIdAndName("alice", "alice-qwen"));
        assertFalse(repo.existsByOwnerIdAndName("alice", "bob-qwen"));

        assertTrue(repo.findByIdAndOwnerId(a.getId(), "bob").isEmpty());
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isPresent());
    }
}
