package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TemplateFlowTest {

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
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody();
        assertNotNull(login);
        this.token = login.token();
    }

    @Test
    void listReturnsTemplates() {
        List<TemplateVO> templates = client.get().uri("/api/templates")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TemplateVO.class)
                .returnResult().getResponseBody();
        assertNotNull(templates);
        assertTrue(templates.size() >= 3, "expected at least 3 templates, got " + templates.size());
    }

    @Test
    void getReturnsTemplate() {
        TemplateVO blank = client.get().uri("/api/templates/blank")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TemplateVO.class)
                .returnResult().getResponseBody();
        assertNotNull(blank);
        assertEquals("blank", blank.id());
        assertNotNull(blank.agent());
        assertEquals("react", blank.agent().get("agentType"));
    }
}
