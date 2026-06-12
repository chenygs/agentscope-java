package io.agentscope.builder.saton.resource.marketplace;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.marketplace.BuilderMarketplace;
import io.agentscope.builder.saton.marketplace.MarketSkillContent;
import io.agentscope.builder.saton.marketplace.UserMarketplaceRegistry;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.marketplace.dto.MarketSkillSummaryVO;
import io.agentscope.builder.saton.resource.marketplace.dto.MarketSkillVO;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceUpsertReq;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SkillMarketplaceService {

    private final SkillMarketplaceRepository repo;
    private final UserMarketplaceRegistry marketplaceRegistry;

    public SkillMarketplaceService(SkillMarketplaceRepository repo,
                                   UserMarketplaceRegistry marketplaceRegistry) {
        this.repo = repo;
        this.marketplaceRegistry = marketplaceRegistry;
    }

    public List<SkillMarketplaceVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me)
                .stream().map(SkillMarketplaceVO::maskedFrom).toList();
    }

    public SkillMarketplaceVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return SkillMarketplaceVO.maskedFrom(loadMine(id, me));
    }

    @Transactional
    public SkillMarketplaceVO create(SkillMarketplaceUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        if (repo.existsByOwnerIdAndMarketplaceId(me, req.marketplaceId())) {
            throw new ConflictException("marketplaceId already exists: " + req.marketplaceId());
        }
        long now = System.currentTimeMillis();
        SkillMarketplaceEntity e = new SkillMarketplaceEntity();
        e.setOwnerId(me);
        e.setMarketplaceId(req.marketplaceId());
        e.setType(req.type());
        e.setPropsJson(ResourceCommon.mapToJson(req.props()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        SkillMarketplaceVO result = SkillMarketplaceVO.maskedFrom(repo.save(e));
        marketplaceRegistry.invalidate(me);
        return result;
    }

    @Transactional
    public SkillMarketplaceVO update(Long id, SkillMarketplaceUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        SkillMarketplaceEntity e = loadMine(id, me);
        if (!e.getMarketplaceId().equals(req.marketplaceId())
                && repo.existsByOwnerIdAndMarketplaceId(me, req.marketplaceId())) {
            throw new ConflictException("marketplaceId already exists: " + req.marketplaceId());
        }
        e.setMarketplaceId(req.marketplaceId());
        e.setType(req.type());
        // merge：incoming 里 value = "***" 的敏感字段保留 existing 值
        String incoming = ResourceCommon.mapToJson(req.props());
        String merged = ResourceCommon.mergeKeepMasked(incoming, e.getPropsJson());
        e.setPropsJson(merged);
        e.setUpdatedAt(System.currentTimeMillis());
        SkillMarketplaceVO result = SkillMarketplaceVO.maskedFrom(e);
        marketplaceRegistry.invalidate(me);
        return result;
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("skill marketplace not found: " + id);
        }
        marketplaceRegistry.invalidate(me);
    }

    public List<MarketSkillSummaryVO> listSkills(Long marketplaceId) {
        String me = StpUtil.getLoginIdAsString();
        SkillMarketplaceEntity entity = loadMine(marketplaceId, me);
        BuilderMarketplace mp = marketplaceRegistry.find(me, entity.getMarketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not available: " + marketplaceId));
        return mp.list().stream().map(MarketSkillSummaryVO::from).toList();
    }

    public MarketSkillVO getSkill(Long marketplaceId, String skillName) {
        String me = StpUtil.getLoginIdAsString();
        SkillMarketplaceEntity entity = loadMine(marketplaceId, me);
        BuilderMarketplace mp = marketplaceRegistry.find(me, entity.getMarketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not available: " + marketplaceId));
        MarketSkillContent content = mp.fetch(skillName);
        if (content == null) throw new NotFoundException("skill not found: " + skillName);
        return MarketSkillVO.from(content);
    }

    private SkillMarketplaceEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("skill marketplace not found: " + id));
    }

    private void requireFields(SkillMarketplaceUpsertReq req) {
        if (req == null
                || req.marketplaceId() == null || req.marketplaceId().isBlank()
                || req.type() == null || req.type().isBlank()) {
            throw new IllegalArgumentException("marketplaceId and type required");
        }
    }
}
