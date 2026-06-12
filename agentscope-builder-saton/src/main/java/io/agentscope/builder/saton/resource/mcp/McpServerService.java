package io.agentscope.builder.saton.resource.mcp;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerUpsertReq;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class McpServerService {

    private final McpServerRepository repo;

    public McpServerService(McpServerRepository repo) {
        this.repo = repo;
    }

    public List<McpServerVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me)
                .stream().map(McpServerVO::maskedFrom).toList();
    }

    public McpServerVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return McpServerVO.maskedFrom(loadMine(id, me));
    }

    @Transactional
    public McpServerVO create(McpServerUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        if (repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        long now = System.currentTimeMillis();
        McpServerEntity e = new McpServerEntity();
        e.setOwnerId(me);
        e.setName(req.name());
        e.setType(req.type());
        e.setPropsJson(ResourceCommon.mapToJson(req.props()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return McpServerVO.maskedFrom(repo.save(e));
    }

    @Transactional
    public McpServerVO update(Long id, McpServerUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        McpServerEntity e = loadMine(id, me);
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
        return McpServerVO.maskedFrom(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("mcp server not found: " + id);
        }
    }

    private McpServerEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("mcp server not found: " + id));
    }

    private void requireFields(McpServerUpsertReq req) {
        if (req == null
                || req.name() == null || req.name().isBlank()
                || req.type() == null || req.type().isBlank()) {
            throw new IllegalArgumentException("name and type required");
        }
    }
}
