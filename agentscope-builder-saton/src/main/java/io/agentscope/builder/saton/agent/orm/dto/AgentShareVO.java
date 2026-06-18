package io.agentscope.builder.saton.agent.orm.dto;

import io.agentscope.builder.saton.agent.orm.entity.AgentShareEntity;

public record AgentShareVO(Long id, Long agentDefId, String granteeType, String granteeId, String tier, long createdAt) {
    public static AgentShareVO from(AgentShareEntity e) {
        return new AgentShareVO(e.getId(), e.getAgentDefId(), e.getGranteeType(), e.getGranteeId(), e.getTier(), e.getCreatedAt());
    }
}
