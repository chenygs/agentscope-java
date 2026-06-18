package io.agentscope.builder.saton.marketplace.service;

import java.util.List;

/**
 * A builder-managed skill marketplace. Browsed and configured via the UI, independent of the
 * runtime {@code skillRepositories} that an agent itself loads.
 *
 * <p>Implementations are stateful (open git clones, open nacos clients) and must be closed
 * when the registry replaces or removes them.
 */
public interface BuilderMarketplace extends AutoCloseable {

    /** Stable id, chosen by the user when the marketplace was created. */
    String id();

    /** Discriminator used by the UI for badges and config forms ({@code "git"} / {@code "nacos"}). */
    String type();

    /** Human-readable location shown in the UI (URL, server address, etc.). Never includes credentials. */
    String displayLocation();

    /**
     * Whether this marketplace would accept writes — builder treats marketplaces as read-only today,
     * but UI may surface this.
     */
    default boolean writable() {
        return false;
    }

    /**
     * List all skills exposed by this marketplace. Implementations may cache or hit the upstream
     * on every call; callers should treat this as potentially slow and let the UI lazy-load.
     */
    List<MarketSkillSummary> list();

    /**
     * Fetch the full content (SKILL.md plus any side resources) for the named skill, or
     * {@code null} if it does not exist.
     */
    MarketSkillContent fetch(String name);

    /** Release upstream resources (close git, shut down nacos client). Safe to call repeatedly. */
    @Override
    void close();
}
