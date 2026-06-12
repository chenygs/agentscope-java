package io.agentscope.builder.saton.resource.model;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelProviderController {

    private final ModelProviderService service;

    public ModelProviderController(ModelProviderService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<ModelProviderVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<ModelProviderVO> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<ModelProviderVO> create(@RequestBody ModelProviderUpsertReq req,
                                        ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<ModelProviderVO> update(@PathVariable("id") Long id,
                                        @RequestBody ModelProviderUpsertReq req,
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
