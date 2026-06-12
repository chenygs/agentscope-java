package io.agentscope.builder.saton.resource.model.dto;

import java.util.Map;

public record ModelProviderUpsertReq(String name, String type, Map<String, Object> props) {}
