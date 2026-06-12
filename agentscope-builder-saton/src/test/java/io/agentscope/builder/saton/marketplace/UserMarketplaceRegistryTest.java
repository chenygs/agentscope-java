package io.agentscope.builder.saton.marketplace;

import io.agentscope.builder.saton.resource.marketplace.SkillMarketplaceEntity;
import io.agentscope.builder.saton.resource.marketplace.SkillMarketplaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class UserMarketplaceRegistryTest {

    @Autowired UserMarketplaceRegistry registry;

    @MockitoBean SkillMarketplaceRepository repo;

    @Test
    void listWhenNoEntitiesReturnsEmpty() {
        when(repo.findByOwnerIdOrderByCreatedAtDesc("nobody")).thenReturn(List.of());
        List<BuilderMarketplace> result = registry.list("nobody");
        assertTrue(result.isEmpty(), "expected empty list for unknown user");
    }

    @Test
    void listWithGitEntitySkipsWhenClasspathMissing() {
        SkillMarketplaceEntity entity = new SkillMarketplaceEntity();
        entity.setOwnerId("admin");
        entity.setMarketplaceId("my-git");
        entity.setType("git");
        entity.setPropsJson("{\"remoteUrl\":\"https://example.com/repo.git\"}");
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());

        when(repo.findByOwnerIdOrderByCreatedAtDesc("admin")).thenReturn(List.of(entity));

        List<BuilderMarketplace> result = registry.list("admin");
        // GitSkillRepository not on classpath → createInstance ISE → log.warn skip → empty
        assertTrue(result.isEmpty(), "git marketplace without classpath should be silently skipped");
    }

    @Test
    void findExistingReturnsEmptyWhenClasspathMissing() {
        SkillMarketplaceEntity entity = new SkillMarketplaceEntity();
        entity.setOwnerId("admin");
        entity.setMarketplaceId("my-nacos");
        entity.setType("nacos");
        entity.setPropsJson("{\"serverAddr\":\"http://nacos:8848\"}");
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());

        when(repo.findByOwnerIdOrderByCreatedAtDesc("admin")).thenReturn(List.of(entity));

        // No nacos SDK on classpath → skipped
        assertTrue(registry.find("admin", "my-nacos").isEmpty(),
                "nacos marketplace without classpath should be not found");
    }

    @Test
    void findNonExistingReturnsEmpty() {
        when(repo.findByOwnerIdOrderByCreatedAtDesc("admin")).thenReturn(List.of());
        assertTrue(registry.find("admin", "nonexistent").isEmpty());
    }
}
