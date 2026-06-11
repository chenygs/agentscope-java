package io.agentscope.builder.saton.agent.chat.dto;

public record ChatSendReq(String message, Long overrideModelProviderId) {}
