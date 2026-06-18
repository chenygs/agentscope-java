package io.agentscope.builder.saton.resource.mcp.controller;

import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.resource.mcp.orm.dto.McpServerUpsertReq;
import io.agentscope.builder.saton.resource.mcp.orm.dto.McpServerVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.core.type.TypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class McpServerFlowTest {

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
    void createListGetUpdateDeleteCycle() {
        McpServerVO created = TestR.data(
                client.post().uri("/api/mcp-servers")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new McpServerUpsertReq("my-playwright", "stdio",
                                Map.of("command", "npx playwright-mcp", "token", "tok-12345")))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<McpServerVO>>() {});
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-playwright", created.name());
        assertEquals("***", created.props().get("token"), "token must be masked");
        assertEquals("npx playwright-mcp", created.props().get("command"));

        List<McpServerVO> list = TestR.data(
                client.get().uri("/api/mcp-servers")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<McpServerVO>>>() {});
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-playwright".equals(v.name())));

        McpServerVO got = TestR.data(
                client.get().uri("/api/mcp-servers/" + created.id())
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<McpServerVO>>() {});
        assertNotNull(got);
        assertEquals("***", got.props().get("token"));

        McpServerVO updated = TestR.data(
                client.put().uri("/api/mcp-servers/" + created.id())
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new McpServerUpsertReq("my-playwright", "stdio",
                                Map.of("token", "***", "command", "npx playwright-mcp-v2")))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<McpServerVO>>() {});
        assertNotNull(updated);
        assertEquals("npx playwright-mcp-v2", updated.props().get("command"));
        assertEquals("***", updated.props().get("token"));

        client.delete().uri("/api/mcp-servers/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/mcp-servers/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateNameReturns409() {
        client.post().uri("/api/mcp-servers").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new McpServerUpsertReq("dup", "stdio",
                        Map.of("token", "tok-1")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/mcp-servers").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new McpServerUpsertReq("dup", "sse",
                        Map.of("token", "tok-2")))
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/mcp-servers")
                .exchange().expectStatus().isUnauthorized();
    }
}
