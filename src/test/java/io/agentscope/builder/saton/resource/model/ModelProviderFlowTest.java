package io.agentscope.builder.saton.resource.model;

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
class ModelProviderFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

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
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        ModelProviderVO created = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("my-qwen", "dashscope",
                        Map.of("apiKey", "sk-12345", "modelName", "qwen-max")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-qwen", created.name());
        assertEquals("***", created.props().get("apiKey"), "apiKey must be masked");
        assertEquals("qwen-max", created.props().get("modelName"));

        List<ModelProviderVO> list = client.get().uri("/api/models")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ModelProviderVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-qwen".equals(v.name())));

        ModelProviderVO got = client.get().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("***", got.props().get("apiKey"));

        ModelProviderVO updated = client.put().uri("/api/models/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("my-qwen", "dashscope",
                        Map.of("apiKey", "***", "modelName", "qwen-plus")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(updated);
        assertEquals("qwen-plus", updated.props().get("modelName"));
        assertEquals("***", updated.props().get("apiKey"));

        client.delete().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateNameReturns409() {
        client.post().uri("/api/models").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("dup", "dashscope",
                        Map.of("apiKey", "sk-1")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/models").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("dup", "openai",
                        Map.of("apiKey", "sk-2")))
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/models")
                .exchange().expectStatus().isUnauthorized();
    }
}
