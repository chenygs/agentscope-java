package io.agentscope.builder.saton.factory.service.model.impl;

import io.agentscope.builder.saton.factory.service.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.service.core.TypeMeta;
import io.agentscope.builder.saton.factory.service.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
import io.agentscope.core.model.GeminiChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiModelProviderType implements ModelProviderType {

    @Override
    public String type() { return "gemini"; }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "gemini",
                "Google Gemini",
                "Google Generative AI（Gemini 1.5 / 2.x）",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "Google AI Studio API Key")
                        .field("modelName", "string", true, "如 gemini-2.0-flash / gemini-2.5-pro")
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

        GeminiChatModel.Builder b = GeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
