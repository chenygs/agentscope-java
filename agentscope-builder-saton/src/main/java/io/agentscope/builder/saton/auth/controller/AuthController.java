package io.agentscope.builder.saton.auth.controller;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.auth.orm.dto.LoginRequest;
import io.agentscope.builder.saton.auth.orm.dto.LoginResponse;
import io.agentscope.builder.saton.auth.orm.dto.MeResponse;
import io.agentscope.builder.saton.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.agentscope.builder.saton.auth.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Mono<R<LoginResponse>> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return R.ok(userService.login(req));
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    @GetMapping("/me")
    public Mono<R<MeResponse>> me(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return R.ok(userService.currentUser());
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }
}
