package io.agentscope.builder.saton.resource.model.controller;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import io.agentscope.builder.saton.resource.model.service.ModelProviderService;

@RestController
@RequestMapping("/api/models")
public class ModelProviderController {

    private final ModelProviderService service;

    public ModelProviderController(ModelProviderService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<R<List<ModelProviderVO>>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.okList(service.list()));
    }

    @GetMapping("/{id}")
    public Mono<R<ModelProviderVO>> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.get(id)));
    }

    @PostMapping
    public Mono<R<ModelProviderVO>> create(@RequestBody ModelProviderUpsertReq req,
                                           ServerWebExchange exchange) {
        return inSaContext(exchange, () -> R.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    public Mono<R<ModelProviderVO>> update(@PathVariable("id") Long id,
                                           @RequestBody ModelProviderUpsertReq req,
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
