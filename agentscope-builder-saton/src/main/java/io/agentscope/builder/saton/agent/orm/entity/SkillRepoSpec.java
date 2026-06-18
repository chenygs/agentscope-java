package io.agentscope.builder.saton.agent.orm.entity;

import java.util.Map;

/**
 * One entry in {@code skill_repositories_json}.
 *
 * <p>JSON shape: {@code {"type":"local","props":{"path":"skills"}}}
 */
public record SkillRepoSpec(String type, Map<String, Object> props) { }
