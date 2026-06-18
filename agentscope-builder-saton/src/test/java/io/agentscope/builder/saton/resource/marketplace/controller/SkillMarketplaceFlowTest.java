package io.agentscope.builder.saton.resource.marketplace.controller;

import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.SkillMarketplaceUpsertReq;
import io.agentscope.builder.saton.resource.marketplace.orm.dto.SkillMarketplaceVO;
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SkillMarketplaceFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
        this.token = TestR.login(client);
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        SkillMarketplaceVO created = TestR.data(
                client.post().uri("/api/skill-marketplaces")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new SkillMarketplaceUpsertReq("my-skills", "git",
                                Map.of("url", "git@github.com:me/x.git", "token", "tok-12345")))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<SkillMarketplaceVO>>() {});
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-skills", created.marketplaceId());
        assertEquals("***", created.props().get("token"), "token must be masked");
        assertEquals("git@github.com:me/x.git", created.props().get("url"));

        List<SkillMarketplaceVO> list = TestR.data(
                client.get().uri("/api/skill-marketplaces")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<SkillMarketplaceVO>>>() {});
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-skills".equals(v.marketplaceId())));

        SkillMarketplaceVO got = TestR.data(
                client.get().uri("/api/skill-marketplaces/" + created.id())
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<SkillMarketplaceVO>>() {});
        assertNotNull(got);
        assertEquals("***", got.props().get("token"));

        SkillMarketplaceVO updated = TestR.data(
                client.put().uri("/api/skill-marketplaces/" + created.id())
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new SkillMarketplaceUpsertReq("my-skills", "git",
                                Map.of("token", "***", "url", "git@github.com:me/x-v2.git")))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<SkillMarketplaceVO>>() {});
        assertNotNull(updated);
        assertEquals("git@github.com:me/x-v2.git", updated.props().get("url"));
        assertEquals("***", updated.props().get("token"));

        client.delete().uri("/api/skill-marketplaces/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/skill-marketplaces/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateNameReturns409() {
        client.post().uri("/api/skill-marketplaces").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillMarketplaceUpsertReq("dup", "git",
                        Map.of("token", "tok-1")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/skill-marketplaces").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillMarketplaceUpsertReq("dup", "nacos",
                        Map.of("token", "tok-2")))
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/skill-marketplaces")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void listSkillsReturnsOk() {
        SkillMarketplaceVO created = TestR.data(
                client.post().uri("/api/skill-marketplaces")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new SkillMarketplaceUpsertReq("browse-stub", "test-stub",
                                Map.of()))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<SkillMarketplaceVO>>() {});

        client.get().uri("/api/skill-marketplaces/" + created.id() + "/skills")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getSkillNonExistentReturns404() {
        SkillMarketplaceVO created = TestR.data(
                client.post().uri("/api/skill-marketplaces")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new SkillMarketplaceUpsertReq("fetch-stub", "test-stub",
                                Map.of()))
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<SkillMarketplaceVO>>() {});

        client.get().uri("/api/skill-marketplaces/" + created.id() + "/skills/missing-skill")
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }
}
