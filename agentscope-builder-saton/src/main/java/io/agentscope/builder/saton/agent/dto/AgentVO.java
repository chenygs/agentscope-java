package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentType;
import io.agentscope.builder.saton.agent.MiddlewareSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.core.type.TypeReference;

import java.util.List;

public record AgentVO(Long id,
                      String ownerId,
                      String agentId,
                      String name,
                      String description,
                      String sysPrompt,
                      AgentType agentType,
                      Long defaultModelProviderId,
                      Integer maxIters,
                      List<ToolSpec> toolSpecs,
                      List<SkillRepoSpec> skillRepositories,
                      List<MiddlewareSpec> middlewareSpecs,
                      List<String> subagentRefs,
                      long createdAt,
                      long updatedAt) {

    public static AgentVO from(AgentDefinitionEntity e) {
        return new AgentVO(
                e.getId(), e.getOwnerId(), e.getAgentId(), e.getName(),
                e.getDescription(), e.getSysPrompt(), e.getAgentType(),
                e.getDefaultModelProviderId(), e.getMaxIters(),
                parseList(e.getToolSpecsJson(), new TypeReference<List<ToolSpec>>() {}),
                parseList(e.getSkillRepositoriesJson(), new TypeReference<List<SkillRepoSpec>>() {}),
                parseList(e.getHookSpecsJson(), new TypeReference<List<MiddlewareSpec>>() {}),
                parseList(e.getSubagentRefsJson(), new TypeReference<List<String>>() {}),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static <T> List<T> parseList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, ref);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
