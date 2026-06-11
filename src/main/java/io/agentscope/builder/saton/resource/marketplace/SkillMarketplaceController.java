package io.agentscope.builder.saton.resource.marketplace;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceUpsertReq;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/skill-marketplaces")
public class SkillMarketplaceController {

    private final SkillMarketplaceService service;

    public SkillMarketplaceController(SkillMarketplaceService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<SkillMarketplaceVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<SkillMarketplaceVO> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<SkillMarketplaceVO> create(@RequestBody SkillMarketplaceUpsertReq req,
                                           ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<SkillMarketplaceVO> update(@PathVariable("id") Long id,
                                           @RequestBody SkillMarketplaceUpsertReq req,
                                           ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return null; });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
