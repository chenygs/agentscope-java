package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentService {

    private final AgentDefinitionRepository repo;
    private final ModelProviderRepository modelRepo;

    public AgentService(AgentDefinitionRepository repo, ModelProviderRepository modelRepo) {
        this.repo = repo;
        this.modelRepo = modelRepo;
    }

    public List<AgentVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me).stream().map(AgentVO::from).toList();
    }

    public AgentVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return AgentVO.from(loadMine(id, me));
    }

    @Transactional
    public AgentVO create(AgentUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        requireModelOwned(req.defaultModelProviderId(), me);
        if (repo.existsByOwnerIdAndAgentId(me, req.agentId())) {
            throw new ConflictException("agentId already exists: " + req.agentId());
        }
        long now = System.currentTimeMillis();
        AgentDefinitionEntity e = new AgentDefinitionEntity();
        e.setOwnerId(me);
        e.setAgentId(req.agentId());
        e.setName(req.name() != null ? req.name() : req.agentId());
        e.setDescription(req.description());
        e.setSysPrompt(req.sysPrompt());
        e.setAgentType(req.agentType() != null ? req.agentType() : "react");
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return AgentVO.from(repo.save(e));
    }

    @Transactional
    public AgentVO update(Long id, AgentUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        requireModelOwned(req.defaultModelProviderId(), me);
        AgentDefinitionEntity e = loadMine(id, me);
        if (!e.getAgentId().equals(req.agentId())
                && repo.existsByOwnerIdAndAgentId(me, req.agentId())) {
            throw new ConflictException("agentId already exists: " + req.agentId());
        }
        e.setAgentId(req.agentId());
        e.setName(req.name() != null ? req.name() : req.agentId());
        e.setDescription(req.description());
        e.setSysPrompt(req.sysPrompt());
        e.setAgentType(req.agentType() != null ? req.agentType() : "react");
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setUpdatedAt(System.currentTimeMillis());
        // M4-3 完成后这里调 runtimeResolver.invalidateByAgent(e.getId())；
        // 本步骤先不调（解耦）。
        return AgentVO.from(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("agent not found: " + id);
        }
        // M4-3 完成后这里调 runtimeResolver.invalidateByAgent(id)；本步骤先不调。
    }

    private AgentDefinitionEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("agent not found: " + id));
    }

    private void requireFields(AgentUpsertReq req) {
        if (req == null
                || req.agentId() == null || req.agentId().isBlank()
                || req.defaultModelProviderId() == null) {
            throw new IllegalArgumentException("agentId and defaultModelProviderId required");
        }
    }

    private void requireModelOwned(Long modelId, String me) {
        if (modelRepo.findByIdAndOwnerId(modelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + modelId);
        }
    }
}
