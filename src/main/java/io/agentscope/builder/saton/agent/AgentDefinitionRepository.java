package io.agentscope.builder.saton.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<AgentDefinitionEntity> findByIdAndOwnerId(Long id, String ownerId);

    Optional<AgentDefinitionEntity> findByOwnerIdAndAgentId(String ownerId, String agentId);

    boolean existsByOwnerIdAndAgentId(String ownerId, String agentId);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
