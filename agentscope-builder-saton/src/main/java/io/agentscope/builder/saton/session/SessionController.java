package io.agentscope.builder.saton.session;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.session.dto.ChatMessageVO;
import io.agentscope.builder.saton.session.dto.ResetResp;
import io.agentscope.builder.saton.session.dto.SessionVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class SessionController {

    private final SessionService sessions;
    private final AgentDefinitionRepository agentRepo;

    public SessionController(SessionService sessions, AgentDefinitionRepository agentRepo) {
        this.sessions = sessions;
        this.agentRepo = agentRepo;
    }

    @GetMapping("/{id}/sessions")
    public Mono<R<List<SessionVO>>> list(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                String me = StpUtil.getLoginIdAsString();
                requireOwn(id, me);
                return R.okList(sessions.list(me, id));
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    @PostMapping("/{id}/sessions/{key}/reset")
    public Mono<R<ResetResp>> reset(@PathVariable("id") Long id,
                                    @PathVariable("key") String key,
                                    ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                String me = StpUtil.getLoginIdAsString();
                requireOwn(id, me);
                return R.ok(new ResetResp(sessions.reset(me, id, key)));
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    @GetMapping("/{id}/sessions/{key}/messages")
    public Mono<R<List<ChatMessageVO>>> messages(@PathVariable("id") Long id,
                                                 @PathVariable("key") String key,
                                                 ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                String me = StpUtil.getLoginIdAsString();
                requireOwn(id, me);
                return R.okList(sessions.getMessages(me, id, key));
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    private void requireOwn(Long agentDefId, String me) {
        if (agentRepo.findByIdAndOwnerId(agentDefId, me).isEmpty()) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
    }
}
