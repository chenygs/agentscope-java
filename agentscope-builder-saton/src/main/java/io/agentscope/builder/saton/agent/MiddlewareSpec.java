package io.agentscope.builder.saton.agent;

import java.util.Map;

/**
 * One entry in {@code hook_specs_json} (DB column name kept for backward compat).
 *
 * <p>JSON shape: {@code {"type":"logging","props":{}}}
 */
public record MiddlewareSpec(String type, Map<String, Object> props) { }
