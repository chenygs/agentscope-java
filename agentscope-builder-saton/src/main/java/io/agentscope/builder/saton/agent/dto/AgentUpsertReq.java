package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentType;
import io.agentscope.builder.saton.agent.HookSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;

import java.util.List;

/**
 * Agent 创建/更新请求。
 *
 * <p>M7 起新增 skillRepositories / hookSpecs / subagentRefs 三个可空字段；老 8 参 ctor
 * 保留作向后兼容（M5/M6 的测试还在用）。
 */
public record AgentUpsertReq(String agentId,
                             String name,
                             String description,
                             String sysPrompt,
                             AgentType agentType,
                             Long defaultModelProviderId,
                             Integer maxIters,
                             List<ToolSpec> toolSpecs,
                             List<SkillRepoSpec> skillRepositories,
                             List<HookSpec> hookSpecs,
                             List<String> subagentRefs) {

    /** 兼容 M5/M6 老 8 参 ctor —— skill/hook/subagent 默认 null。 */
    public AgentUpsertReq(String agentId,
                          String name,
                          String description,
                          String sysPrompt,
                          AgentType agentType,
                          Long defaultModelProviderId,
                          Integer maxIters,
                          List<ToolSpec> toolSpecs) {
        this(agentId, name, description, sysPrompt, agentType, defaultModelProviderId,
                maxIters, toolSpecs, null, null, null);
    }
}
