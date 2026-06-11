package io.agentscope.builder.saton.agent.chat;

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
class ChatStreamFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;
    Long modelId;
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
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        this.token = login.token();

        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("chat-stub-stream-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        this.modelId = mp.id();

        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "chat-stream-agent-" + System.nanoTime(),
                        "stream agent", "test", "you are helpful",
                        "react", modelId, 3, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        this.agentId = ag.id();
    }

    @Test
    void streamEmitsAgentStartAndAgentEnd() {
        List<ServerSentEvent<String>> events = client.post()
                .uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("hello", null))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(30));

        assertNotNull(events, "events should not be null");
        assertFalse(events.isEmpty(), "expected at least one event");

        // First event must be agent_start
        assertEquals("agent_start", events.get(0).event(),
                "first event should be agent_start; got events=" +
                events.stream().map(ServerSentEvent::event).toList());

        // Last event must be agent_end
        assertEquals("agent_end", events.get(events.size() - 1).event(),
                "last event should be agent_end; got events=" +
                events.stream().map(ServerSentEvent::event).toList());
    }

    @Test
    void streamWithoutTokenReturns401() {
        client.post().uri("/api/agents/" + agentId + "/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("x", null))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void streamToOtherUsersAgentReturns4xx() {
        // SSE error handling: error thrown synchronously in defer surfaces as HTTP error status.
        client.post().uri("/api/agents/999999/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("x", null))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().value(s ->
                        assertTrue(s >= 400 && s < 500, "expected 4xx; got " + s));
    }
}
