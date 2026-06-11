package io.agentscope.builder.saton.resource.mcp.dto;

import java.util.Map;

public record McpServerUpsertReq(String name, String type, Map<String, Object> props) {}
