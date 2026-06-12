package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

    @LocalServerPort int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void loginThenMe() {
        LoginResponse login = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(login);
        assertNotNull(login.token());
        assertEquals("admin", login.userId());

        MeResponse me = client.get()
                .uri("/api/auth/me")
                .header("satoken", login.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(me);
        assertEquals("admin", me.userId());
        assertEquals("admin", me.username());
    }

    @Test
    void meWithoutTokenReturns401() {
        client.get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
