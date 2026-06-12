package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentShareEntity;

public record AgentShareVO(Long id, Long agentDefId, String granteeType, String granteeId, String tier, long createdAt) {
    public static AgentShareVO from(AgentShareEntity e) {
        return new AgentShareVO(e.getId(), e.getAgentDefId(), e.getGranteeType(), e.getGranteeId(), e.getTier(), e.getCreatedAt());
    }
}
