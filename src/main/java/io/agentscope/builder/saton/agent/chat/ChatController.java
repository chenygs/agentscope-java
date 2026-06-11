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
}
