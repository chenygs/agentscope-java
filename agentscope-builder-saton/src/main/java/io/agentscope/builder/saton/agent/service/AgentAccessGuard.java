package io.agentscope.builder.saton.agent.service;

import io.agentscope.builder.saton.common.error.ForbiddenException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import org.springframework.stereotype.Component;
import io.agentscope.builder.saton.agent.orm.entity.Tier;
import io.agentscope.builder.saton.agent.orm.repository.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.orm.entity.AgentDefinitionEntity;

/**
 * Unified access guard for agent-scoped operations.
 * Replaces ad-hoc ownership checks with tier-based authorization.
 */
@Component
public class AgentAccessGuard {

    private final AgentAclService aclService;
    private final AgentDefinitionRepository agentRepo;

    public AgentAccessGuard(AgentAclService aclService, AgentDefinitionRepository agentRepo) {
        this.aclService = aclService;
        this.agentRepo = agentRepo;
    }

    /**
     * Require that {@code userId} has at least {@code minTier} on the agent.
     * Returns the AgentDefinitionEntity on success, throws on failure.
     */
    public AgentDefinitionEntity require(Long agentDefId, String userId, Tier minTier) {
        Tier actual = aclService.tierFor(userId, agentDefId);
        if (actual == null) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
        if (!actual.atLeast(minTier)) {
            throw new ForbiddenException("insufficient tier: need " + minTier + ", have " + actual);
        }
        return agentRepo.findById(agentDefId)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
    }

    /** Quick owner-only check (for delete, share management, etc.). */
    public AgentDefinitionEntity requireOwner(Long agentDefId, String userId) {
        return require(agentDefId, userId, Tier.EDIT);
    }
}
