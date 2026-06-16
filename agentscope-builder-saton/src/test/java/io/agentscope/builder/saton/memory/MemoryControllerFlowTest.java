package io.agentscope.builder.saton.memory;

import io.agentscope.builder.saton.agent.AgentType;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.common.R;
import io.agentscope.builder.saton.common.TestR;
import io.agentscope.builder.saton.memory.dto.MemoryFileVO;
import io.agentscope.builder.saton.memory.dto.MemorySummaryVO;
import io.agentscope.builder.saton.memory.dto.WriteMemoryReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证 {@link MemoryController}:
 * <ul>
 *   <li>不存在的文件读返回空串(非 404)</li>
 *   <li>读写往返</li>
 *   <li>summary 元信息正确</li>
 *   <li>未知 kind 返回 400</li>
 *   <li>未登录返回 401</li>
 *   <li>跨 agent 共享 — 同一 user 的不同 agent 看到的是同一份记忆
 *       (commit 40caefcd 路径架构改造的核心动机)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MemoryControllerFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
        token = TestR.login(client);

        // 清理可能的残留(测试串行,只清不写;后续 case 自己写自己读)
        putMemory(MemoryKind.PERSONA.slug(), "");
        putMemory(MemoryKind.LONG_TERM.slug(), "");
    }

    @Test
    void readMissingReturnsEmptyString() {
        // 已被 setUp 写成空,语义上和"不存在"等价 — 关键是不抛 404
        String body = readMemory(MemoryKind.PERSONA.slug());
        assertEquals("", body);
    }

    @Test
    void writeReadRoundtrip() {
        putMemory(MemoryKind.PERSONA.slug(), "I prefer concise code.");
        assertEquals("I prefer concise code.", readMemory(MemoryKind.PERSONA.slug()));

        putMemory(MemoryKind.LONG_TERM.slug(), "User's project is on Windows.");
        assertEquals("User's project is on Windows.", readMemory(MemoryKind.LONG_TERM.slug()));
    }

    @Test
    void summaryReportsBothKinds() {
        putMemory(MemoryKind.PERSONA.slug(), "X");
        putMemory(MemoryKind.LONG_TERM.slug(), "");

        MemorySummaryVO sum = TestR.data(
                client.get().uri("/api/memory")
                        .header("satoken", token).exchange().expectStatus().isOk(),
                new TypeReference<R<MemorySummaryVO>>() {});

        assertNotNull(sum);
        assertNotNull(sum.root());
        assertEquals(MemoryKind.values().length, sum.files().size());

        MemoryFileVO persona = sum.files().stream()
                .filter(f -> f.kind().equals(MemoryKind.PERSONA.slug()))
                .findFirst().orElseThrow();
        assertEquals("AGENTS.md", persona.fileName());
        assertTrue(persona.exists());
        assertEquals(1L, persona.size());
        assertNotNull(persona.modifiedAt());

        MemoryFileVO longTerm = sum.files().stream()
                .filter(f -> f.kind().equals(MemoryKind.LONG_TERM.slug()))
                .findFirst().orElseThrow();
        assertEquals("MEMORY.md", longTerm.fileName());
        // 写过空串也算存在
        assertTrue(longTerm.exists());
        assertEquals(0L, longTerm.size());
    }

    @Test
    void unknownKindReturns400() {
        client.get().uri("/api/memory/bogus")
                .header("satoken", token).exchange().expectStatus().isBadRequest();
    }

    @Test
    void withoutTokenReturns401() {
        client.get().uri("/api/memory/persona")
                .exchange().expectStatus().isUnauthorized();
    }

    /**
     * 同一用户的两个不同 agent 应当共享 AGENTS.md / MEMORY.md。
     *
     * <p>记忆 API 本身是 user 级、不带 agentId,所以这个验收用"先建两个 agent
     * 确认它们都属于同一用户,再确认记忆 API 在两次创建之间内容稳定"的方式做。
     * 真正物理路径的跨 agent 共享性由 {@link WorkspacePathResolver#userRoot} 保证,
     * 这里通过 API 一致性侧面验证。
     */
    @Test
    void memoryIsSharedAcrossAgents() {
        Long agent1 = createAgent("mem-shared-a-" + System.nanoTime());
        putMemory(MemoryKind.LONG_TERM.slug(), "shared note from agent1's session");

        Long agent2 = createAgent("mem-shared-b-" + System.nanoTime());
        // 创建第二个 agent 后,第一个 agent 写入的记忆依然可见
        assertEquals("shared note from agent1's session",
                readMemory(MemoryKind.LONG_TERM.slug()));

        // 两个 agent 是同一 owner 名下的不同记录,但记忆只有一份
        assertNotEquals(agent1, agent2);
    }

    // ── helpers ──

    private String readMemory(String kind) {
        byte[] bytes = client.get().uri("/api/memory/" + kind)
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        return bytes == null ? "" : new String(bytes);
    }

    private void putMemory(String kind, String content) {
        client.put().uri("/api/memory/" + kind)
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteMemoryReq(content))
                .exchange().expectStatus().isOk();
    }

    private Long createAgent(String agentKey) {
        ModelProviderVO mp = TestR.data(
                client.post().uri("/api/models")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ModelProviderUpsertReq(
                                "mem-stub-" + System.nanoTime(), "test-stub", Map.of()))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<ModelProviderVO>>() {});

        AgentVO ag = TestR.data(
                client.post().uri("/api/agents")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AgentUpsertReq(
                                agentKey, "mem", null, "hi",
                                AgentType.REACT, mp.id(), 3, null))
                        .exchange().expectStatus().isOk(),
                new TypeReference<R<AgentVO>>() {});
        return ag.id();
    }
}
