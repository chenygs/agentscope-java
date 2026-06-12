package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.dto.AgentShareUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentShareVO;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.agent.dto.CloneReq;
import io.agentscope.builder.saton.common.error.NotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;
    private final AgentAccessGuard accessGuard;
    private final AgentShareRepository shareRepo;

    public AgentController(AgentService service,
                           AgentAccessGuard accessGuard,
                           AgentShareRepository shareRepo) {
        this.service = service;
        this.accessGuard = accessGuard;
        this.shareRepo = shareRepo;
    }

    @GetMapping
    public Mono<List<AgentVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<AgentVO> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<AgentVO> create(@RequestBody AgentUpsertReq req, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<AgentVO> update(@PathVariable("id") Long id,
                                @RequestBody AgentUpsertReq req,
                                ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return null; });
    }

    // -- Share CRUD --

    @GetMapping("/{id}/shares")
    public Mono<List<AgentShareVO>> listShares(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            return shareRepo.findByAgentDefId(id).stream().map(AgentShareVO::from).toList();
        });
    }

    @PostMapping("/{id}/shares")
    public Mono<AgentShareVO> createShare(@PathVariable("id") Long id,
                                           @RequestBody AgentShareUpsertReq req,
                                           ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            if (req.granteeId() == null || req.granteeId().isBlank()
                    || req.tier() == null || req.tier().isBlank()) {
                throw new IllegalArgumentException("granteeId and tier required");
            }
            return service.createShare(id, req.granteeId(), req.tier(), me);
        });
    }

    @DeleteMapping("/{id}/shares/{shareId}")
    public Mono<Void> deleteShare(@PathVariable("id") Long id,
                                   @PathVariable("shareId") Long shareId,
                                   ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            service.deleteShare(id, shareId);
            return null;
        });
    }

    // -- Clone --

    @PostMapping("/{id}/clone")
    public Mono<AgentVO> cloneAgent(@PathVariable("id") Long id,
                                     @RequestBody CloneReq req,
                                     ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(id, me, Tier.CLONE);
            return service.clone(id, req, me);
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
