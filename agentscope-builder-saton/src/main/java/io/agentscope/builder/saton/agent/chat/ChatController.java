package io.agentscope.builder.saton.agent.chat;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/agents")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/{id}/chat/send")
    public Mono<ChatSendResp> send(@PathVariable("id") Long id,
                                   @RequestBody ChatSendReq req,
                                   ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return service.send(id, req);
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        }).subscribeOn(Schedulers.boundedElastic());
        // ↑ subscribeOn(boundedElastic) is required because ChatService.send blocks on
        // ReActAgent.call(...).block(). Without it, Netty event-loop will throw at .block().
        // SaReactorSyncHolder.setContext(exchange) MUST be inside the lambda (executes on
        // the boundedElastic thread) so the ThreadLocal binding lives where StpUtil is called.
    }

    @PostMapping(value = "/{id}/chat/stream",
                 produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<String>>
            stream(@PathVariable("id") Long id,
                   @RequestBody ChatSendReq req,
                   ServerWebExchange exchange) {
        // subscribeOn(boundedElastic) is required because ReActAgent internally calls
        // Mono.block() in applySystemPromptMiddlewares() and MemoryMaintenanceMiddleware's
        // doOnComplete(). Running on the Netty event-loop would deadlock.
        // SaReactorSyncHolder.setContext(exchange) MUST be inside the lambda (executes on
        // the boundedElastic thread) so the ThreadLocal binding lives where StpUtil is called.
        return reactor.core.publisher.Flux.defer(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return service.stream(id, req);
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        }).subscribeOn(Schedulers.boundedElastic()).map(this::toSse);
    }

    private org.springframework.http.codec.ServerSentEvent<String> toSse(
            io.agentscope.core.event.AgentEvent event) {
        String dataJson;
        try {
            dataJson = io.agentscope.builder.saton.common.json.JsonUtil.mapper().writeValueAsString(event);
        } catch (Exception e) {
            dataJson = "{\"error\":\"serialize failed: " + e.getMessage() + "\"}";
        }
        // event name: lowercased type discriminator (e.g. "text_block_delta")
        String name = event.getType() != null ? event.getType().name().toLowerCase() : "event";
        return org.springframework.http.codec.ServerSentEvent.<String>builder()
                .event(name)
                .data(dataJson)
                .build();
    }
}
