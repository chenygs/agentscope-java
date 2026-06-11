package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

/**
 * 用 agent_definition 行 + model_provider 行装配一个真实可调的 {@link ReActAgent}。
 *
 * <p>M4 只接 model；toolkit 留空（{@code new Toolkit()}）；workspace / skills / hooks 待 M5+。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;

    public AgentBuildOrchestrator(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    public ReActAgent build(AgentDefinitionEntity def, ModelProviderEntity model) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;
        return ReActAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(new Toolkit())
                .maxIters(maxIters)
                .build();
    }
}
