package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FactoriesControllerFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        assertNotNull(login);
        this.token = login.token();
    }

    @Test
    void listAllFiveModelTypes() {
        List<TypeMeta> types = client.get().uri("/api/factories/model-types")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TypeMeta>>() {})
                .returnResult().getResponseBody();

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai")),
                "missing some builtin types; got " + names);
    }

    @Test
    void everyTypeHasMetaAndSchema() {
        List<TypeMeta> types = client.get().uri("/api/factories/model-types")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TypeMeta>>() {})
                .returnResult().getResponseBody();
        assertNotNull(types);
        for (TypeMeta t : types) {
            assertNotNull(t.displayName(), "displayName for " + t.type());
            assertNotNull(t.description(), "description for " + t.type());
            assertNotNull(t.schema(), "schema for " + t.type());
            assertEquals("object", t.schema().get("type"));
            assertNotNull(t.schema().get("properties"));
            assertNotNull(t.schema().get("required"));
        }
    }

    @Test
    void modelTypesRequiresLogin() {
        client.get().uri("/api/factories/model-types")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void listAllToolTypes() {
        java.util.List<io.agentscope.builder.saton.factory.core.TypeMeta> types =
                client.get().uri("/api/factories/tool-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(new org.springframework.core.ParameterizedTypeReference<
                                java.util.List<io.agentscope.builder.saton.factory.core.TypeMeta>>() {})
                        .returnResult().getResponseBody();

        assertNotNull(types);
        java.util.Set<String> names = types.stream()
                .map(io.agentscope.builder.saton.factory.core.TypeMeta::type)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(names.containsAll(java.util.Set.of("read-file", "write-file", "shell-cmd")),
                "missing builtin tool types; got " + names);
    }

    @Test
    void toolTypesRequiresLogin() {
        client.get().uri("/api/factories/tool-types")
                .exchange().expectStatus().isUnauthorized();
    }
}
