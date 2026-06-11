package io.agentscope.builder.saton.resource.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelProviderRepository extends JpaRepository<ModelProviderEntity, Long> {

    List<ModelProviderEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<ModelProviderEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndName(String ownerId, String name);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
