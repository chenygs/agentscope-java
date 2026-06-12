package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.core.type.TypeReference;
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
        this.token = TestR.login(client);
    }

    @Test
    void listReturnsTemplates() {
        List<TemplateVO> templates = TestR.data(
                client.get().uri("/api/templates")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<List<TemplateVO>>>() {});
        assertNotNull(templates);
        assertTrue(templates.size() >= 3, "expected at least 3 templates, got " + templates.size());
    }

    @Test
    void getReturnsTemplate() {
        TemplateVO blank = TestR.data(
                client.get().uri("/api/templates/blank")
                        .header("satoken", token)
                        .exchange()
                        .expectStatus().isOk(),
                new TypeReference<R<TemplateVO>>() {});
        assertNotNull(blank);
        assertEquals("blank", blank.id());
        assertNotNull(blank.agent());
        assertEquals("react", blank.agent().get("agentType"));
    }
}
