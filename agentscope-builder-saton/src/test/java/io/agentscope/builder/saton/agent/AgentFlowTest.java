package io.agentscope.builder.saton.agent;

import io.agentscope.builder.saton.agent.dto.AgentShareUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentShareVO;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.agent.dto.CloneReq;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
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

        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        assertNotNull(login);
        this.token = login.token();

        // 先建一个 model provider 作为依赖（使用 unique 名字防止跨测试冲突）
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("agent-test-model-" + System.nanoTime(), "dashscope",
                        Map.of("apiKey", "sk-not-real", "modelName", "qwen-max")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(mp);
        this.modelId = mp.id();
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        String agentBizId = "my-agent-" + System.nanoTime();
        AgentVO created = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        agentBizId, "My Agent", "test", "you are helpful",
                        "react", modelId, 5, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals(agentBizId, created.agentId());
        assertEquals(modelId, created.defaultModelProviderId());
        assertEquals(5, created.maxIters());

        // list
        List<AgentVO> list = client.get().uri("/api/agents")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<AgentVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(a -> agentBizId.equals(a.agentId())));

        // get
        AgentVO got = client.get().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("you are helpful", got.sysPrompt());

        // update
        AgentVO updated = client.put().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        agentBizId, "Renamed", "x", "you are now strict",
                        "react", modelId, 8, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
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
                        "react", modelId, null, null))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(dupId, null, null, null,
                        "react", modelId, null, null))
                .exchange().expectStatus().is4xxClientError();   // 409
    }

    @Test
    void createWithNonExistentModelReturns404() {
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq("bad-agent-" + System.nanoTime(), null, null, null,
                        "react", 999999L, null, null))
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
        io.agentscope.builder.saton.agent.dto.AgentVO created = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new io.agentscope.builder.saton.agent.dto.AgentUpsertReq(
                        agentBizId, "T", null, "you are helpful",
                        "react", modelId, 3,
                        java.util.List.of(
                                new io.agentscope.builder.saton.agent.ToolSpec("read-file", java.util.Map.of()),
                                new io.agentscope.builder.saton.agent.ToolSpec("shell-cmd",
                                        java.util.Map.of("allowedCommands", java.util.List.of("ls", "cat"))))))
                .exchange()
                .expectStatus().isOk()
                .expectBody(io.agentscope.builder.saton.agent.dto.AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.toolSpecs());
        assertEquals(2, created.toolSpecs().size());
        assertEquals("read-file", created.toolSpecs().get(0).type());
        assertEquals("shell-cmd", created.toolSpecs().get(1).type());

        // GET should return the same shape
        io.agentscope.builder.saton.agent.dto.AgentVO got = client.get().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(io.agentscope.builder.saton.agent.dto.AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals(2, got.toolSpecs().size());
    }

    // -- Share tests --

    @Test
    void createShareSuccess() {
        AgentVO agent = createAgent("share-agent-" + System.nanoTime());

        AgentShareVO share = client.post().uri("/api/agents/" + agent.id() + "/shares")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentShareUpsertReq("user2", "RUN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentShareVO.class)
                .returnResult().getResponseBody();
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
        List<AgentShareVO> shares = client.get().uri("/api/agents/" + agent.id() + "/shares")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<AgentShareVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(shares);
        assertFalse(shares.isEmpty());
    }

    @Test
    void deleteShareRemovesShare() {
        AgentVO agent = createAgent("share-del-agent-" + System.nanoTime());

        AgentShareVO share = client.post().uri("/api/agents/" + agent.id() + "/shares")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentShareUpsertReq("user4", "RUN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentShareVO.class)
                .returnResult().getResponseBody();
        assertNotNull(share);

        // Delete the share
        client.delete().uri("/api/agents/" + agent.id() + "/shares/" + share.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        // List should be empty now
        List<AgentShareVO> shares = client.get().uri("/api/agents/" + agent.id() + "/shares")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<AgentShareVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(shares);
        assertTrue(shares.isEmpty());
    }

    // -- Clone tests --

    @Test
    void cloneCreatesNewAgent() {
        AgentVO source = createAgent("clone-source-" + System.nanoTime());

        String cloneAgentId = "clone-of-" + System.nanoTime();
        AgentVO cloned = client.post().uri("/api/agents/" + source.id() + "/clone")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CloneReq(cloneAgentId, "Cloned Agent"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
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
        return client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(agentId, "Test", null, "prompt",
                        "react", modelId, 10, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
    }
}
