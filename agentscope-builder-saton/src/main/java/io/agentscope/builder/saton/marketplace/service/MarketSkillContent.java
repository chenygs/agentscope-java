package io.agentscope.builder.saton.marketplace.service;

import java.util.Map;

/**
 * Full skill payload returned by {@link BuilderMarketplace#fetch(String)}. {@code markdown} is the
 * SKILL.md body; {@code resources} are sibling files keyed by their workspace-relative path
 * (e.g. {@code "templates/intro.md"} -> contents).
 *
 * @param name        skill identifier; matches the {@link MarketSkillSummary#name()} requested
 * @param description one-line summary mirrored from the summary so callers don't need a second lookup
 * @param markdown    SKILL.md body; must be present and non-empty
 * @param resources   relative-path -> file contents for every side file; never null, may be empty
 */
public record MarketSkillContent(
        String name, String description, String markdown, Map<String, String> resources) {}
