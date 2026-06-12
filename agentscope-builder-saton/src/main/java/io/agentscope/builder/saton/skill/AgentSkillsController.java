package io.agentscope.builder.saton.skill;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentAccessGuard;
import io.agentscope.builder.saton.agent.Tier;
import io.agentscope.builder.saton.common.R;
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
    private final AgentAccessGuard accessGuard;

    public AgentSkillsController(AgentSkillService skillService,
                                  AgentAccessGuard accessGuard) {
        this.skillService = skillService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/workspace")
    public Mono<R<List<WorkspaceSkillVO>>> listWorkspaceSkills(
            @PathVariable("agentId") Long agentDefId, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.okList(skillService.listWorkspaceSkills(me, agentDefId));
        });
    }

    @DeleteMapping("/workspace/{name}")
    public Mono<R<Void>> deleteWorkspaceSkill(
            @PathVariable("agentId") Long agentDefId,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.EDIT);
            skillService.deleteWorkspaceSkill(me, agentDefId, name);
            return R.ok();
        });
    }

    @PostMapping("/workspace/install")
    public Mono<R<WorkspaceSkillVO>> installFromRepository(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody InstallFromRepoReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.ok(skillService.installFromRepository(me, agentDefId, req));
        });
    }

    @PostMapping("/workspace/marketplace-install")
    public Mono<R<WorkspaceSkillVO>> installFromMarketplace(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody MarketplaceInstallReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(agentDefId, me, Tier.RUN);
            return R.ok(skillService.installFromMarketplace(me, agentDefId, req));
        });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                     java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
