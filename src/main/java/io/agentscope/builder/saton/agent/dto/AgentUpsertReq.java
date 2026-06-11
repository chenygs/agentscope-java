package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.ToolSpec;

import java.util.List;

/**
 * Create / update agent 的请求体。
 *
 * <p>{@code toolSpecs} 为 null 表示“不带工具”（M5 之前的默认）；空列表也是同样语义。
 */
public record AgentUpsertReq(
        String agentId,
        String name,
        String description,
        String sysPrompt,
        String agentType,
        Long defaultModelProviderId,
        Integer maxIters,
        List<ToolSpec> toolSpecs
) {}
