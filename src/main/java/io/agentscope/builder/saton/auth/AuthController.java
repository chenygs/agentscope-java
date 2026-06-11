package io.agentscope.builder.saton.auth;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return userService.login(req);
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    @GetMapping("/me")
    public Mono<MeResponse> me(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return userService.currentUser();
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }
}
