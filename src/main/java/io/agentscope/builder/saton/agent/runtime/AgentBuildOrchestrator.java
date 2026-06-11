package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 用 agent_definition 行 + model_provider 行 + ownerId 装配一个真实可调的 {@link HarnessAgent}。
 *
 * <p>M7 起：返回类型从 ReActAgent 切到 HarnessAgent。HarnessAgent 接口和 ReActAgent 一致
 * （都有 call / streamEvents），ChatService 改个 import 即可；HarnessAgent 多出来的
 * skillRepository / subagentFactory / workspace context 等 M7 后续 task 接入。
 *
 * <p>workspace 路径由 {@link WorkspacePathResolver#agentRoot(String, Long)} 给出,
 * orchestrator 负责 mkdir 后传给 HarnessAgent.Builder.workspace(...)。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;
    private final AgentStateStore stateStore;
    private final WorkspacePathResolver workspaceResolver;

    public AgentBuildOrchestrator(ModelFactory modelFactory,
                                  ToolFactory toolFactory,
                                  AgentStateStore stateStore,
                                  WorkspacePathResolver workspaceResolver) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.stateStore = stateStore;
        this.workspaceResolver = workspaceResolver;
    }

    public HarnessAgent build(AgentDefinitionEntity def,
                              ModelProviderEntity model,
                              String ownerId) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            Object tool = toolFactory.instantiate(spec.type(), spec.props());
            toolkit.registerTool(tool);
        }

        Path workspace = workspaceResolver.agentRoot(ownerId, def.getId());
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to mkdir workspace: " + workspace, e);
        }

        return HarnessAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .stateStore(stateStore)
                .defaultSessionId("agent_" + def.getId() + "_default")
                .workspace(workspace)
                // M7-1 见 spec §12.18 — 关掉默认的 dynamic skill + workspace context middleware，
                // 它们的 onSystemPrompt 实现内部 Mono.block() 会在 WebFlux Netty loop 上抛
                // IllegalStateException。后续 task 启用 skill 时配合 Schedulers.boundedElastic
                // offload 再去掉这两行。
                .disableDynamicSkills()
                .disableWorkspaceContext()
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
