package io.agentscope.builder.saton.marketplace;

/**
 * Lightweight skill descriptor returned by {@link BuilderMarketplace#list()}. Used to populate
 * the marketplace browser without paying the cost of downloading every SKILL.md.
 *
 * @param name        stable identifier the user installs by
 * @param description one-line summary shown in the UI; may be empty but never null
 * @param version     upstream version string, or {@code null} if the source has no concept of
 *                    versions (e.g. git repository without tags)
 */
public record MarketSkillSummary(String name, String description, String version) {}
