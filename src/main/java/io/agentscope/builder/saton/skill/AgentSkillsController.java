package io.agentscope.builder.saton.skill;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.skill.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.dto.WorkspaceSkillVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{agentId}/skills")
public class AgentSkillsController {

    private final AgentSkillService skillService;
    private final AgentDefinitionRepository agentRepo;

    public AgentSkillsController(AgentSkillService skillService,
                                  AgentDefinitionRepository agentRepo) {
        this.skillService = skillService;
        this.agentRepo = agentRepo;
    }

    @GetMapping("/workspace")
    public Mono<List<WorkspaceSkillVO>> listWorkspaceSkills(
            @PathVariable("agentId") Long agentDefId, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.listWorkspaceSkills(me, agentDefId);
        });
    }

    @DeleteMapping("/workspace/{name}")
    public Mono<Void> deleteWorkspaceSkill(
            @PathVariable("agentId") Long agentDefId,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            skillService.deleteWorkspaceSkill(me, agentDefId, name);
            return null;
        }).then(Mono.empty());
    }

    @PostMapping("/workspace/install")
    public Mono<WorkspaceSkillVO> installFromRepository(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody InstallFromRepoReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.installFromRepository(me, agentDefId, req);
        });
    }

    @PostMapping("/workspace/marketplace-install")
    public Mono<WorkspaceSkillVO> installFromMarketplace(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody MarketplaceInstallReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.installFromMarketplace(me, agentDefId, req);
        });
    }

    private void assertOwns(Long agentDefId, String userId) {
        if (!agentRepo.existsByIdAndOwnerId(agentDefId, userId)) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                     java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
