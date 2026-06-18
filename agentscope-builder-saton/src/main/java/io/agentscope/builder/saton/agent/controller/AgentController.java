package io.agentscope.builder.saton.agent.controller;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.orm.dto.AgentShareUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentShareVO;
import io.agentscope.builder.saton.agent.orm.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.agent.orm.dto.CloneReq;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import io.agentscope.builder.saton.agent.orm.repository.AgentShareRepository;
import io.agentscope.builder.saton.agent.service.AgentService;
import io.agentscope.builder.saton.agent.service.AgentAccessGuard;
import io.agentscope.builder.saton.agent.orm.entity.Tier;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService service;
    private final AgentAccessGuard accessGuard;
    private final AgentShareRepository shareRepo;

    @GetMapping
    public Mono<R<List<AgentVO>>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.okList(service.list()));
    }

    @GetMapping("/{id}")
    public Mono<R<AgentVO>> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.get(id)));
    }

    @PostMapping
    public Mono<R<AgentVO>> create(@RequestBody AgentUpsertReq req, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    public Mono<R<AgentVO>> update(@PathVariable("id") Long id,
                                   @RequestBody AgentUpsertReq req,
                                   ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public Mono<R<Void>> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return R.ok(); });
    }

    // -- Share CRUD --

    @GetMapping("/{id}/shares")
    public Mono<R<List<AgentShareVO>>> listShares(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            return R.okList(shareRepo.findByAgentDefId(id).stream().map(AgentShareVO::from).toList());
        });
    }

    @PostMapping("/{id}/shares")
    public Mono<R<AgentShareVO>> createShare(@PathVariable("id") Long id,
                                             @RequestBody AgentShareUpsertReq req,
                                             ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            if (req.granteeId() == null || req.granteeId().isBlank()
                    || req.tier() == null || req.tier().isBlank()) {
                throw new IllegalArgumentException("granteeId and tier required");
            }
            return R.ok(service.createShare(id, req.granteeId(), req.tier(), me));
        });
    }

    @DeleteMapping("/{id}/shares/{shareId}")
    public Mono<R<Void>> deleteShare(@PathVariable("id") Long id,
                                     @PathVariable("shareId") Long shareId,
                                     ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            service.deleteShare(id, shareId);
            return R.ok();
        });
    }

    // -- Clone --

    @PostMapping("/{id}/clone")
    public Mono<R<AgentVO>> cloneAgent(@PathVariable("id") Long id,
                                       @RequestBody CloneReq req,
                                       ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.require(id, me, Tier.CLONE);
            return R.ok(service.clone(id, req, me));
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
