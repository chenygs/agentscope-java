package io.agentscope.builder.saton.resource.mcp.orm.dto;

import java.util.Map;

public record McpServerUpsertReq(String name, String type, Map<String, Object> props) {}
