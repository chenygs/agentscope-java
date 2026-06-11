package io.agentscope.builder.saton.resource.repository.dto;

import java.util.Map;

public record SkillRepositoryUpsertReq(String name, String type, Map<String, Object> props) {}
