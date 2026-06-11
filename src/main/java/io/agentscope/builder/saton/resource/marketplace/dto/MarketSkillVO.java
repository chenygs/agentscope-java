package io.agentscope.builder.saton.resource.marketplace.dto;

import io.agentscope.builder.saton.marketplace.MarketSkillContent;

import java.util.Map;

public record MarketSkillVO(String name, String description, String markdown, Map<String, String> resources) {
    public static MarketSkillVO from(MarketSkillContent c) {
        return new MarketSkillVO(c.name(), c.description(), c.markdown(), c.resources());
    }
}
