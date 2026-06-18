package io.agentscope.builder.saton.resource.skill.service;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.service.ResourceCommon;
import io.agentscope.builder.saton.resource.skill.orm.dto.SkillRepositoryUpsertReq;
import io.agentscope.builder.saton.resource.skill.orm.dto.SkillRepositoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import io.agentscope.builder.saton.resource.skill.orm.repository.SkillRepositoryRepository;
import io.agentscope.builder.saton.resource.skill.orm.entity.SkillRepositoryEntity;

@Service
public class SkillRepositoryService {

    private final SkillRepositoryRepository repo;

    public SkillRepositoryService(SkillRepositoryRepository repo) {
        this.repo = repo;
    }

    public List<SkillRepositoryVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me)
                .stream().map(SkillRepositoryVO::maskedFrom).toList();
    }

    public SkillRepositoryVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return SkillRepositoryVO.maskedFrom(loadMine(id, me));
    }

    @Transactional
    public SkillRepositoryVO create(SkillRepositoryUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        if (repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        long now = System.currentTimeMillis();
        SkillRepositoryEntity e = new SkillRepositoryEntity();
        e.setOwnerId(me);
        e.setName(req.name());
        e.setType(req.type());
        e.setPropsJson(ResourceCommon.mapToJson(req.props()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return SkillRepositoryVO.maskedFrom(repo.save(e));
    }

    @Transactional
    public SkillRepositoryVO update(Long id, SkillRepositoryUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        SkillRepositoryEntity e = loadMine(id, me);
        if (!e.getName().equals(req.name())
                && repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        e.setName(req.name());
        e.setType(req.type());
        // merge：incoming 里 value = "***" 的敏感字段保留 existing 值
        String incoming = ResourceCommon.mapToJson(req.props());
        String merged = ResourceCommon.mergeKeepMasked(incoming, e.getPropsJson());
        e.setPropsJson(merged);
        e.setUpdatedAt(System.currentTimeMillis());
        return SkillRepositoryVO.maskedFrom(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("skill repository not found: " + id);
        }
    }

    private SkillRepositoryEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("skill repository not found: " + id));
    }

    private void requireFields(SkillRepositoryUpsertReq req) {
        if (req == null
                || req.name() == null || req.name().isBlank()
                || req.type() == null || req.type().isBlank()) {
            throw new IllegalArgumentException("name and type required");
        }
    }
}
