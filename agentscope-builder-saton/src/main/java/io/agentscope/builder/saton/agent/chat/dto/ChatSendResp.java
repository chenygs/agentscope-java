package io.agentscope.builder.saton.agent.chat.dto;

public record ChatSendResp(String reply, Long agentDefId, Long modelProviderIdUsed) {}
