package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.core.type.TypeReference;

import java.util.List;

public record AgentVO(
        Long id,
        String agentId,
        String name,
        String description,
        String sysPrompt,
        String agentType,
        Long defaultModelProviderId,
        Integer maxIters,
        List<ToolSpec> toolSpecs,
        long createdAt,
        long updatedAt
) {
    public static AgentVO from(AgentDefinitionEntity e) {
        List<ToolSpec> specs = parseToolSpecs(e.getToolSpecsJson());
        return new AgentVO(
                e.getId(), e.getAgentId(), e.getName(), e.getDescription(),
                e.getSysPrompt(), e.getAgentType(),
                e.getDefaultModelProviderId(), e.getMaxIters(),
                specs,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static List<ToolSpec> parseToolSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<ToolSpec>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("invalid tool_specs_json on agent_definition", ex);
        }
    }
}
