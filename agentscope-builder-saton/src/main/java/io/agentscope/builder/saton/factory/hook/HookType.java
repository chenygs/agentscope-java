package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.factory.core.Provider;
import io.agentscope.core.hook.Hook;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for hook providers. Each implementation declares a unique {@code type()} string used in
 * {@code hook_specs_json} entries.
 *
 * <p>{@code activityDir} is passed so hooks that write to disk (e.g. audit-jsonl) can target
 * a per-agent activity directory without needing global config.
 */
public interface HookType extends Provider {

    /** Instantiate a {@link Hook} from props + agent activity directory. */
    @SuppressWarnings("deprecation") // Hook is deprecated in core but still supported by HarnessAgent.Builder
    Hook instantiate(Map<String, Object> props, Path activityDir);
}
