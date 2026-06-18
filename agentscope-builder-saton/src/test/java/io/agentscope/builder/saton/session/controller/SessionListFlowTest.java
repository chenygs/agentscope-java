package io.agentscope.builder.saton.session.controller;

import io.agentscope.builder.saton.agent.orm.enums.AgentType;
import io.agentscope.builder.saton.agent.orm.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.orm.dto.ModelProviderVO;
import io.agentscope.builder.saton.session.orm.dto.ResetResp;
import io.agentscope.builder.saton.session.orm.dto.SessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessionListFlowTest {

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
                        .bodyValue(new ModelProviderUpsertReq("ses-list-" + System.nanoTime(),
                                "test-stub", Map.of()))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});

        AgentVO ag = TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                "session-list-agent-" + System.nanoTime(),
                                "list", null, "hi", AgentType.REACT, mp.id(), 3, null))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        agentId = ag.id();
    }

    private void chat(String sessionKey, String msg) {
        client.post().uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq(msg, null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
    }

    @Test
    void chatThenListShowsSession() {
        String key = "lk-" + System.nanoTime();
        chat(key, "hi");
        List<SessionVO> sessions = TestR.data(
                client.get().uri("/api/agents/" + agentId + "/sessions")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<List<SessionVO>>>() {});
        assertNotNull(sessions);
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals(key)),
                "expected sessionKey " + key + " in " +
                sessions.stream().map(SessionVO::sessionKey).toList());
    }

    @Test
    void resetReturnsTrueThenListNoLongerShows() {
        String key = "lk-" + System.nanoTime();
        chat(key, "hi");
        ResetResp r = TestR.data(
                client.post().uri("/api/agents/" + agentId + "/sessions/" + key + "/reset")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<ResetResp>>() {});
        assertNotNull(r);
        assertTrue(r.removed());
        List<SessionVO> sessions = TestR.data(
                client.get().uri("/api/agents/" + agentId + "/sessions")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<List<SessionVO>>>() {});
        assertNotNull(sessions);
        assertTrue(sessions.stream().noneMatch(s -> s.sessionKey().equals(key)));
    }

    @Test
    void resetNonexistentReturnsFalse() {
        ResetResp r = TestR.data(
                client.post()
                        .uri("/api/agents/" + agentId + "/sessions/never-existed/reset")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<ResetResp>>() {});
        assertNotNull(r);
        assertFalse(r.removed());
    }

    @Test
    void listOtherUsersAgentReturns404() {
        client.get().uri("/api/agents/999999/sessions")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/agents/" + agentId + "/sessions")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void deleteAgentPurgesSessions() {
        String key = "dk-" + System.nanoTime();
        chat(key, "hi");
        // delete agent
        client.delete().uri("/api/agents/" + agentId)
                .header("satoken", token).exchange().expectStatus().isOk();
        // GET on the deleted agent's sessions 404s
        client.get().uri("/api/agents/" + agentId + "/sessions")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }
}
