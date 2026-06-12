package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.dto.AgentShareVO;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.agent.dto.CloneReq;
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
    private final io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver;
    private final io.agentscope.builder.saton.session.SessionService sessionService;
    private final AgentAccessGuard accessGuard;
    private final AgentShareRepository shareRepo;

    public AgentService(AgentDefinitionRepository repo,
                        ModelProviderRepository modelRepo,
                        io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver,
                        io.agentscope.builder.saton.session.SessionService sessionService,
                        AgentAccessGuard accessGuard,
                        AgentShareRepository shareRepo) {
        this.repo = repo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
        this.sessionService = sessionService;
        this.accessGuard = accessGuard;
        this.shareRepo = shareRepo;
    }

    public List<AgentVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrGranteeId(me).stream().map(AgentVO::from).toList();
    }

    public AgentVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return AgentVO.from(accessGuard.require(id, me, Tier.RUN));
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
        e.setAgentType(req.agentType() != null ? req.agentType() : AgentType.REACT);
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setToolSpecsJson(serializeToolSpecs(req.toolSpecs()));
        e.setSkillRepositoriesJson(serializeJson(req.skillRepositories()));
        e.setHookSpecsJson(serializeJson(req.hookSpecs()));
        e.setSubagentRefsJson(serializeJson(req.subagentRefs()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return AgentVO.from(repo.save(e));
    }

    @Transactional
    public AgentVO update(Long id, AgentUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        requireModelOwned(req.defaultModelProviderId(), me);
        AgentDefinitionEntity e = accessGuard.requireOwner(id, me);
        if (!e.getAgentId().equals(req.agentId())
                && repo.existsByOwnerIdAndAgentId(me, req.agentId())) {
            throw new ConflictException("agentId already exists: " + req.agentId());
        }
        e.setAgentId(req.agentId());
        e.setName(req.name() != null ? req.name() : req.agentId());
        e.setDescription(req.description());
        e.setSysPrompt(req.sysPrompt());
        e.setAgentType(req.agentType() != null ? req.agentType() : AgentType.REACT);
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setToolSpecsJson(serializeToolSpecs(req.toolSpecs()));
        e.setSkillRepositoriesJson(serializeJson(req.skillRepositories()));
        e.setHookSpecsJson(serializeJson(req.hookSpecs()));
        e.setSubagentRefsJson(serializeJson(req.subagentRefs()));
        e.setUpdatedAt(System.currentTimeMillis());
        runtimeResolver.invalidateByAgent(e.getId());
        return AgentVO.from(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        accessGuard.requireOwner(id, me);
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("agent not found: " + id);
        }
        shareRepo.deleteByAgentDefId(id);
        runtimeResolver.invalidateByAgent(id);
        sessionService.purgeAgent(me, id);
    }

    @Transactional
    public void deleteShare(Long agentDefId, Long shareId) {
        long n = shareRepo.deleteByIdAndAgentDefId(shareId, agentDefId);
        if (n == 0) throw new NotFoundException("share not found: " + shareId);
        runtimeResolver.invalidateByAgent(agentDefId);
    }

    @Transactional
    public AgentShareVO createShare(Long agentDefId, String granteeId, String tier, String createdBy) {
        Tier.valueOf(tier); // validates tier value
        long now = System.currentTimeMillis();
        AgentShareEntity e = new AgentShareEntity();
        e.setAgentDefId(agentDefId);
        e.setGranteeType("USER");
        e.setGranteeId(granteeId);
        e.setTier(tier);
        e.setCreatedBy(createdBy);
        e.setCreatedAt(now);
        AgentShareVO result = AgentShareVO.from(shareRepo.save(e));
        runtimeResolver.invalidateByAgent(agentDefId);
        return result;
    }

    @Transactional
    public AgentVO clone(Long sourceId, CloneReq req, String ownerId) {
        AgentDefinitionEntity source = repo.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("source agent not found: " + sourceId));

        if (req.newAgentId() == null || req.newAgentId().isBlank()) {
            throw new IllegalArgumentException("newAgentId required");
        }
        if (repo.existsByOwnerIdAndAgentId(ownerId, req.newAgentId())) {
            throw new ConflictException("agentId already exists: " + req.newAgentId());
        }

        long now = System.currentTimeMillis();
        AgentDefinitionEntity clone = new AgentDefinitionEntity();
        clone.setOwnerId(ownerId);
        clone.setAgentId(req.newAgentId());
        clone.setName(req.name() != null ? req.name() : source.getName());
        clone.setDescription(source.getDescription());
        clone.setSysPrompt(source.getSysPrompt());
        clone.setAgentType(source.getAgentType());
        clone.setDefaultModelProviderId(source.getDefaultModelProviderId());
        clone.setMaxIters(source.getMaxIters());
        clone.setToolSpecsJson(source.getToolSpecsJson());
        clone.setSkillRepositoriesJson(source.getSkillRepositoriesJson());
        clone.setHookSpecsJson(source.getHookSpecsJson());
        clone.setSubagentRefsJson(source.getSubagentRefsJson());
        clone.setForkOf(source.getAgentId());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);

        return AgentVO.from(repo.save(clone));
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

    /** toolSpecs → JSON 字符串；null/empty → null。 */
    private static String serializeToolSpecs(java.util.List<io.agentscope.builder.saton.agent.ToolSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        try {
            return io.agentscope.builder.saton.common.json.JsonUtil.mapper().writeValueAsString(specs);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid toolSpecs", ex);
        }
    }

    /** 通用 list → JSON 字符串；null/empty → null。 */
    private static String serializeJson(java.util.List<?> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return io.agentscope.builder.saton.common.json.JsonUtil.mapper().writeValueAsString(list);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid json payload", ex);
        }
    }
}
