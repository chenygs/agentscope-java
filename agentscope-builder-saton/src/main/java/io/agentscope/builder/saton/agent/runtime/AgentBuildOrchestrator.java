package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.MiddlewareSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.middleware.MiddlewareFactory;
import io.agentscope.builder.saton.factory.skill.SkillFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link HarnessAgent} from agent_definition + model_provider + ownerId.
 *
 * <p>M7 wires skill repositories (skill_repositories_json), middlewares (hook_specs_json),
 * and subagent factories (subagent_refs_json) on top of M5/M6's model + tools + state.
 *
 * <p>Subagent factories are recursive: parent declares child agent_id in subagent_refs;
 * orchestrator looks up child's AgentDefinitionEntity by (ownerId, agent_id) and recursively
 * builds a HarnessAgent for it.
 *
 * <p>@Lazy on AgentDefinitionRepository + ModelProviderRepository 避免 Spring 启动期
 * DI 死循环（orchestrator → resolver → AgentService → orchestrator 之类）。
 */
@Slf4j
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;
    private final SkillFactory skillFactory;
    private final MiddlewareFactory middlewareFactory;
    private final AgentStateStore stateStore;
    private final WorkspacePathResolver workspaceResolver;
    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;

    public AgentBuildOrchestrator(ModelFactory modelFactory,
                                  ToolFactory toolFactory,
                                  SkillFactory skillFactory,
                                  MiddlewareFactory middlewareFactory,
                                  AgentStateStore stateStore,
                                  WorkspacePathResolver workspaceResolver,
                                  @Lazy AgentDefinitionRepository agentRepo,
                                  @Lazy ModelProviderRepository modelRepo) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.skillFactory = skillFactory;
        this.middlewareFactory = middlewareFactory;
        this.stateStore = stateStore;
        this.workspaceResolver = workspaceResolver;
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
    }

    public HarnessAgent build(AgentDefinitionEntity def,
                              ModelProviderEntity model,
                              String ownerId) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            try {
                toolkit.registerTool(toolFactory.instantiate(spec.type(), spec.props()));
            } catch (RuntimeException e) {
                log.warn("skip tool type={} due to {}", spec.type(), e.getMessage());
            }
        }

        // Harness 自己会在 workspace 下挂 agents/<id>/sessions/ 这层,我们传 user 级根 —
        // 这样 MEMORY.md / AGENTS.md / skills/ 自然落在 <userId>/ 根下,跨 agent 共享。
        Path workspace = workspaceResolver.userRoot(ownerId);
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to mkdir workspace: " + workspace, e);
        }

        List<AgentSkillRepository> skillRepos = new ArrayList<>();
        for (SkillRepoSpec spec : parseSkillRepoSpecs(def.getSkillRepositoriesJson())) {
            try {
                skillRepos.add(skillFactory.instantiate(spec.type(), spec.props(), workspace));
            } catch (RuntimeException e) {
                log.warn("skip skill repo type={} due to {}", spec.type(), e.getMessage());
            }
        }

        List<MiddlewareBase> middlewares = new ArrayList<>();
        for (MiddlewareSpec spec : parseMiddlewareSpecs(def.getHookSpecsJson())) {
            try {
                middlewares.add(middlewareFactory.instantiate(spec.type(), spec.props(), workspace));
            } catch (RuntimeException e) {
                log.warn("skip middleware type={} due to {}", spec.type(), e.getMessage());
            }
        }

        HarnessAgent.Builder b = HarnessAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .stateStore(stateStore)
                .defaultSessionId("agent_" + def.getId() + "_default")
                .workspace(workspace);

        if (!skillRepos.isEmpty()) {
            b.skillRepositories(skillRepos);
        }
        for (MiddlewareBase mw : middlewares) {
            b.middleware(mw);
        }

        // Subagents: look up each ref by (ownerId, agentId), build recursively.
        for (String childAgentId : parseSubagentRefs(def.getSubagentRefsJson())) {
            b.subagentFactory(childAgentId, name -> buildChildAgent(name, ownerId));
        }

        return b.build();
    }

    private io.agentscope.core.agent.Agent buildChildAgent(String childAgentId, String ownerId) {
        AgentDefinitionEntity child = agentRepo.findByOwnerIdAndAgentId(ownerId, childAgentId)
                .orElseThrow(() -> new IllegalStateException(
                        "subagent not found: " + childAgentId + " (owner=" + ownerId + ")"));
        ModelProviderEntity childModel = modelRepo
                .findByIdAndOwnerId(child.getDefaultModelProviderId(), ownerId)
                .orElseThrow(() -> new IllegalStateException(
                        "subagent's model not found: " + child.getDefaultModelProviderId()));
        return build(child, childModel, ownerId);
    }

    private static List<ToolSpec> parseToolSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<ToolSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid tool_specs_json", e);
        }
    }

    private static List<SkillRepoSpec> parseSkillRepoSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<SkillRepoSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid skill_repositories_json", e);
        }
    }

    private static List<MiddlewareSpec> parseMiddlewareSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<MiddlewareSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid hook_specs_json", e);
        }
    }

    private static List<String> parseSubagentRefs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid subagent_refs_json", e);
        }
    }
}
