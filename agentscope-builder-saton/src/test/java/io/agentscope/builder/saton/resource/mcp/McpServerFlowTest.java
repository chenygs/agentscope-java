package io.agentscope.builder.saton.resource.mcp;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerUpsertReq;
import io.agentscope.builder.saton.resource.mcp.dto.McpServerVO;
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
    void createListGetUpdateDeleteCycle() {
        McpServerVO created = client.post().uri("/api/mcp-servers")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new McpServerUpsertReq("my-playwright", "stdio",
                        Map.of("command", "npx playwright-mcp", "token", "tok-12345")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpServerVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-playwright", created.name());
        assertEquals("***", created.props().get("token"), "token must be masked");
        assertEquals("npx playwright-mcp", created.props().get("command"));

        List<McpServerVO> list = client.get().uri("/api/mcp-servers")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<McpServerVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-playwright".equals(v.name())));

        McpServerVO got = client.get().uri("/api/mcp-servers/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpServerVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("***", got.props().get("token"));

        McpServerVO updated = client.put().uri("/api/mcp-servers/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new McpServerUpsertReq("my-playwright", "stdio",
                        Map.of("token", "***", "command", "npx playwright-mcp-v2")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpServerVO.class)
                .returnResult().getResponseBody();
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
