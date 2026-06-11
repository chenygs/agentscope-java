package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 本地 Ollama —— 没有 apiKey，只需 modelName + baseUrl（默认 http://localhost:11434）。
 */
@Component
public class OllamaModelProviderType implements ModelProviderType {

    @Override
    public String type() { return "ollama"; }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "ollama",
                "Ollama (本地)",
                "本地 Ollama 服务，默认监听 http://localhost:11434",
                JsonSchemaUtil.object()
                        .field("modelName", "string", true, "如 llama3.2 / qwen2.5:7b")
                        .field("baseUrl", "string", false, "默认 http://localhost:11434")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        OllamaChatModel.Builder b = OllamaChatModel.builder()
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
