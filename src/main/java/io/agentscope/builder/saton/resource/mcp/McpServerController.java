package io.agentscope.builder.saton.resource.mcp;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerUpsertReq;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerService service;

    public McpServerController(McpServerService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<McpServerVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<McpServerVO> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<McpServerVO> create(@RequestBody McpServerUpsertReq req,
                                    ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<McpServerVO> update(@PathVariable("id") Long id,
                                    @RequestBody McpServerUpsertReq req,
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
