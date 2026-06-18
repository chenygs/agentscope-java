package io.agentscope.builder.saton.factory.service.model.impl;

import io.agentscope.builder.saton.factory.service.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AnthropicModelProviderType implements ModelProviderType {

    @Override
    public String type() { return "anthropic"; }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "anthropic",
                "Anthropic Claude",
                "Anthropic Messages API（Claude 4.x / Sonnet / Opus / Haiku）",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "Anthropic API Key (sk-ant-...)")
                        .field("modelName", "string", true, "如 claude-sonnet-4-6 / claude-haiku-4-5-20251001")
                        .field("baseUrl", "string", false, "自定义网关")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String apiKey = DashScopeModelProviderType.requireString(props, "apiKey");
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        AnthropicChatModel.Builder b = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
