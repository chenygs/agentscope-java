package io.agentscope.builder.saton.auth.service;

import io.agentscope.builder.saton.auth.orm.dto.LoginRequest;
import io.agentscope.builder.saton.auth.service.ex.BadCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.auth.service.UserService;

/**
 * Unit-ish tests for {@link UserService}.
 *
 * <p>Only covers the failure paths that throw before touching {@code StpUtil.login()}.
 * The happy-path "login returns valid token" assertion is covered by
 * {@code AuthFlowTest} (Task 9) which runs under a real HTTP request context where
 * sa-token's reactor SaTokenContext is properly initialised.
 */
@SpringBootTest
class UserServiceTest {

    @Autowired UserService userService;

    @Test
    void loginWithWrongPasswordThrows() {
        assertThrows(BadCredentialsException.class,
                () -> userService.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void loginWithUnknownUserThrows() {
        assertThrows(BadCredentialsException.class,
                () -> userService.login(new LoginRequest("nobody", "x")));
    }
}
