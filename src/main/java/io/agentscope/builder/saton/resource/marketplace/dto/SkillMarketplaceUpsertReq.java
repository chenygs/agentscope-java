package io.agentscope.builder.saton.resource.marketplace.dto;

import java.util.Map;

public record SkillMarketplaceUpsertReq(String marketplaceId, String type, Map<String, Object> props) {}
