package io.agentscope.builder.saton.resource.marketplace.controller;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.MarketSkillSummaryVO;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.MarketSkillVO;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.SkillMarketplaceUpsertReq;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.SkillMarketplaceVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import io.agentscope.builder.saton.resource.marketplace.service.SkillMarketplaceService;

@RestController
@RequestMapping("/api/skill-marketplaces")
public class SkillMarketplaceController {

    private final SkillMarketplaceService service;

    public SkillMarketplaceController(SkillMarketplaceService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<R<List<SkillMarketplaceVO>>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.okList(service.list()));
    }

    @GetMapping("/{id}")
    public Mono<R<SkillMarketplaceVO>> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.get(id)));
    }

    @PostMapping
    public Mono<R<SkillMarketplaceVO>> create(@RequestBody SkillMarketplaceUpsertReq req,
                                              ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    public Mono<R<SkillMarketplaceVO>> update(@PathVariable("id") Long id,
                                              @RequestBody SkillMarketplaceUpsertReq req,
                                              ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public Mono<R<Void>> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return R.ok(); });
    }

    @GetMapping("/{id}/skills")
    public Mono<R<List<MarketSkillSummaryVO>>> listSkills(
            @PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.okList(service.listSkills(id)));
    }

    @GetMapping("/{id}/skills/{name}")
    public Mono<R<MarketSkillVO>> getSkill(
            @PathVariable("id") Long id,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.getSkill(id, name)));
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
