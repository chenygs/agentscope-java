package io.agentscope.builder.saton.resource.repository;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.resource.repository.dto.SkillRepositoryUpsertReq;
import io.agentscope.builder.saton.resource.repository.dto.SkillRepositoryVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/skill-repositories")
public class SkillRepositoryController {

    private final SkillRepositoryService service;

    public SkillRepositoryController(SkillRepositoryService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<R<List<SkillRepositoryVO>>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.okList(service.list()));
    }

    @GetMapping("/{id}")
    public Mono<R<SkillRepositoryVO>> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.get(id)));
    }

    @PostMapping
    public Mono<R<SkillRepositoryVO>> create(@RequestBody SkillRepositoryUpsertReq req,
                                             ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    public Mono<R<SkillRepositoryVO>> update(@PathVariable("id") Long id,
                                             @RequestBody SkillRepositoryUpsertReq req,
                                             ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public Mono<R<Void>> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return R.ok(); });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
