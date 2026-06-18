package io.agentscope.builder.saton.workspace.controller;

import io.agentscope.builder.saton.agent.orm.entity.AgentType;
import io.agentscope.builder.saton.agent.orm.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderVO;
import io.agentscope.builder.saton.workspace.orm.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.orm.dto.WorkspaceSummaryVO;
import io.agentscope.builder.saton.workspace.orm.dto.WriteFileReq;
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
class WorkspaceFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
        token = TestR.login(client);

        ModelProviderVO mp = TestR.data(
                client.post().uri("/api/models")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ModelProviderUpsertReq("ws-stub-" + System.nanoTime(),
                                "test-stub", Map.of()))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});

        AgentVO ag = TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                "ws-agent-" + System.nanoTime(),
                                "ws", null, "hi", AgentType.REACT, mp.id(), 3, null))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        agentId = ag.id();
    }

    @Test
    void writeReadListDeleteRoundtrip() {
        // write
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("hello world"))
                .exchange().expectStatus().isOk();

        // read (raw text, not wrapped in R)
        String body = client.get().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertEquals("hello world", body);

        // list
        List<FileNodeVO> nodes = TestR.data(
                client.get().uri("/api/agents/" + agentId + "/workspace/files")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<List<FileNodeVO>>>() {});
        assertNotNull(nodes);
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("notes.md")));

        // delete
        client.delete().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk();

        // list again — empty
        List<FileNodeVO> after = TestR.data(
                client.get().uri("/api/agents/" + agentId + "/workspace/files")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<List<FileNodeVO>>>() {});
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

        WorkspaceSummaryVO sum = TestR.data(
                client.get().uri("/api/agents/" + agentId + "/workspace")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<WorkspaceSummaryVO>>() {});
        assertNotNull(sum);
        assertTrue(sum.fileCount() >= 2,
                "expected at least 2 files but got " + sum.fileCount() + " (workspace may contain files from other tests)");
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
