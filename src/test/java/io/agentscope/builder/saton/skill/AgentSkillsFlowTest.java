package io.agentscope.builder.saton.skill;

import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import io.agentscope.builder.saton.skill.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.dto.WorkspaceSkillVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentSkillsFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;
    Long modelId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Login
        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        assertNotNull(login);
        token = login.token();

        // Seed a stub model provider
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq(
                        "skill-flow-stub-" + System.nanoTime(), "dashscope",
                        Map.of("apiKey", "sk-not-real", "modelName", "qwen-max")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(mp);
        modelId = mp.id();

        // Seed an agent
        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "skill-flow-agent-" + System.nanoTime(), "test", null,
                        "you are helpful", "react", modelId, 3, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(ag);
        agentId = ag.id();
    }

    // Test 1: install from repo with no skill_repositories_json → 400 (IAE per §12.17)
    @Test
    void installFromRepoWithoutRepoSpecReturns4xx() {
        client.post().uri("/api/agents/" + agentId + "/skills/workspace/install")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InstallFromRepoReq(0, "test-skill", null, null))
                .exchange()
                .expectStatus().value(s -> assertTrue(s >= 400 && s < 500, "expected 4xx; got " + s));
    }

    // Test 2: install from marketplace with nonexistent marketplaceId → 404
    @Test
    void installFromMarketplaceWithoutMarketplaceReturns404() {
        client.post().uri("/api/agents/" + agentId + "/skills/workspace/marketplace-install")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MarketplaceInstallReq("nonexistent", "skill", null, null))
                .exchange()
                .expectStatus().isNotFound();
    }

    // Test 3: list workspace skills returns 200 + body (empty list)
    @Test
    void listWorkspaceSkillsReturnsOk() {
        List<WorkspaceSkillVO> skills = client.get()
                .uri("/api/agents/" + agentId + "/skills/workspace")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(WorkspaceSkillVO.class)
                .returnResult().getResponseBody();
        assertNotNull(skills);
    }

    // Test 4: delete nonexistent workspace skill → 4xx
    @Test
    void deleteWorkspaceSkillNonExistentReturns4xx() {
        client.delete().uri("/api/agents/" + agentId + "/skills/workspace/no-such-skill")
                .header("satoken", token)
                .exchange()
                .expectStatus().value(s -> assertTrue(s >= 400 && s < 500, "expected 4xx; got " + s));
    }

    // Test 5: install with empty body → 400
    @Test
    void installFromRepoWithInvalidReqReturns4xx() {
        client.post().uri("/api/agents/" + agentId + "/skills/workspace/install")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
