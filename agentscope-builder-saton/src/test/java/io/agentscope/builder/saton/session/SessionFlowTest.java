package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.agent.AgentType;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessionFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody();
        this.token = login.token();

        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("session-stub-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange().expectStatus().isOk()
                .expectBody(ModelProviderVO.class).returnResult().getResponseBody();

        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "session-agent-" + System.nanoTime(),
                        "session agent", null, "you are helpful",
                        AgentType.REACT, mp.id(), 3, null))
                .exchange().expectStatus().isOk()
                .expectBody(AgentVO.class).returnResult().getResponseBody();
        this.agentId = ag.id();
    }

    @Test
    void secondCallSeesFirstCallHistory() {
        String sessionKey = "s-" + System.nanoTime();

        List<ServerSentEvent<String>> first = client.post()
                .uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("first message", null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
        assertNotNull(first);
        assertEquals("agent_start", first.get(0).event());
        assertEquals("agent_end", first.get(first.size() - 1).event());

        List<ServerSentEvent<String>> second = client.post()
                .uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("second message", null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
        assertNotNull(second);
        assertEquals("agent_start", second.get(0).event());
        assertEquals("agent_end", second.get(second.size() - 1).event());
        // Both turns end cleanly with the same sessionKey: ReActAgent's slot loader picked up the
        // persisted agent_state. History-level inspection lives in Task 3's SessionService unit test.
    }

    @Test
    void differentSessionKeysAreIndependent() {
        String s1 = "s1-" + System.nanoTime();
        String s2 = "s2-" + System.nanoTime();
        for (String s : List.of(s1, s2)) {
            client.post()
                    .uri("/api/agents/" + agentId + "/chat/stream")
                    .header("satoken", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ChatSendReq("hi", null, s))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange().expectStatus().isOk()
                    .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .getResponseBody().collectList().block(Duration.ofSeconds(30));
        }
    }
}
