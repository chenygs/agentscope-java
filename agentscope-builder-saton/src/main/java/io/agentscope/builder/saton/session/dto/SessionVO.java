package io.agentscope.builder.saton.session.dto;

/** 一个 session 的简要视图。 */
public record SessionVO(String sessionKey, long lastActiveAt) { }
