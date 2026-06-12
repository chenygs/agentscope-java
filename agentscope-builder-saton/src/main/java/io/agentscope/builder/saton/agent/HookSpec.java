package io.agentscope.builder.saton.agent;

import java.util.Map;

/**
 * One entry in {@code hook_specs_json}.
 *
 * <p>JSON shape: {@code {"type":"logging","props":{}}}
 */
public record HookSpec(String type, Map<String, Object> props) { }
