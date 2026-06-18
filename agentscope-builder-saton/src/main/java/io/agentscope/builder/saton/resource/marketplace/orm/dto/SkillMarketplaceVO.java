package io.agentscope.builder.saton.resource.marketplace.orm.dto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.resource.service.ResourceCommon;
import io.agentscope.builder.saton.resource.marketplace.orm.entity.SkillMarketplaceEntity;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public record SkillMarketplaceVO(
        Long id,
        String marketplaceId,
        String type,
        Map<String, Object> props,
        long createdAt,
        long updatedAt
) {
    public static SkillMarketplaceVO maskedFrom(SkillMarketplaceEntity e) {
        String maskedJson = ResourceCommon.maskSensitive(
                ResourceCommon.normalizePropsJson(e.getPropsJson()));
        Map<String, Object> propsMap = jsonToMap(maskedJson);
        return new SkillMarketplaceVO(
                e.getId(), e.getMarketplaceId(), e.getType(), propsMap,
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
