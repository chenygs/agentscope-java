package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * 用 agent_definition 行 + model_provider 行装配一个真实可调的 {@link ReActAgent}。
 *
 * <p>M5 起接入 ToolFactory：解析 {@code toolSpecsJson} 中每个 {@link ToolSpec} 通过
 * {@link ToolFactory#instantiate(String, java.util.Map)} 实例化工具对象，再统一注入到
 * agent 的 {@link Toolkit}。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;

    public AgentBuildOrchestrator(ModelFactory modelFactory, ToolFactory toolFactory) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
    }

    public ReActAgent build(AgentDefinitionEntity def, ModelProviderEntity model) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            Object tool = toolFactory.instantiate(spec.type(), spec.props());
            toolkit.registerTool(tool);
        }

        return ReActAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .build();
    }

    private static List<ToolSpec> parseToolSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<ToolSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid tool_specs_json", e);
        }
    }
}
