package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
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

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
