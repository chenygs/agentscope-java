package io.agentscope.builder.saton.resource.mcp.orm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import io.agentscope.builder.saton.resource.mcp.orm.entity.McpServerEntity;

public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    List<McpServerEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<McpServerEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndName(String ownerId, String name);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
