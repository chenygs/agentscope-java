package io.agentscope.builder.saton.agent.controller.chat;

import io.agentscope.builder.saton.agent.orm.enums.AgentType;
import io.agentscope.builder.saton.agent.orm.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.orm.dto.ChatSendResp;
import io.agentscope.builder.saton.agent.orm.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.orm.dto.AgentVO;
import io.agentscope.builder.saton.agent.service.runtime.TestStubModel;
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

        token = TestR.login(client);

        ModelProviderVO mp = TestR.data(
                client.post().uri("/api/models")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ModelProviderUpsertReq("chat-stub-" + System.nanoTime(), "test-stub", Map.of()))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});
        this.modelId = mp.id();

        AgentVO ag = TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                "chat-agent-" + System.nanoTime(),
                                "chat agent", "test", "you are helpful",
                                AgentType.REACT, modelId, 3, null))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        this.agentId = ag.id();
    }

    @Test
    void sendReturnsCannedReply() {
        ChatSendResp resp = TestR.data(
                client.post().uri("/api/agents/" + agentId + "/chat/send")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ChatSendReq("hello", null))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<ChatSendResp>>() {});
        assertNotNull(resp);
        assertEquals(TestStubModel.CANNED_REPLY, resp.reply());
        assertEquals(agentId, resp.agentDefId());
        assertEquals(modelId, resp.modelProviderIdUsed());
    }

    @Test
    void sendWithOverrideModelUsesOverride() {
        ModelProviderVO mp2 = TestR.data(
                client.post().uri("/api/models")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ModelProviderUpsertReq("chat-stub2-" + System.nanoTime(), "test-stub", Map.of()))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});

        ChatSendResp resp = TestR.data(
                client.post().uri("/api/agents/" + agentId + "/chat/send")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ChatSendReq("hi", mp2.id()))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<ChatSendResp>>() {});
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
