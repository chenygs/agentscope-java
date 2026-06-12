package io.agentscope.builder.saton.resource.repository;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.repository.dto.SkillRepositoryUpsertReq;
import io.agentscope.builder.saton.resource.repository.dto.SkillRepositoryVO;
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
class SkillRepositoryFlowTest {

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
        SkillRepositoryVO created = client.post().uri("/api/skill-repositories")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillRepositoryUpsertReq("my-overlay", "git",
                        Map.of("url", "git@github.com:me/skills.git", "token", "tok-12345")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillRepositoryVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-overlay", created.name());
        assertEquals("***", created.props().get("token"), "token must be masked");
        assertEquals("git@github.com:me/skills.git", created.props().get("url"));

        List<SkillRepositoryVO> list = client.get().uri("/api/skill-repositories")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<SkillRepositoryVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-overlay".equals(v.name())));

        SkillRepositoryVO got = client.get().uri("/api/skill-repositories/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillRepositoryVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("***", got.props().get("token"));

        SkillRepositoryVO updated = client.put().uri("/api/skill-repositories/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillRepositoryUpsertReq("my-overlay", "git",
                        Map.of("token", "***", "url", "git@github.com:me/skills-v2.git")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillRepositoryVO.class)
                .returnResult().getResponseBody();
        assertNotNull(updated);
        assertEquals("git@github.com:me/skills-v2.git", updated.props().get("url"));
        assertEquals("***", updated.props().get("token"));

        client.delete().uri("/api/skill-repositories/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/skill-repositories/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateNameReturns409() {
        client.post().uri("/api/skill-repositories").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillRepositoryUpsertReq("dup", "git",
                        Map.of("token", "tok-1")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/skill-repositories").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillRepositoryUpsertReq("dup", "filesystem",
                        Map.of("token", "tok-2")))
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/skill-repositories")
                .exchange().expectStatus().isUnauthorized();
    }
}
