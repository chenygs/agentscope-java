package io.agentscope.builder.saton.agent.chat;

import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.agent.runtime.TestStubModel;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ChatFlowTest {

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
                .bodyValue(new ModelProviderUpsertReq("chat-stub-" + System.nanoTime(), "test-stub", Map.of()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        this.modelId = mp.id();

        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "chat-agent-" + System.nanoTime(),
                        "chat agent", "test", "you are helpful",
                        "react", modelId, 3, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        this.agentId = ag.id();
    }

    @Test
    void sendReturnsCannedReply() {
        ChatSendResp resp = client.post().uri("/api/agents/" + agentId + "/chat/send")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("hello", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSendResp.class)
                .returnResult().getResponseBody();
        assertNotNull(resp);
        assertEquals(TestStubModel.CANNED_REPLY, resp.reply());
        assertEquals(agentId, resp.agentDefId());
        assertEquals(modelId, resp.modelProviderIdUsed());
    }

    @Test
    void sendWithOverrideModelUsesOverride() {
        ModelProviderVO mp2 = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("chat-stub2-" + System.nanoTime(), "test-stub", Map.of()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();

        ChatSendResp resp = client.post().uri("/api/agents/" + agentId + "/chat/send")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("hi", mp2.id()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatSendResp.class)
                .returnResult().getResponseBody();
        assertEquals(mp2.id(), resp.modelProviderIdUsed());
    }

    @Test
    void sendWithoutTokenReturns401() {
        client.post().uri("/api/agents/" + agentId + "/chat/send")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("x", null))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void sendToOtherUsersAgentReturns404() {
        client.post().uri("/api/agents/999999/chat/send")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("x", null))
                .exchange().expectStatus().isNotFound();
    }
}
