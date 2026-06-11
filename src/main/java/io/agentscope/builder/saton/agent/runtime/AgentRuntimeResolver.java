package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.core.ReActAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (agentDefId, modelProviderId) → {@link ReActAgent} 缓存。
 *
 * <p>语义：
 * <ul>
 *   <li>{@link #resolve(Long, Long)} 拿到（或惰性构造）缓存的 agent</li>
 *   <li>{@link #invalidateByAgent(Long)} agent 自身改/删时，移除所有相关 key</li>
 *   <li>{@link #invalidateByModel(Long)} model 改/删时，移除所有用这个 model 的 key</li>
 * </ul>
 */
@Component
public class AgentRuntimeResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeResolver.class);

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentBuildOrchestrator orchestrator;

    private final Map<RuntimeKey, ReActAgent> cache = new ConcurrentHashMap<>();
    /** 反向索引：byAgent[defId] 集合中所有 RuntimeKey 用了这个 defId。 */
    private final Map<Long, Set<RuntimeKey>> byAgent = new ConcurrentHashMap<>();
    /** 反向索引：byModel[modelId] 集合中所有 RuntimeKey 用了这个 modelId。 */
    private final Map<Long, Set<RuntimeKey>> byModel = new ConcurrentHashMap<>();

    public AgentRuntimeResolver(AgentDefinitionRepository agentRepo,
                                ModelProviderRepository modelRepo,
                                AgentBuildOrchestrator orchestrator) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.orchestrator = orchestrator;
    }

    public ReActAgent resolve(Long agentDefId, Long modelProviderId) {
        RuntimeKey key = new RuntimeKey(agentDefId, modelProviderId);
        return cache.computeIfAbsent(key, k -> {
            AgentDefinitionEntity def = agentRepo.findById(agentDefId)
                    .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
            ModelProviderEntity model = modelRepo.findById(modelProviderId)
                    .orElseThrow(() -> new NotFoundException("model provider not found: " + modelProviderId));
            ReActAgent built = orchestrator.build(def, model);
            byAgent.computeIfAbsent(agentDefId, x -> ConcurrentHashMap.newKeySet()).add(k);
            byModel.computeIfAbsent(modelProviderId, x -> ConcurrentHashMap.newKeySet()).add(k);
            log.debug("built ReActAgent for {}/{}", agentDefId, modelProviderId);
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

    /** 测试用：当前缓存条目数量。 */
    int cacheSize() {
        return cache.size();
    }
}
