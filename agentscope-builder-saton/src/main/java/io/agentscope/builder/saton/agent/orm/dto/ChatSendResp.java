package io.agentscope.builder.saton.agent.orm.dto;

public record ChatSendResp(String reply, Long agentDefId, Long modelProviderIdUsed) {}
