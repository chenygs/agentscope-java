package io.agentscope.builder.saton.agent.dto;

/**
 * Create / update agent 的请求体。M4 字段最小集 —— tool/skill/hook 等扩展字段以后再加。
 */
public record AgentUpsertReq(
        String agentId,             // 业务唯一标识（per-owner）
        String name,
        String description,
        String sysPrompt,
        String agentType,           // "react" / "harness"（M4 默认 react）
        Long defaultModelProviderId,
        Integer maxIters
) {}
