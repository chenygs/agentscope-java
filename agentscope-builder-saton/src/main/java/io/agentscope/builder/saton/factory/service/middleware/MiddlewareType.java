package io.agentscope.builder.saton.factory.service.middleware;

import io.agentscope.builder.saton.factory.service.core.Provider;
import io.agentscope.core.middleware.MiddlewareBase;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for middleware providers. Each implementation declares a unique {@code type()} string
 * used in {@code hook_specs_json} entries.
 *
 * <p>{@code activityDir} is passed so middlewares that write to disk (e.g. audit-jsonl)
 * can target a per-agent activity directory without needing global config.
 */
public interface MiddlewareType extends Provider {

    /** Instantiate a {@link MiddlewareBase} from props + agent activity directory. */
    MiddlewareBase instantiate(Map<String, Object> props, Path activityDir);
}
