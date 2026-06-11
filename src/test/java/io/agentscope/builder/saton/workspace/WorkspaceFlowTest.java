package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import io.agentscope.builder.saton.workspace.dto.WriteFileReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WorkspaceFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
        token = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody().token();
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("ws-stub-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange().expectStatus().isOk()
                .expectBody(ModelProviderVO.class).returnResult().getResponseBody();
        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "ws-agent-" + System.nanoTime(),
                        "ws", null, "hi", "react", mp.id(), 3, null))
                .exchange().expectStatus().isOk()
                .expectBody(AgentVO.class).returnResult().getResponseBody();
        agentId = ag.id();
    }

    @Test
    void writeReadListDeleteRoundtrip() {
        // write
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("hello world"))
                .exchange().expectStatus().isOk();

        // read
        String body = client.get().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertEquals("hello world", body);

        // list (top-level)
        List<FileNodeVO> nodes = client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(FileNodeVO.class).returnResult().getResponseBody();
        assertNotNull(nodes);
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("notes.md")));

        // delete
        client.delete().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk();

        // list again — empty
        List<FileNodeVO> after = client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(FileNodeVO.class).returnResult().getResponseBody();
        assertNotNull(after);
        assertTrue(after.stream().noneMatch(n -> n.name().equals("notes.md")));
    }

    @Test
    void summaryReturnsFileCount() {
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=a.txt")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("A")).exchange().expectStatus().isOk();
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=sub/b.txt")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("B")).exchange().expectStatus().isOk();

        WorkspaceSummaryVO sum = client.get().uri("/api/agents/" + agentId + "/workspace")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(WorkspaceSummaryVO.class).returnResult().getResponseBody();
        assertNotNull(sum);
        assertEquals(2, sum.fileCount());
    }

    @Test
    void readMissingFileReturns404() {
        client.get().uri("/api/agents/" + agentId + "/workspace/file?path=ghost.md")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void pathTraversalReturns400() {
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=../escape")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("x")).exchange().expectStatus().isBadRequest();
    }

    @Test
    void otherUsersAgentReturns404() {
        client.get().uri("/api/agents/999999/workspace/files")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void withoutTokenReturns401() {
        client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .exchange().expectStatus().isUnauthorized();
    }
}
