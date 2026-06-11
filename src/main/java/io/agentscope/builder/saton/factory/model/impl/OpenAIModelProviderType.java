package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class OpenAIModelProviderType implements ModelProviderType {

    @Override
    public String type() { return "openai"; }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "openai",
                "OpenAI",
                "OpenAI Chat Completions / Compatible",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "OpenAI API Key")
                        .field("modelName", "string", true, "如 gpt-4o / gpt-4o-mini")
                        .field("baseUrl", "string", false, "自定义网关 / Compatible Endpoint")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String apiKey = DashScopeModelProviderType.requireString(props, "apiKey");
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        OpenAIChatModel.Builder b = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
