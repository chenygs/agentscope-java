package io.agentscope.builder.saton.agent.service;

import org.springframework.stereotype.Service;
import io.agentscope.builder.saton.agent.orm.repository.AgentShareRepository;
import io.agentscope.builder.saton.agent.orm.enums.Tier;
import io.agentscope.builder.saton.agent.orm.repository.AgentDefinitionRepository;

@Service
public class AgentAclService {

    private final AgentDefinitionRepository agentRepo;
    private final AgentShareRepository shareRepo;

    public AgentAclService(AgentDefinitionRepository agentRepo, AgentShareRepository shareRepo) {
        this.agentRepo = agentRepo;
        this.shareRepo = shareRepo;
    }

    /**
     * Return the effective tier for {@code userId} on the agent identified by {@code agentDefId}.
     * Owner always gets EDIT. Returns null if the user has no access.
     */
    public Tier tierFor(String userId, Long agentDefId) {
        // Owner always gets EDIT
        if (agentRepo.existsByIdAndOwnerId(agentDefId, userId)) {
            return Tier.EDIT;
        }
        // Check shares
        return shareRepo.findByAgentDefIdAndGranteeId(agentDefId, userId)
                .map(s -> Tier.valueOf(s.getTier()))
                .orElse(null);
    }
}
