package io.agentscope.builder.saton.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<AgentDefinitionEntity> findByIdAndOwnerId(Long id, String ownerId);

    Optional<AgentDefinitionEntity> findByOwnerIdAndAgentId(String ownerId, String agentId);

    boolean existsByOwnerIdAndAgentId(String ownerId, String agentId);

    boolean existsByIdAndOwnerId(Long id, String ownerId);

    long deleteByIdAndOwnerId(Long id, String ownerId);

    @Query("SELECT a FROM AgentDefinitionEntity a WHERE a.ownerId = :userId " +
           "OR EXISTS (SELECT 1 FROM AgentShareEntity s WHERE s.agentDefId = a.id AND s.granteeId = :userId) " +
           "ORDER BY a.createdAt DESC")
    List<AgentDefinitionEntity> findByOwnerIdOrGranteeId(@Param("userId") String userId);
}
