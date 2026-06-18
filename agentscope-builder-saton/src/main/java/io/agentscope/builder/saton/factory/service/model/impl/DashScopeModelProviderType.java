package io.agentscope.builder.saton.factory.service.model.impl;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.service.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DashScopeModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "dashscope";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "dashscope",
                "通义千问 (DashScope)",
                "阿里云 DashScope OpenAI-compatible / Generation 接口",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "DashScope API Key (sk-...)")
                        .field("modelName", "string", true, "模型名，如 qwen-max / qwen-plus / qwen-turbo")
                        .field("baseUrl", "string", false, "自定义网关地址；不填走官方")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = readProps(entity);
        String apiKey = requireString(props, "apiKey");
        String modelName = requireString(props, "modelName");
        String baseUrl = optionalString(props, "baseUrl");

        DashScopeChatModel.Builder b = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }

    /* ----- shared helpers; the other 4 impls reference these via package-static call ----- */

    static JsonNode readProps(ModelProviderEntity e) {
        try {
            String json = (e.getPropsJson() == null || e.getPropsJson().isBlank())
                    ? "{}" : e.getPropsJson();
            return JsonUtil.mapper().readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid propsJson on model provider " + e.getId(), ex);
        }
    }

    static String requireString(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isString() || v.asString().isBlank()) {
            throw new IllegalArgumentException("model props missing required field: " + field);
        }
        return v.asString();
    }

    static String optionalString(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || !v.isString() || v.asString().isBlank()) ? null : v.asString();
    }
}
