package io.agentscope.builder.saton.resource.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillMarketplaceRepository extends JpaRepository<SkillMarketplaceEntity, Long> {

    List<SkillMarketplaceEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<SkillMarketplaceEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndMarketplaceId(String ownerId, String marketplaceId);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
