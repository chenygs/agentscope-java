package io.agentscope.builder.saton.resource.marketplace.orm.dto;

import io.agentscope.builder.saton.marketplace.service.MarketSkillSummary;

public record MarketSkillSummaryVO(String name, String description, String version) {
    public static MarketSkillSummaryVO from(MarketSkillSummary s) {
        return new MarketSkillSummaryVO(s.name(), s.description(), s.version());
    }
}
