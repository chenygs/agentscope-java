package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (agentDefId, modelProviderId) → {@link HarnessAgent} 缓存。
 *
 * <p>M7 起：缓存值类型从 ReActAgent 切到 HarnessAgent。resolve 多接一个 ownerId 参数
 * （HarnessAgent 需要 workspace 路径，路径取决于 ownerId）；cache key 仍是 (defId, modelId)，
 * 因为 agent 本身已经隐含 ownership。
 */
@Slf4j
@Component
public class AgentRuntimeResolver {

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentBuildOrchestrator orchestrator;

    private final Map<RuntimeKey, HarnessAgent> cache = new ConcurrentHashMap<>();
    private final Map<Long, Set<RuntimeKey>> byAgent = new ConcurrentHashMap<>();
    private final Map<Long, Set<RuntimeKey>> byModel = new ConcurrentHashMap<>();

    public AgentRuntimeResolver(AgentDefinitionRepository agentRepo,
                                ModelProviderRepository modelRepo,
                                AgentBuildOrchestrator orchestrator) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.orchestrator = orchestrator;
    }

    public HarnessAgent resolve(Long agentDefId, Long modelProviderId, String ownerId) {
        RuntimeKey key = new RuntimeKey(agentDefId, modelProviderId);
        return cache.computeIfAbsent(key, k -> {
            AgentDefinitionEntity def = agentRepo.findById(agentDefId)
                    .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
            ModelProviderEntity model = modelRepo.findById(modelProviderId)
                    .orElseThrow(() -> new NotFoundException("model provider not found: " + modelProviderId));
            HarnessAgent built = orchestrator.build(def, model, ownerId);
            byAgent.computeIfAbsent(agentDefId, x -> ConcurrentHashMap.newKeySet()).add(k);
            byModel.computeIfAbsent(modelProviderId, x -> ConcurrentHashMap.newKeySet()).add(k);
            log.debug("built HarnessAgent for {}/{} (owner={})", agentDefId, modelProviderId, ownerId);
            return built;
        });
    }

    public void invalidateByAgent(Long agentDefId) {
        Set<RuntimeKey> keys = byAgent.remove(agentDefId);
        if (keys == null) return;
        for (RuntimeKey k : keys) {
            cache.remove(k);
            Set<RuntimeKey> mKeys = byModel.get(k.modelProviderId());
            if (mKeys != null) mKeys.remove(k);
        }
        log.debug("invalidated {} cache entries for agent {}", keys.size(), agentDefId);
    }

    public void invalidateByModel(Long modelProviderId) {
        Set<RuntimeKey> keys = byModel.remove(modelProviderId);
        if (keys == null) return;
        for (RuntimeKey k : keys) {
            cache.remove(k);
            Set<RuntimeKey> aKeys = byAgent.get(k.agentDefId());
            if (aKeys != null) aKeys.remove(k);
        }
        log.debug("invalidated {} cache entries for model {}", keys.size(), modelProviderId);
    }

    int cacheSize() {
        return cache.size();
    }
}
