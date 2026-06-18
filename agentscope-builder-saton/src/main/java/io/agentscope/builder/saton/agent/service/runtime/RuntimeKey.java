package io.agentscope.builder.saton.agent.service.runtime;

/**
 * AgentRuntimeResolver 的缓存 key：{@code (agentDefId, modelProviderId)}。
 *
 * <p>同一个 agent 切换模型时，两个不同 key 各自缓存一个 ReActAgent 实例；
 * agent 自身 invalidate 时按 agentDefId 一并丢弃所有相关 key。
 */
public record RuntimeKey(Long agentDefId, Long modelProviderId) {}
