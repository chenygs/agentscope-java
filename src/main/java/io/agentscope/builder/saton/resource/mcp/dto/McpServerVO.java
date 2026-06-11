package io.agentscope.builder.saton.resource.mcp.dto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.mcp.McpServerEntity;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public record McpServerVO(
        Long id,
        String name,
        String type,
        Map<String, Object> props,
        long createdAt,
        long updatedAt
) {
    public static McpServerVO maskedFrom(McpServerEntity e) {
        String maskedJson = ResourceCommon.maskSensitive(
                ResourceCommon.normalizePropsJson(e.getPropsJson()));
        Map<String, Object> propsMap = jsonToMap(maskedJson);
        return new McpServerVO(
                e.getId(), e.getName(), e.getType(), propsMap,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(String json) {
        try {
            JsonNode node = JsonUtil.mapper().readTree(json);
            return JsonUtil.mapper().treeToValue(node, HashMap.class);
        } catch (Exception e) {
            throw new IllegalStateException("VO json -> map failed", e);
        }
    }
}
