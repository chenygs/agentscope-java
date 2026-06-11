package io.agentscope.builder.saton.resource.marketplace;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.marketplace.BuilderMarketplace;
import io.agentscope.builder.saton.marketplace.MarketSkillSummary;
import io.agentscope.builder.saton.marketplace.UserMarketplaceRegistry;
import io.agentscope.builder.saton.resource.marketplace.dto.MarketSkillSummaryVO;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceUpsertReq;
import io.agentscope.builder.saton.resource.marketplace.dto.SkillMarketplaceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SkillMarketplaceFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

    @MockitoBean
    UserMarketplaceRegistry marketplaceRegistry;

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
        SkillMarketplaceVO created = client.post().uri("/api/skill-marketplaces")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillMarketplaceUpsertReq("my-skills", "git",
                        Map.of("url", "git@github.com:me/x.git", "token", "tok-12345")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillMarketplaceVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-skills", created.marketplaceId());
        assertEquals("***", created.props().get("token"), "token must be masked");
        assertEquals("git@github.com:me/x.git", created.props().get("url"));

        List<SkillMarketplaceVO> list = client.get().uri("/api/skill-marketplaces")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<SkillMarketplaceVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-skills".equals(v.marketplaceId())));

        SkillMarketplaceVO got = client.get().uri("/api/skill-marketplaces/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillMarketplaceVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("***", got.props().get("token"));

        SkillMarketplaceVO updated = client.put().uri("/api/skill-marketplaces/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SkillMarketplaceUpsertReq("my-skills", "git",
                        Map.of("token", "***", "url", "git@github.com:me/x-v2.git")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillMarketplaceVO.class)
                .returnResult().getResponseBody();
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
        BuilderMarketplace mockMP = Mockito.mock(BuilderMarketplace.class);
        when(marketplaceRegistry.find(anyString(), anyString())).thenReturn(Optional.of(mockMP));
        when(mockMP.list()).thenReturn(List.of(new MarketSkillSummary("s1", "desc", "1.0")));

        SkillMarketplaceUpsertReq req = new SkillMarketplaceUpsertReq("browse-git", "git",
                Map.of("remoteUrl", "https://example.com/repo.git"));
        SkillMarketplaceVO created = client.post().uri("/api/skill-marketplaces")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillMarketplaceVO.class)
                .returnResult().getResponseBody();

        client.get().uri("/api/skill-marketplaces/" + created.id() + "/skills")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(MarketSkillSummaryVO.class);
    }

    @Test
    void getSkillNonExistentReturns404() {
        BuilderMarketplace mockMP = Mockito.mock(BuilderMarketplace.class);
        when(marketplaceRegistry.find(anyString(), anyString())).thenReturn(Optional.of(mockMP));
        when(mockMP.fetch(anyString())).thenReturn(null);

        SkillMarketplaceUpsertReq req = new SkillMarketplaceUpsertReq("fetch-git", "git",
                Map.of("remoteUrl", "https://example.com/repo.git"));
        SkillMarketplaceVO created = client.post().uri("/api/skill-marketplaces")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SkillMarketplaceVO.class)
                .returnResult().getResponseBody();

        client.get().uri("/api/skill-marketplaces/" + created.id() + "/skills/missing-skill")
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }
}
