package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> userService.login(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/me")
    public Mono<MeResponse> me() {
        return Mono.fromCallable(userService::currentUser)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
