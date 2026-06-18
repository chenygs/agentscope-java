package io.agentscope.builder.saton.agent.controller;

import io.agentscope.builder.saton.agent.orm.dto.AgentShareUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentShareVO;
import io.agentscope.builder.saton.agent.orm.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.agent.orm.dto.CloneReq;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderVO;
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
import io.agentscope.builder.saton.agent.orm.enums.AgentType;
import io.agentscope.builder.saton.agent.orm.entity.ToolSpec;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AgentFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;
    Long modelId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
        this.token = TestR.login(client);

        // Seed a model provider as dependency
        ModelProviderVO mp = TestR.data(
                client.post().uri("/api/models")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ModelProviderUpsertReq("agent-test-model-" + System.nanoTime(), "dashscope",
                                Map.of("apiKey", "sk-not-real", "modelName", "qwen-max")))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});
        assertNotNull(mp);
        this.modelId = mp.id();
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        String agentBizId = "my-agent-" + System.nanoTime();
        AgentVO created = createAgent(agentBizId);
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals(agentBizId, created.agentId());
        assertEquals(modelId, created.defaultModelProviderId());
        assertEquals(5, created.maxIters());

        // list
        List<AgentVO> list = TestR.data(
                client.get().uri("/api/agents")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<AgentVO>>>() {});
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(a -> agentBizId.equals(a.agentId())));

        // get
        AgentVO got = TestR.data(
                client.get().uri("/api/agents/" + created.id())
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        assertNotNull(got);
        assertEquals("you are helpful", got.sysPrompt());

        // update
        AgentVO updated = TestR.data(
                client.put().uri("/api/agents/" + created.id())
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                agentBizId, "Renamed", "x", "you are now strict",
                                AgentType.REACT, modelId, 8, null))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        assertNotNull(updated);
        assertEquals("you are now strict", updated.sysPrompt());
        assertEquals(8, updated.maxIters());

        // delete
        client.delete().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        // get → 404
        client.get().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateAgentIdReturns409() {
        String dupId = "dup-agent-" + System.nanoTime();
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(dupId, null, null, null,
                        AgentType.REACT, modelId, null, null))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(dupId, null, null, null,
                        AgentType.REACT, modelId, null, null))
                .exchange().expectStatus().is4xxClientError();   // 409
    }

    @Test
    void createWithNonExistentModelReturns404() {
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq("bad-agent-" + System.nanoTime(), null, null, null,
                        AgentType.REACT, 999999L, null, null))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/agents")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void createWithToolSpecsRoundTrips() {
        String agentBizId = "tool-agent-" + System.nanoTime();
        AgentVO created = TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                agentBizId, "T", null, "you are helpful",
                                AgentType.REACT, modelId, 3,
                                List.of(
                                        new ToolSpec("read-file", Map.of()),
                                        new ToolSpec("shell-cmd",
                                                Map.of("allowedCommands", List.of("ls", "cat"))))))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        assertNotNull(created);
        assertNotNull(created.toolSpecs());
        assertEquals(2, created.toolSpecs().size());
        assertEquals("read-file", created.toolSpecs().get(0).type());
        assertEquals("shell-cmd", created.toolSpecs().get(1).type());

        // GET should return the same shape
        AgentVO got = TestR.data(
                client.get().uri("/api/agents/" + created.id())
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        assertNotNull(got);
        assertEquals(2, got.toolSpecs().size());
    }

    // -- Share tests --

    @Test
    void createShareSuccess() {
        AgentVO agent = createAgent("share-agent-" + System.nanoTime());

        AgentShareVO share = TestR.data(
                client.post().uri("/api/agents/" + agent.id() + "/shares")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentShareUpsertReq("user2", "RUN"))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentShareVO>>() {});
        assertNotNull(share);
        assertEquals("user2", share.granteeId());
        assertEquals("RUN", share.tier());
    }

    @Test
    void listSharesReturnsShares() {
        AgentVO agent = createAgent("share-list-agent-" + System.nanoTime());

        // Create a share first
        client.post().uri("/api/agents/" + agent.id() + "/shares")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentShareUpsertReq("user3", "CLONE"))
                .exchange()
                .expectStatus().isOk();

        // List shares
        List<AgentShareVO> shares = TestR.data(
                client.get().uri("/api/agents/" + agent.id() + "/shares")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<AgentShareVO>>>() {});
        assertNotNull(shares);
        assertFalse(shares.isEmpty());
    }

    @Test
    void deleteShareRemovesShare() {
        AgentVO agent = createAgent("share-del-agent-" + System.nanoTime());

        AgentShareVO share = TestR.data(
                client.post().uri("/api/agents/" + agent.id() + "/shares")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentShareUpsertReq("user4", "RUN"))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentShareVO>>() {});
        assertNotNull(share);

        // Delete the share
        client.delete().uri("/api/agents/" + agent.id() + "/shares/" + share.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        // List should be empty now
        List<AgentShareVO> shares = TestR.data(
                client.get().uri("/api/agents/" + agent.id() + "/shares")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<AgentShareVO>>>() {});
        assertNotNull(shares);
        assertTrue(shares.isEmpty());
    }

    // -- Clone tests --

    @Test
    void cloneCreatesNewAgent() {
        AgentVO source = createAgent("clone-source-" + System.nanoTime());

        String cloneAgentId = "clone-of-" + System.nanoTime();
        AgentVO cloned = TestR.data(
                client.post().uri("/api/agents/" + source.id() + "/clone")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new CloneReq(cloneAgentId, "Cloned Agent"))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        assertNotNull(cloned);
        assertNotEquals(source.id(), cloned.id());
        assertEquals(cloneAgentId, cloned.agentId());
        assertEquals("Cloned Agent", cloned.name());
        assertEquals(source.sysPrompt(), cloned.sysPrompt());
    }

    @Test
    void cloneWithDuplicateAgentIdReturns409() {
        AgentVO source = createAgent("clone-dup-source-" + System.nanoTime());

        // Try cloning with the same agentId as source
        client.post().uri("/api/agents/" + source.id() + "/clone")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CloneReq(source.agentId(), "Dup"))
                .exchange()
                .expectStatus().is4xxClientError();  // 409
    }

    // -- Helper --

    private AgentVO createAgent(String agentId) {
        return TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(agentId, "Test", null, "prompt",
                                AgentType.REACT, modelId, 10, null))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
    }
}
