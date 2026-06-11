package io.agentscope.builder.saton.resource.model;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelProviderService {

    private final ModelProviderRepository repo;

    public ModelProviderService(ModelProviderRepository repo) {
        this.repo = repo;
    }

    public List<ModelProviderVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me)
                .stream().map(ModelProviderVO::maskedFrom).toList();
    }

    public ModelProviderVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return ModelProviderVO.maskedFrom(loadMine(id, me));
    }

    @Transactional
    public ModelProviderVO create(ModelProviderUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        if (repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        long now = System.currentTimeMillis();
        ModelProviderEntity e = new ModelProviderEntity();
        e.setOwnerId(me);
        e.setName(req.name());
        e.setType(req.type());
        e.setPropsJson(ResourceCommon.mapToJson(req.props()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return ModelProviderVO.maskedFrom(repo.save(e));
    }

    @Transactional
    public ModelProviderVO update(Long id, ModelProviderUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        ModelProviderEntity e = loadMine(id, me);
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
        return ModelProviderVO.maskedFrom(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("model provider not found: " + id);
        }
    }

    private ModelProviderEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("model provider not found: " + id));
    }

    private void requireFields(ModelProviderUpsertReq req) {
        if (req == null
                || req.name() == null || req.name().isBlank()
                || req.type() == null || req.type().isBlank()) {
            throw new IllegalArgumentException("name and type required");
        }
    }
}
