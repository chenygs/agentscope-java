package io.agentscope.builder.saton.agent.orm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import io.agentscope.builder.saton.agent.orm.entity.AgentShareEntity;

public interface AgentShareRepository extends JpaRepository<AgentShareEntity, Long> {

    List<AgentShareEntity> findByAgentDefId(Long agentDefId);

    List<AgentShareEntity> findByGranteeId(String granteeId);

    Optional<AgentShareEntity> findByAgentDefIdAndGranteeId(Long agentDefId, String granteeId);

    long deleteByAgentDefId(Long agentDefId);

    long deleteByIdAndAgentDefId(Long id, Long agentDefId);
}
