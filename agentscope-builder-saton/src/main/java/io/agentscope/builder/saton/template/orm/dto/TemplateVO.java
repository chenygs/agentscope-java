package io.agentscope.builder.saton.template.orm.dto;

import java.util.Map;

public record TemplateVO(String id, String name, String description, Map<String, Object> agent) {}
