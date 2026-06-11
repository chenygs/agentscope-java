package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;

public record AgentVO(
        Long id,
        String agentId,
        String name,
        String description,
        String sysPrompt,
        String agentType,
        Long defaultModelProviderId,
        Integer maxIters,
        long createdAt,
        long updatedAt
) {
    public static AgentVO from(AgentDefinitionEntity e) {
        return new AgentVO(
                e.getId(), e.getAgentId(), e.getName(), e.getDescription(),
                e.getSysPrompt(), e.getAgentType(),
                e.getDefaultModelProviderId(), e.getMaxIters(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
