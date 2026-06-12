package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.core.type.TypeReference;
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
        this.token = TestR.login(client);
    }

    @Test
    void listAllFiveModelTypes() {
        List<TypeMeta> types = TestR.data(
                client.get().uri("/api/factories/model-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TypeMeta>>>() {});

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai")),
                "missing some builtin types; got " + names);
    }

    @Test
    void everyTypeHasMetaAndSchema() {
        List<TypeMeta> types = TestR.data(
                client.get().uri("/api/factories/model-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TypeMeta>>>() {});
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
        List<TypeMeta> types = TestR.data(
                client.get().uri("/api/factories/tool-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TypeMeta>>>() {});

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("read-file", "write-file", "shell-cmd")),
                "missing builtin tool types; got " + names);
    }

    @Test
    void toolTypesRequiresLogin() {
        client.get().uri("/api/factories/tool-types")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void skillRepoTypesReturnsBuiltins() {
        List<TypeMeta> types = TestR.data(
                client.get().uri("/api/factories/skill-repo-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TypeMeta>>>() {});

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("local", "git")),
                "missing builtin skill repo types; got " + names);
    }

    @Test
    void middlewareTypesReturnsBuiltins() {
        List<TypeMeta> types = TestR.data(
                client.get().uri("/api/factories/middleware-types")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TypeMeta>>>() {});

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("logging", "audit-jsonl")),
                "missing builtin middleware types; got " + names);
    }
}
