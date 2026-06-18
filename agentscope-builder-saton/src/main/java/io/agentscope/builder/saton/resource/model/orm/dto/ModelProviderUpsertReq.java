package io.agentscope.builder.saton.resource.model.orm.dto;

import java.util.Map;

public record ModelProviderUpsertReq(String name, String type, Map<String, Object> props) {}
