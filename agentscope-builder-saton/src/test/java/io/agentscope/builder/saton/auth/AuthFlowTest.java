package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.core.type.TypeReference;
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
        String token = TestR.login(client);

        MeResponse me = TestR.data(
                client.get()
                        .uri("/api/auth/me")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<MeResponse>>() {});

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
