package io.agentscope.builder.saton.skill.dto;

public record MarketplaceInstallReq(String marketplaceId, String skillName, String targetName, Boolean overwrite) {}
