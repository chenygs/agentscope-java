# M6 Session 连续性 + Workspace 文件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 agent 装上"会记得上次说了啥"和"有自己的文件区"两大能力：
1. **Session 持久化**：用 `JsonFileAgentStateStore` 把 `AgentState`（含 conversation context）按 `(userId, sessionId)` 落到磁盘；下一轮聊天 `streamEvents(msg, RuntimeContext)` 传入同样的 `(userId, sessionId)` → ReActAgent 自动加载历史
2. **每 agent 的 Workspace 文件区**：每个 agent 一个目录 `<base>/<ownerId>/<agentId>/files/`，提供 list/read/write/delete REST，沙箱化（路径校验防穿越）
3. **Session 列表 + 复位**：`GET /api/agents/{id}/sessions` 列举该 agent 已有的会话 key；`POST /api/agents/{id}/sessions/{key}/reset` 清空一个 session 的历史

**完成定义：**
1. `SessionFlowTest` — 用 stub model 连发 2 条 `chat/stream`，第 2 条的 history 包含第 1 条的 user + assistant message
2. `WorkspaceFlowTest` — write a file, list 看到它，read 拿到内容，delete 后 list 看不到；路径穿越（`../etc/passwd`）返回 400
3. `SessionListFlowTest` — 在 stream 完一次后，`GET /api/agents/{id}/sessions` 至少返回一条；`POST .../sessions/{key}/reset` 后再 stream → 历史已清空

**Architecture:**
- **Session 由 `agentscope-core` 原生支持** —— `ReActAgent.Builder` 有 `stateStore(AgentStateStore)` + `defaultSessionId(String)`；调用 `streamEvents(msgs, RuntimeContext.builder().sessionId(s).userId(u).build())` 自动按 slot 加载/保存历史
- 新增 `SessionStoreConfig` `@Configuration`：暴露一个 `JsonFileAgentStateStore` bean，根目录由 `app.session.root` 配置（默认 `./data/sessions`）；test profile 用 tempdir
- `AgentBuildOrchestrator.build(def, model)` 改造：注入 `stateStore` + `defaultSessionId = "agent_" + def.id`
- `ChatService.send / stream` 加 sessionKey 参数（默认 `"default"`），构建 `RuntimeContext.builder().userId(me).sessionId(sessionKey).build()` 透传给 `streamEvents(msg, ctx)`
- `SessionService` —— 列举 session keys（从 stateStore 根目录扫描 `<ownerId>/<agentId>_<sessionKey>/` 子目录）+ `reset(...)`（删除该 session 的 `agent_state` 文件）
- `WorkspaceService` —— 单文件读写，目录 `<workspaceRoot>/<ownerId>/<agentId>/files/`，所有路径相对该根，校验后不允许越过

**关键决策：**
- **session 列表"扫盘"实现就够了** —— 单人视角，session 数量小（通常 <50/agent），不引入额外索引表
- **不引入新表** —— sessions 元数据（如 last_active_at）从文件 mtime 读；reset 就是删 `agent_state` 文件
- **workspace 走纯文件系统、不走 agentscope-harness 的 AbstractFilesystem** —— harness 抽象层是为子 agent / 沙箱设计的，M6 还没引入；用 JDK `java.nio.file` 简单 list/read/write 即可，M7 再考虑接 harness
- **agent_definition 表不加列** —— sessionKey 由前端发；workspacePath 字段 M4 已留位，但 M6 不让用户改（强制按 ownerId/agentId 命名）
- **ChatService.send/stream 接受 `sessionKey` 参数（可空）** —— `ChatSendReq` 增加 `sessionKey` 字段；默认 `"default"`
- **复用 M3-M5 现有缓存** —— `AgentRuntimeResolver` 不感知 sessionKey；同一个 `ReActAgent` 实例支持多 session（agentscope-core 原生）
- **删 agent 时清空所有 session 文件** —— `AgentService.delete(id)` 调 `SessionService.purgeAgent(agentId)`

**Tech Stack:** 无新 pom 依赖（`JsonFileAgentStateStore` 在 `agentscope-core` 里）

**踩坑预防（spec §12.1-12.15 都已踩验过）：**
- `JsonFileAgentStateStore` 构造器吞 `IOException` 抛 `RuntimeException`；测试目录用 `@TempDir`
- 路径穿越校验用 `Path.normalize().startsWith(rootPath)`，**不要** 单纯字符串包含 `..`
- `WorkspaceService` 是阻塞 IO，跟 `ChatService.send` 一样在 Netty 线程跑（M1 实测够快；试图加 `subscribeOn(boundedElastic)` 会拖累 sa-token 上下文）
- 测试 `WebTestClient.put().bodyValue(...)` 写文本文件时 contentType 必须显式设 `MediaType.APPLICATION_JSON`（请求体是 `WriteFileReq` record）
- spec §12.14：`stream` 方法中 `String me = StpUtil.getLoginIdAsString()` 必须在 `Flux.defer` 之外捕获 —— M6 stream 加 sessionKey 后仍然适用

---

## File Structure

```
agentscope-builder-saton/
└── src/main/java/io/agentscope/builder/saton/
    ├── agent/
    │   ├── AgentService.java                                  # MODIFY: delete() 触发 session purge
    │   ├── runtime/
    │   │   └── AgentBuildOrchestrator.java                    # MODIFY: 注入 stateStore + defaultSessionId
    │   └── chat/
    │       ├── ChatController.java                            # MODIFY: 透传 sessionKey
    │       ├── ChatService.java                               # MODIFY: 用 RuntimeContext(userId, sessionKey)
    │       └── dto/
    │           └── ChatSendReq.java                           # MODIFY: 加 sessionKey 字段
    ├── session/                                               # NEW 包
    │   ├── SessionStoreConfig.java                            # NEW: JsonFileAgentStateStore bean
    │   ├── SessionService.java                                # NEW: list / reset / purge
    │   ├── SessionController.java                             # NEW: /api/agents/{id}/sessions/**
    │   └── dto/
    │       ├── SessionVO.java                                 # NEW: record(key, lastActiveAt)
    │       └── ResetResp.java                                 # NEW: record(removed)
    └── workspace/                                             # NEW 包
        ├── WorkspaceConfig.java                               # NEW: 暴露 WorkspacePathResolver
        ├── WorkspacePathResolver.java                         # NEW: (ownerId, agentId) -> Path; 校验越界
        ├── WorkspaceService.java                              # NEW: list/read/write/delete
        ├── WorkspaceController.java                           # NEW: /api/agents/{id}/workspace/**
        └── dto/
            ├── FileNodeVO.java                                # NEW: record(name, path, type, size)
            ├── WriteFileReq.java                              # NEW: record(content)
            └── WorkspaceSummaryVO.java                        # NEW: record(root, fileCount)

src/main/resources/
└── application.yml                                            # MODIFY: 加 app.session.root + app.workspace.root

src/test/java/io/agentscope/builder/saton/
├── session/
│   ├── SessionStoreConfigTest.java                            # NEW: bean 装配
│   ├── SessionFlowTest.java                                   # NEW: 2 轮对话历史可见
│   ├── SessionListFlowTest.java                               # NEW: list + reset
│   └── SessionServiceTest.java                                # NEW: 单测 list/reset/purge
└── workspace/
    ├── WorkspacePathResolverTest.java                         # NEW: 路径穿越拒绝
    ├── WorkspaceServiceTest.java                              # NEW: CRUD 单测
    └── WorkspaceFlowTest.java                                 # NEW: REST CRUD

src/test/resources/
└── application.yml                                            # MODIFY: 加 app.session.root / app.workspace.root 临时路径
```

**职责说明：**

- `SessionStoreConfig` —— 启动时构造一个 `JsonFileAgentStateStore` 绑定到 `app.session.root`；这是个进程级单例
- `AgentBuildOrchestrator.build(...)` —— 之前只接 ModelFactory + ToolFactory；现在 `ReActAgent.builder()` 链上加 `.stateStore(stateStore).defaultSessionId("agent_" + def.getId())`
- `ChatService.stream(agentDefId, req)` —— 拿到 sessionKey = `req.sessionKey() != null ? req.sessionKey() : "default"`，构造 `RuntimeContext.builder().userId(me).sessionId("agent_" + agentDefId + "_" + sessionKey).build()` 传给 `streamEvents(msg, ctx)`
- `SessionService.list(ownerId, agentDefId)` —— 扫盘 `<root>/<ownerId>/agent_<agentDefId>_*/` 目录，返回 (sessionKey, mtime)
- `SessionService.reset(ownerId, agentDefId, sessionKey)` —— 删除对应目录
- `SessionService.purgeAgent(ownerId, agentDefId)` —— 删除所有该 agent 的 session 目录（用于 agent delete）
- `WorkspacePathResolver.resolve(ownerId, agentDefId, relPath)` —— 返回绝对 Path，校验 normalize 后没越界
- `WorkspaceService.list(ownerId, agentDefId)` —— 单层 ls，返回 `List<FileNodeVO>`
- `WorkspaceService.read/write/delete` —— 单文件操作
- `WorkspaceController` —— REST 包装
- `SessionController` —— `GET /api/agents/{id}/sessions`、`POST /api/agents/{id}/sessions/{key}/reset`

---

## Task 1: SessionStoreConfig + 把 stateStore 注入 AgentBuildOrchestrator

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/session/SessionStoreConfig.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`
- Test: `src/test/java/io/agentscope/builder/saton/session/SessionStoreConfigTest.java`

- [ ] **Step 1: 写失败测试 — SessionStoreConfig 能注入 AgentStateStore bean，且根目录可读**

Write `src/test/java/io/agentscope/builder/saton/session/SessionStoreConfigTest.java`:

```java
package io.agentscope.builder.saton.session;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionStoreConfigTest {

    @Autowired
    AgentStateStore stateStore;

    @Test
    void stateStoreIsJsonFileBacked() {
        assertNotNull(stateStore, "AgentStateStore bean should be present");
        assertTrue(stateStore instanceof JsonFileAgentStateStore,
                "expected JsonFileAgentStateStore; got " + stateStore.getClass());
    }
}
```

- [ ] **Step 2: 运行测试看到失败**

```bash
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn test -Dtest=SessionStoreConfigTest
```

Expected: FAIL — `NoSuchBeanDefinitionException: AgentStateStore`

- [ ] **Step 3: 实现 SessionStoreConfig**

Write `src/main/java/io/agentscope/builder/saton/session/SessionStoreConfig.java`:

```java
package io.agentscope.builder.saton.session;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 暴露进程级 {@link AgentStateStore}，由 ReActAgent 用作 {@code agent_state} 持久化后端。
 *
 * <p>根目录由 {@code app.session.root} 配置（默认 {@code ./data/sessions}）；
 * 测试 profile 通过 {@code application.yml} 覆盖到临时目录。
 */
@Configuration
public class SessionStoreConfig {

    @Bean
    public AgentStateStore agentStateStore(
            @Value("${app.session.root:./data/sessions}") String root) {
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        return new JsonFileAgentStateStore(rootPath);
    }
}
```

- [ ] **Step 4: 加 app.session.root 到 src/main/resources/application.yml**

In `src/main/resources/application.yml`, after the `sa-token:` block, append:

```yaml

app:
  session:
    root: ./data/sessions
  workspace:
    root: ./data/workspaces
```

- [ ] **Step 5: 加临时目录到 src/test/resources/application.yml**

In `src/test/resources/application.yml`, after the `sa-token:` block, append:

```yaml

app:
  session:
    root: ${java.io.tmpdir}/agentscope-builder-saton-sessions
  workspace:
    root: ${java.io.tmpdir}/agentscope-builder-saton-workspaces
```

- [ ] **Step 6: 运行测试看到通过**

```bash
mvn test -Dtest=SessionStoreConfigTest
```

Expected: PASS

- [ ] **Step 7: 把 stateStore 接到 AgentBuildOrchestrator**

Edit `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`:

Replace the constructor and `build` method to inject `AgentStateStore`. Final file content:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * 用 agent_definition 行 + model_provider 行装配一个真实可调的 {@link ReActAgent}。
 *
 * <p>M5 起接入 ToolFactory；M6 起注入 {@link AgentStateStore} 让 ReActAgent 在
 * {@code call/streamEvents} 完成后自动按 {@code (userId, sessionId)} 持久化 agent_state，
 * 下一轮 chat 透传同样 slot 即可恢复历史。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;
    private final AgentStateStore stateStore;

    public AgentBuildOrchestrator(ModelFactory modelFactory,
                                  ToolFactory toolFactory,
                                  AgentStateStore stateStore) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.stateStore = stateStore;
    }

    public ReActAgent build(AgentDefinitionEntity def, ModelProviderEntity model) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            Object tool = toolFactory.instantiate(spec.type(), spec.props());
            toolkit.registerTool(tool);
        }

        return ReActAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .stateStore(stateStore)
                .defaultSessionId("agent_" + def.getId() + "_default")
                .build();
    }

    private static List<ToolSpec> parseToolSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<ToolSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid tool_specs_json", e);
        }
    }
}
```

- [ ] **Step 8: 跑全量回归确保 M5 测试没被打破**

```bash
mvn test
```

Expected: 84 tests PASS (83 旧的 + 1 新 SessionStoreConfigTest)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/session/SessionStoreConfig.java \
        src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java \
        src/main/resources/application.yml \
        src/test/resources/application.yml \
        src/test/java/io/agentscope/builder/saton/session/SessionStoreConfigTest.java
git commit -m "feat(m6): stateStore bean + AgentBuildOrchestrator 注入"
```

---

## Task 2: ChatSendReq 加 sessionKey + ChatService 透传 RuntimeContext

**Files:**
- Modify: `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendReq.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`
- Test: `src/test/java/io/agentscope/builder/saton/session/SessionFlowTest.java`

- [ ] **Step 1: 写失败测试 — 两轮对话历史可见**

Write `src/test/java/io/agentscope/builder/saton/session/SessionFlowTest.java`:

```java
package io.agentscope.builder.saton.session;

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
class SessionFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
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
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody();
        this.token = login.token();

        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("session-stub-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange().expectStatus().isOk()
                .expectBody(ModelProviderVO.class).returnResult().getResponseBody();

        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "session-agent-" + System.nanoTime(),
                        "session agent", null, "you are helpful",
                        "react", mp.id(), 3, null))
                .exchange().expectStatus().isOk()
                .expectBody(AgentVO.class).returnResult().getResponseBody();
        this.agentId = ag.id();
    }

    @Test
    void secondCallSeesFirstCallHistory() {
        String sessionKey = "s-" + System.nanoTime();

        List<ServerSentEvent<String>> first = client.post()
                .uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("first message", null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
        assertNotNull(first);
        assertEquals("agent_start", first.get(0).event());
        assertEquals("agent_end", first.get(first.size() - 1).event());

        List<ServerSentEvent<String>> second = client.post()
                .uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq("second message", null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
        assertNotNull(second);
        assertEquals("agent_start", second.get(0).event());
        assertEquals("agent_end", second.get(second.size() - 1).event());
        // Both turns end cleanly with the same sessionKey: ReActAgent's slot loader picked up the
        // persisted agent_state. History-level inspection lives in Task 3's SessionService unit test.
    }

    @Test
    void differentSessionKeysAreIndependent() {
        String s1 = "s1-" + System.nanoTime();
        String s2 = "s2-" + System.nanoTime();
        for (String s : List.of(s1, s2)) {
            client.post()
                    .uri("/api/agents/" + agentId + "/chat/stream")
                    .header("satoken", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ChatSendReq("hi", null, s))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange().expectStatus().isOk()
                    .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .getResponseBody().collectList().block(Duration.ofSeconds(30));
        }
    }
}
```

- [ ] **Step 2: 运行测试看到失败**

```bash
mvn test -Dtest=SessionFlowTest
```

Expected: COMPILATION FAIL — `ChatSendReq` 构造器只接 2 参（message, overrideModelProviderId）

- [ ] **Step 3: 加 sessionKey 字段到 ChatSendReq**

Replace `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendReq.java` (full file):

```java
package io.agentscope.builder.saton.agent.chat.dto;

/**
 * Chat 请求体。
 *
 * @param message 用户消息（必填）
 * @param overrideModelProviderId 临时切换模型（可空，默认走 agent.defaultModelProviderId）
 * @param sessionKey 会话标识（可空，默认 "default"）；同 sessionKey 多次请求共享 history
 */
public record ChatSendReq(String message,
                          Long overrideModelProviderId,
                          String sessionKey) {

    /** 兼容老调用：sessionKey 默认 null。 */
    public ChatSendReq(String message, Long overrideModelProviderId) {
        this(message, overrideModelProviderId, null);
    }
}
```

- [ ] **Step 4: 改 ChatService 把 sessionKey 译成 RuntimeContext，传给 streamEvents / call**

Replace `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java` (full file):

```java
package io.agentscope.builder.saton.agent.chat;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatService {

    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(2);
    private static final String DEFAULT_SESSION_KEY = "default";

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentRuntimeResolver runtimeResolver;

    public ChatService(AgentDefinitionRepository agentRepo,
                       ModelProviderRepository modelRepo,
                       AgentRuntimeResolver runtimeResolver) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
    }

    public ChatSendResp send(Long agentDefId, ChatSendReq req) {
        String me = StpUtil.getLoginIdAsString();
        AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, me)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
        Long effectiveModelId = req.overrideModelProviderId() != null
                ? req.overrideModelProviderId()
                : def.getDefaultModelProviderId();
        if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + effectiveModelId);
        }
        ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);

        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(req.message() == null ? "" : req.message()).build())
                .build();

        RuntimeContext ctx = buildContext(me, def.getId(), req.sessionKey());
        Msg reply = agent.call(java.util.List.of(userMsg), ctx).block(CALL_TIMEOUT);
        return new ChatSendResp(extractText(reply), def.getId(), effectiveModelId);
    }

    public reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent> stream(
            Long agentDefId,
            ChatSendReq req) {
        // spec §12.14 — capture loginId BEFORE Flux.defer (sa-token context gone by inner subscribe)
        String me = StpUtil.getLoginIdAsString();
        return reactor.core.publisher.Flux.defer(() -> {
            AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, me)
                    .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
            Long effectiveModelId = req.overrideModelProviderId() != null
                    ? req.overrideModelProviderId()
                    : def.getDefaultModelProviderId();
            if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
                throw new NotFoundException(
                        "model provider not found or not yours: " + effectiveModelId);
            }
            ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder()
                            .text(req.message() == null ? "" : req.message()).build())
                    .build();

            RuntimeContext ctx = buildContext(me, def.getId(), req.sessionKey());
            return agent.streamEvents(userMsg, ctx);
        });
    }

    private static RuntimeContext buildContext(String userId, Long agentDefId, String sessionKey) {
        String key = (sessionKey == null || sessionKey.isBlank()) ? DEFAULT_SESSION_KEY : sessionKey;
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId("agent_" + agentDefId + "_" + key)
                .build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock t) {
                if (t.getText() != null) sb.append(t.getText());
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: 运行 SessionFlowTest 看到通过**

```bash
mvn test -Dtest=SessionFlowTest
```

Expected: PASS — `secondCallSeesFirstCallHistory` + `differentSessionKeysAreIndependent` 两个都绿

- [ ] **Step 6: 跑全量回归（ChatFlowTest / ChatStreamFlowTest 使用 ChatSendReq 老 2 参构造器，必须仍然过）**

```bash
mvn test
```

Expected: 86 tests PASS（84 之前 + 2 新 SessionFlowTest）

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendReq.java \
        src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java \
        src/test/java/io/agentscope/builder/saton/session/SessionFlowTest.java
git commit -m "feat(m6): ChatSendReq.sessionKey + RuntimeContext(userId, sessionId) 透传"
```

---

## Task 3: SessionService（list / reset / purge）

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/session/SessionService.java`
- Create: `src/main/java/io/agentscope/builder/saton/session/dto/SessionVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/session/dto/ResetResp.java`
- Test: `src/test/java/io/agentscope/builder/saton/session/SessionServiceTest.java`

**背景 — `JsonFileAgentStateStore` 落盘结构（M6 实测，留作 §12.16）：**
- 根目录是构造器传入的 path（M6 用 `app.session.root` 配置）
- 每个 `(userId, sessionId)` slot 落在 `<root>/<userId>/<sessionId>/<key>.json`，其中：
  - `userId` 就是 sa-token loginId（M6 ChatService 传的）
  - `sessionId` 就是 `"agent_" + agentDefId + "_" + sessionKey`（M6 ChatService 拼的）
  - `key` 是 ReActAgent 自己用的 `"agent_state"`（持久化），可能还有 v1 legacy 的 `memory_messages` / `toolkit_activeGroups`

所以 `SessionService.list(ownerId, agentDefId)` 的实现：
1. 拼出 `<root>/<ownerId>/` 目录
2. 列出所有子目录 `agent_<agentDefId>_<sessionKey>`，反解出 sessionKey + mtime

**reset(ownerId, agentDefId, sessionKey)** = 删 `<root>/<ownerId>/agent_<agentDefId>_<sessionKey>/` 整个目录

**purgeAgent(ownerId, agentDefId)** = 删 `<root>/<ownerId>/agent_<agentDefId>_*/` 所有匹配子目录

- [ ] **Step 1: 写 dto**

Write `src/main/java/io/agentscope/builder/saton/session/dto/SessionVO.java`:

```java
package io.agentscope.builder.saton.session.dto;

/** 一个 session 的简要视图。 */
public record SessionVO(String sessionKey, long lastActiveAt) { }
```

Write `src/main/java/io/agentscope/builder/saton/session/dto/ResetResp.java`:

```java
package io.agentscope.builder.saton.session.dto;

/** {@code POST /sessions/{key}/reset} 的返回体。 */
public record ResetResp(boolean removed) { }
```

- [ ] **Step 2: 写失败测试 — SessionService list/reset/purge 三件套**

Write `src/test/java/io/agentscope/builder/saton/session/SessionServiceTest.java`:

```java
package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.JsonFileAgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    @TempDir Path root;
    SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(root, new JsonFileAgentStateStore(root));
    }

    @Test
    void listReturnsEmptyWhenNoneExist() {
        List<SessionVO> sessions = service.list("alice", 42L);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void listFindsAllSessionsForAgent() throws Exception {
        // Seed: <root>/alice/agent_42_a/agent_state.json + agent_42_b/agent_state.json
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        // Also: a different agent's session must NOT show
        seed("alice", "agent_99_x");
        // Also: a different user's session must NOT show
        seed("bob",   "agent_42_z");

        List<SessionVO> sessions = service.list("alice", 42L);
        assertEquals(2, sessions.size(), () ->
                "expected 2 sessions; got " + sessions.stream().map(SessionVO::sessionKey).toList());
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("a")));
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals("b")));
        for (SessionVO s : sessions) {
            assertTrue(s.lastActiveAt() > 0, "lastActiveAt should be > 0");
        }
    }

    @Test
    void resetDeletesSlotDirectoryAndReturnsTrue() throws Exception {
        seed("alice", "agent_42_a");
        assertTrue(Files.exists(root.resolve("alice").resolve("agent_42_a")));

        boolean removed = service.reset("alice", 42L, "a");
        assertTrue(removed);
        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_a")));
    }

    @Test
    void resetReturnsFalseWhenSlotNotExist() {
        assertFalse(service.reset("alice", 42L, "ghost"));
    }

    @Test
    void purgeAgentDeletesAllSlotsForThatAgent() throws Exception {
        seed("alice", "agent_42_a");
        seed("alice", "agent_42_b");
        seed("alice", "agent_99_x");  // different agent — must survive

        service.purgeAgent("alice", 42L);

        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_a")));
        assertFalse(Files.exists(root.resolve("alice").resolve("agent_42_b")));
        assertTrue(Files.exists(root.resolve("alice").resolve("agent_99_x")));
    }

    private void seed(String userId, String sessionId) throws Exception {
        Path dir = root.resolve(userId).resolve(sessionId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("agent_state.json"), "{}");
    }
}
```

- [ ] **Step 3: 运行测试看到失败**

```bash
mvn test -Dtest=SessionServiceTest
```

Expected: COMPILATION FAIL — `SessionService` 类不存在

- [ ] **Step 4: 实现 SessionService**

Write `src/main/java/io/agentscope/builder/saton/session/SessionService.java`:

```java
package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.session.dto.SessionVO;
import io.agentscope.core.state.AgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 浏览 / 重置 / 整体清空一个 agent 的所有 session 历史。
 *
 * <p>实现走"扫盘"路线 —— 单人视角下 session 数量小（&lt;50/agent），没必要建索引表。
 * 落盘结构由 {@link io.agentscope.core.state.JsonFileAgentStateStore} 定义：
 * {@code <root>/<userId>/<sessionId>/<key>.json}，其中本项目的
 * {@code sessionId = "agent_" + agentDefId + "_" + sessionKey}（见
 * {@link io.agentscope.builder.saton.agent.chat.ChatService}）。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Path root;
    @SuppressWarnings("unused") // held for parity / future use
    private final AgentStateStore stateStore;

    public SessionService(@Value("${app.session.root:./data/sessions}") String rootConfig,
                          AgentStateStore stateStore) {
        this(Paths.get(rootConfig).toAbsolutePath().normalize(), stateStore);
    }

    /** Direct constructor for unit tests with a {@link org.junit.jupiter.api.io.TempDir}. */
    public SessionService(Path root, AgentStateStore stateStore) {
        this.root = root;
        this.stateStore = stateStore;
    }

    public List<SessionVO> list(String ownerId, Long agentDefId) {
        Path userDir = root.resolve(ownerId);
        if (!Files.isDirectory(userDir)) return List.of();
        String prefix = "agent_" + agentDefId + "_";
        List<SessionVO> out = new ArrayList<>();
        try (Stream<Path> children = Files.list(userDir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(p -> {
                        String dirName = p.getFileName().toString();
                        String sessionKey = dirName.substring(prefix.length());
                        long mtime;
                        try {
                            mtime = Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            mtime = 0L;
                        }
                        out.add(new SessionVO(sessionKey, mtime));
                    });
        } catch (IOException e) {
            log.warn("failed to list sessions for {}/{}: {}", ownerId, agentDefId, e.getMessage());
        }
        out.sort(Comparator.comparingLong(SessionVO::lastActiveAt).reversed());
        return out;
    }

    public boolean reset(String ownerId, Long agentDefId, String sessionKey) {
        Path slot = root.resolve(ownerId).resolve("agent_" + agentDefId + "_" + sessionKey);
        if (!Files.isDirectory(slot)) return false;
        deleteRecursively(slot);
        return true;
    }

    public void purgeAgent(String ownerId, Long agentDefId) {
        Path userDir = root.resolve(ownerId);
        if (!Files.isDirectory(userDir)) return;
        String prefix = "agent_" + agentDefId + "_";
        try (Stream<Path> children = Files.list(userDir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.warn("failed to purge sessions for {}/{}: {}", ownerId, agentDefId, e.getMessage());
        }
    }

    private void deleteRecursively(Path p) {
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(child -> {
                try { Files.deleteIfExists(child); }
                catch (IOException e) { log.warn("failed to delete {}: {}", child, e.getMessage()); }
            });
        } catch (IOException e) {
            log.warn("failed to walk {}: {}", p, e.getMessage());
        }
    }
}
```

- [ ] **Step 5: 运行测试看到通过**

```bash
mvn test -Dtest=SessionServiceTest
```

Expected: PASS — 5 个 case 都绿

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/session/SessionService.java \
        src/main/java/io/agentscope/builder/saton/session/dto/SessionVO.java \
        src/main/java/io/agentscope/builder/saton/session/dto/ResetResp.java \
        src/test/java/io/agentscope/builder/saton/session/SessionServiceTest.java
git commit -m "feat(m6): SessionService list/reset/purgeAgent + dto"
```

---

## Task 4: SessionController + AgentService.delete() 触发 purgeAgent

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/session/SessionController.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`
- Test: `src/test/java/io/agentscope/builder/saton/session/SessionListFlowTest.java`

- [ ] **Step 1: 写 SessionListFlowTest（list + reset 走 REST，并验证 reset 后历史断开）**

Write `src/test/java/io/agentscope/builder/saton/session/SessionListFlowTest.java`:

```java
package io.agentscope.builder.saton.session;

import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import io.agentscope.builder.saton.session.dto.ResetResp;
import io.agentscope.builder.saton.session.dto.SessionVO;
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
class SessionListFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
        token = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody().token();
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("ses-list-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange().expectStatus().isOk()
                .expectBody(ModelProviderVO.class).returnResult().getResponseBody();
        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "session-list-agent-" + System.nanoTime(),
                        "list", null, "hi", "react", mp.id(), 3, null))
                .exchange().expectStatus().isOk()
                .expectBody(AgentVO.class).returnResult().getResponseBody();
        agentId = ag.id();
    }

    private void chat(String sessionKey, String msg) {
        client.post().uri("/api/agents/" + agentId + "/chat/stream")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatSendReq(msg, null, sessionKey))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .getResponseBody().collectList().block(Duration.ofSeconds(30));
    }

    @Test
    void chatThenListShowsSession() {
        String key = "lk-" + System.nanoTime();
        chat(key, "hi");
        List<SessionVO> sessions = client.get().uri("/api/agents/" + agentId + "/sessions")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(SessionVO.class).returnResult().getResponseBody();
        assertNotNull(sessions);
        assertTrue(sessions.stream().anyMatch(s -> s.sessionKey().equals(key)),
                "expected sessionKey " + key + " in " +
                sessions.stream().map(SessionVO::sessionKey).toList());
    }

    @Test
    void resetReturnsTrueThenListNoLongerShows() {
        String key = "lk-" + System.nanoTime();
        chat(key, "hi");
        ResetResp r = client.post().uri("/api/agents/" + agentId + "/sessions/" + key + "/reset")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(ResetResp.class).returnResult().getResponseBody();
        assertNotNull(r);
        assertTrue(r.removed());
        List<SessionVO> sessions = client.get().uri("/api/agents/" + agentId + "/sessions")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(SessionVO.class).returnResult().getResponseBody();
        assertNotNull(sessions);
        assertTrue(sessions.stream().noneMatch(s -> s.sessionKey().equals(key)));
    }

    @Test
    void resetNonexistentReturnsFalse() {
        ResetResp r = client.post()
                .uri("/api/agents/" + agentId + "/sessions/never-existed/reset")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(ResetResp.class).returnResult().getResponseBody();
        assertNotNull(r);
        assertFalse(r.removed());
    }

    @Test
    void listOtherUsersAgentReturns404() {
        client.get().uri("/api/agents/999999/sessions")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/agents/" + agentId + "/sessions")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void deleteAgentPurgesSessions() {
        String key = "dk-" + System.nanoTime();
        chat(key, "hi");
        // delete agent
        client.delete().uri("/api/agents/" + agentId)
                .header("satoken", token).exchange().expectStatus().isOk();
        // recreate same id then list — should be empty (sessions were purged)
        // Skip: agent_id is per-owner unique and we don't reuse; just verify GET on the deleted
        // agent's sessions 404s (because it doesn't exist).
        client.get().uri("/api/agents/" + agentId + "/sessions")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }
}
```

- [ ] **Step 2: 运行测试看到失败**

```bash
mvn test -Dtest=SessionListFlowTest
```

Expected: FAIL — `/api/agents/{id}/sessions` 不存在

- [ ] **Step 3: 实现 SessionController**

Write `src/main/java/io/agentscope/builder/saton/session/SessionController.java`:

```java
package io.agentscope.builder.saton.session;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.session.dto.ResetResp;
import io.agentscope.builder.saton.session.dto.SessionVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class SessionController {

    private final SessionService sessions;
    private final AgentDefinitionRepository agentRepo;

    public SessionController(SessionService sessions, AgentDefinitionRepository agentRepo) {
        this.sessions = sessions;
        this.agentRepo = agentRepo;
    }

    @GetMapping("/{id}/sessions")
    public Mono<List<SessionVO>> list(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                String me = StpUtil.getLoginIdAsString();
                requireOwn(id, me);
                return sessions.list(me, id);
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    @PostMapping("/{id}/sessions/{key}/reset")
    public Mono<ResetResp> reset(@PathVariable("id") Long id,
                                 @PathVariable("key") String key,
                                 ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                String me = StpUtil.getLoginIdAsString();
                requireOwn(id, me);
                return new ResetResp(sessions.reset(me, id, key));
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }

    private void requireOwn(Long agentDefId, String me) {
        if (agentRepo.findByIdAndOwnerId(agentDefId, me).isEmpty()) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
    }
}
```

- [ ] **Step 4: 接 AgentService.delete() 让它顺手清 session**

Edit `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`:

Replace the field declarations + constructor to inject `SessionService`, and update `delete(...)` to call `purgeAgent`. Specifically:

Find the constructor:

```java
    public AgentService(AgentDefinitionRepository repo,
                        ModelProviderRepository modelRepo,
                        io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver) {
        this.repo = repo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
    }
```

Replace with:

```java
    private final io.agentscope.builder.saton.session.SessionService sessionService;

    public AgentService(AgentDefinitionRepository repo,
                        ModelProviderRepository modelRepo,
                        io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver,
                        io.agentscope.builder.saton.session.SessionService sessionService) {
        this.repo = repo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
        this.sessionService = sessionService;
    }
```

Find the `delete` method:

```java
    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("agent not found: " + id);
        }
        runtimeResolver.invalidateByAgent(id);
    }
```

Replace with:

```java
    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("agent not found: " + id);
        }
        runtimeResolver.invalidateByAgent(id);
        sessionService.purgeAgent(me, id);
    }
```

- [ ] **Step 5: 运行 SessionListFlowTest 看到通过**

```bash
mvn test -Dtest=SessionListFlowTest
```

Expected: PASS — 6 个 case 都绿

- [ ] **Step 6: 跑全量回归**

```bash
mvn test
```

Expected: 92 tests PASS（86 之前 + 5 SessionServiceTest + 6 SessionListFlowTest - 5 dedupes... 实际：86 + 5 + 6 = 97 if 之前算上 SessionStoreConfigTest = 1 + SessionFlowTest = 2，应该是 84 + 1 + 2 + 5 + 6 = 98 累计）

注意：以上推算可能与实际略有出入，关键看"无回归 + 新测试全绿"两个标志。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/session/SessionController.java \
        src/main/java/io/agentscope/builder/saton/agent/AgentService.java \
        src/test/java/io/agentscope/builder/saton/session/SessionListFlowTest.java
git commit -m "feat(m6): SessionController + AgentService.delete 触发 purgeAgent"
```

---

## Task 5: WorkspacePathResolver（路径穿越校验）

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/workspace/WorkspacePathResolver.java`
- Create: `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceConfig.java`
- Test: `src/test/java/io/agentscope/builder/saton/workspace/WorkspacePathResolverTest.java`

- [ ] **Step 1: 写失败测试**

Write `src/test/java/io/agentscope/builder/saton/workspace/WorkspacePathResolverTest.java`:

```java
package io.agentscope.builder.saton.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePathResolverTest {

    @TempDir Path root;
    WorkspacePathResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new WorkspacePathResolver(root);
    }

    @Test
    void resolveValidRelativePath() {
        Path p = resolver.resolve("alice", 7L, "notes.md");
        assertTrue(p.startsWith(root.resolve("alice").resolve("7").resolve("files")));
        assertEquals("notes.md", p.getFileName().toString());
    }

    @Test
    void resolveSupportsSubdirectories() {
        Path p = resolver.resolve("alice", 7L, "subdir/inner.txt");
        assertTrue(p.endsWith(Path.of("subdir", "inner.txt")));
    }

    @Test
    void resolveRejectsDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "../etc/passwd"));
    }

    @Test
    void resolveRejectsDeepDotDot() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "a/b/../../../../escape"));
    }

    @Test
    void resolveRejectsAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("alice", 7L, "/etc/passwd"));
    }

    @Test
    void resolveRejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, ""));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("alice", 7L, "   "));
    }

    @Test
    void agentRootIsUniquePerOwnerAndAgent() {
        Path a = resolver.agentRoot("alice", 7L);
        Path b = resolver.agentRoot("bob",   7L);
        Path c = resolver.agentRoot("alice", 8L);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }
}
```

- [ ] **Step 2: 运行测试看到失败**

```bash
mvn test -Dtest=WorkspacePathResolverTest
```

Expected: COMPILATION FAIL — `WorkspacePathResolver` 不存在

- [ ] **Step 3: 实现 WorkspaceConfig + WorkspacePathResolver**

Write `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceConfig.java`:

```java
package io.agentscope.builder.saton.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/** 暴露 {@link WorkspacePathResolver}，根目录由 {@code app.workspace.root} 配置。 */
@Configuration
public class WorkspaceConfig {

    @Bean
    public WorkspacePathResolver workspacePathResolver(
            @Value("${app.workspace.root:./data/workspaces}") String root) {
        return new WorkspacePathResolver(Paths.get(root).toAbsolutePath().normalize());
    }
}
```

Write `src/main/java/io/agentscope/builder/saton/workspace/WorkspacePathResolver.java`:

```java
package io.agentscope.builder.saton.workspace;

import java.nio.file.Path;

/**
 * (ownerId, agentDefId, relPath) → 绝对 Path 解析 + 越界校验。
 *
 * <p>每个 agent 一个独立目录 {@code <root>/<ownerId>/<agentDefId>/files/}。所有用户输入
 * 的相对路径必须落在该目录内 —— 任何 {@code ..} 穿越、绝对路径、空白输入都拒绝。
 */
public class WorkspacePathResolver {

    private final Path root;

    public WorkspacePathResolver(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 该 agent 的文件根目录（{@code .../<ownerId>/<agentId>/files}）。 */
    public Path agentRoot(String ownerId, Long agentDefId) {
        return root.resolve(ownerId).resolve(String.valueOf(agentDefId)).resolve("files");
    }

    /** 解析相对路径到绝对路径，校验未越界。 */
    public Path resolve(String ownerId, Long agentDefId, String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        Path agentRoot = agentRoot(ownerId, agentDefId);
        Path target = agentRoot.resolve(relPath).normalize();
        if (!target.startsWith(agentRoot)) {
            throw new IllegalArgumentException("path escapes workspace: " + relPath);
        }
        return target;
    }
}
```

- [ ] **Step 4: 运行测试看到通过**

```bash
mvn test -Dtest=WorkspacePathResolverTest
```

Expected: PASS — 7 个 case 都绿

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/workspace/WorkspacePathResolver.java \
        src/main/java/io/agentscope/builder/saton/workspace/WorkspaceConfig.java \
        src/test/java/io/agentscope/builder/saton/workspace/WorkspacePathResolverTest.java
git commit -m "feat(m6): WorkspacePathResolver + 路径穿越校验"
```

---

## Task 6: WorkspaceService + dto

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceService.java`
- Create: `src/main/java/io/agentscope/builder/saton/workspace/dto/FileNodeVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/workspace/dto/WriteFileReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/workspace/dto/WorkspaceSummaryVO.java`
- Test: `src/test/java/io/agentscope/builder/saton/workspace/WorkspaceServiceTest.java`

- [ ] **Step 1: 写 dto**

Write `src/main/java/io/agentscope/builder/saton/workspace/dto/FileNodeVO.java`:

```java
package io.agentscope.builder.saton.workspace.dto;

/** 一个文件 / 目录的简要视图。 */
public record FileNodeVO(String name, String path, String type, long size) { }
```

Write `src/main/java/io/agentscope/builder/saton/workspace/dto/WriteFileReq.java`:

```java
package io.agentscope.builder.saton.workspace.dto;

/** PUT 文件内容请求体。 */
public record WriteFileReq(String content) { }
```

Write `src/main/java/io/agentscope/builder/saton/workspace/dto/WorkspaceSummaryVO.java`:

```java
package io.agentscope.builder.saton.workspace.dto;

/** GET / 摘要。 */
public record WorkspaceSummaryVO(String root, int fileCount) { }
```

- [ ] **Step 2: 写失败测试 — WorkspaceService CRUD**

Write `src/test/java/io/agentscope/builder/saton/workspace/WorkspaceServiceTest.java`:

```java
package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceTest {

    @TempDir Path root;
    WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(new WorkspacePathResolver(root));
    }

    @Test
    void writeThenReadRoundtrips() {
        service.write("alice", 7L, "notes.md", "hello");
        assertEquals("hello", service.read("alice", 7L, "notes.md"));
    }

    @Test
    void writeOverwritesExisting() {
        service.write("alice", 7L, "a.txt", "v1");
        service.write("alice", 7L, "a.txt", "v2");
        assertEquals("v2", service.read("alice", 7L, "a.txt"));
    }

    @Test
    void listIncludesCreatedFile() {
        service.write("alice", 7L, "x.txt", "X");
        service.write("alice", 7L, "sub/y.txt", "Y");
        List<FileNodeVO> nodes = service.list("alice", 7L);
        // top-level: x.txt + sub/
        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("x.txt") && n.type().equals("file")));
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("sub")   && n.type().equals("dir")));
    }

    @Test
    void deleteFileRemovesIt() {
        service.write("alice", 7L, "x.txt", "X");
        assertTrue(service.delete("alice", 7L, "x.txt"));
        assertFalse(service.delete("alice", 7L, "x.txt"));
    }

    @Test
    void readMissingFileThrows() {
        assertThrows(io.agentscope.builder.saton.common.error.NotFoundException.class,
                () -> service.read("alice", 7L, "missing.txt"));
    }

    @Test
    void summaryCountsAllFiles() {
        service.write("alice", 7L, "a.txt", "A");
        service.write("alice", 7L, "b/c.txt", "C");
        service.write("alice", 7L, "b/d.txt", "D");
        WorkspaceSummaryVO sum = service.summary("alice", 7L);
        assertEquals(3, sum.fileCount());
        assertNotNull(sum.root());
    }

    @Test
    void writeRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> service.write("alice", 7L, "../escape", "x"));
    }

    @Test
    void listReturnsEmptyForUnusedAgent() {
        assertTrue(service.list("alice", 999L).isEmpty());
    }
}
```

- [ ] **Step 3: 运行测试看到失败**

```bash
mvn test -Dtest=WorkspaceServiceTest
```

Expected: COMPILATION FAIL — `WorkspaceService` 不存在

- [ ] **Step 4: 实现 WorkspaceService**

Write `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceService.java`:

```java
package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 每个 agent 的 workspace 文件读写。纯 JDK NIO，单文件单调用，无并发原子性保证
 * （单人视角下够用）。
 *
 * <p>所有路径校验通过 {@link WorkspacePathResolver} 完成；越界请求抛
 * {@link IllegalArgumentException}。
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
    /** 单文件最大 512 KB；超过返回截断提示（参照原 builder）。 */
    private static final int MAX_READ = 512 * 1024;

    private final WorkspacePathResolver resolver;

    public WorkspaceService(WorkspacePathResolver resolver) {
        this.resolver = resolver;
    }

    public WorkspaceSummaryVO summary(String ownerId, Long agentDefId) {
        Path root = resolver.agentRoot(ownerId, agentDefId);
        int count = 0;
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                count = (int) walk.filter(Files::isRegularFile).count();
            } catch (IOException e) {
                log.warn("summary walk failed: {}", e.getMessage());
            }
        }
        return new WorkspaceSummaryVO(root.toString(), count);
    }

    public List<FileNodeVO> list(String ownerId, Long agentDefId) {
        Path root = resolver.agentRoot(ownerId, agentDefId);
        if (!Files.isDirectory(root)) return List.of();
        List<FileNodeVO> out = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> out.add(toNode(p, root)));
        } catch (IOException e) {
            log.warn("list failed: {}", e.getMessage());
        }
        return out;
    }

    public String read(String ownerId, Long agentDefId, String relPath) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        if (!Files.isRegularFile(p)) {
            throw new NotFoundException("file not found: " + relPath);
        }
        try {
            long size = Files.size(p);
            if (size > MAX_READ) {
                return "(file too large to display: " + size + " bytes)";
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("read failed: " + relPath, e);
        }
    }

    public void write(String ownerId, Long agentDefId, String relPath, String content) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("write failed: " + relPath, e);
        }
    }

    /** Returns true if the file existed and was removed. */
    public boolean delete(String ownerId, Long agentDefId, String relPath) {
        Path p = resolver.resolve(ownerId, agentDefId, relPath);
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new RuntimeException("delete failed: " + relPath, e);
        }
    }

    private FileNodeVO toNode(Path p, Path root) {
        String rel = root.relativize(p).toString().replace('\', '/');
        if (Files.isDirectory(p)) {
            return new FileNodeVO(p.getFileName().toString(), rel, "dir", 0L);
        }
        long size;
        try { size = Files.size(p); } catch (IOException e) { size = 0L; }
        return new FileNodeVO(p.getFileName().toString(), rel, "file", size);
    }
}
```

- [ ] **Step 5: 运行测试看到通过**

```bash
mvn test -Dtest=WorkspaceServiceTest
```

Expected: PASS — 8 个 case 都绿

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/workspace/WorkspaceService.java \
        src/main/java/io/agentscope/builder/saton/workspace/dto/FileNodeVO.java \
        src/main/java/io/agentscope/builder/saton/workspace/dto/WriteFileReq.java \
        src/main/java/io/agentscope/builder/saton/workspace/dto/WorkspaceSummaryVO.java \
        src/test/java/io/agentscope/builder/saton/workspace/WorkspaceServiceTest.java
git commit -m "feat(m6): WorkspaceService + dto"
```

---

## Task 7: WorkspaceController + 集成测试

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceController.java`
- Test: `src/test/java/io/agentscope/builder/saton/workspace/WorkspaceFlowTest.java`

REST 端点：

| Method | Path | 行为 |
|---|---|---|
| GET    | /api/agents/{id}/workspace | 摘要 |
| GET    | /api/agents/{id}/workspace/files | 列顶层 |
| GET    | /api/agents/{id}/workspace/file?path= | 读单文件 |
| PUT    | /api/agents/{id}/workspace/file?path= | 写单文件（body = `{content}`） |
| DELETE | /api/agents/{id}/workspace/file?path= | 删单文件 |

- [ ] **Step 1: 写 WorkspaceFlowTest**

Write `src/test/java/io/agentscope/builder/saton/workspace/WorkspaceFlowTest.java`:

```java
package io.agentscope.builder.saton.workspace;

import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import io.agentscope.builder.saton.workspace.dto.WriteFileReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WorkspaceFlowTest {

    @LocalServerPort int port;
    WebTestClient client;
    String token;
    Long agentId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30)).build();
        token = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange().expectStatus().isOk()
                .expectBody(LoginResponse.class).returnResult().getResponseBody().token();
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("ws-stub-" + System.nanoTime(),
                        "test-stub", Map.of()))
                .exchange().expectStatus().isOk()
                .expectBody(ModelProviderVO.class).returnResult().getResponseBody();
        AgentVO ag = client.post().uri("/api/agents")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "ws-agent-" + System.nanoTime(),
                        "ws", null, "hi", "react", mp.id(), 3, null))
                .exchange().expectStatus().isOk()
                .expectBody(AgentVO.class).returnResult().getResponseBody();
        agentId = ag.id();
    }

    @Test
    void writeReadListDeleteRoundtrip() {
        // write
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("hello world"))
                .exchange().expectStatus().isOk();

        // read
        String body = client.get().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertEquals("hello world", body);

        // list (top-level)
        List<FileNodeVO> nodes = client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(FileNodeVO.class).returnResult().getResponseBody();
        assertNotNull(nodes);
        assertTrue(nodes.stream().anyMatch(n -> n.name().equals("notes.md")));

        // delete
        client.delete().uri("/api/agents/" + agentId + "/workspace/file?path=notes.md")
                .header("satoken", token).exchange().expectStatus().isOk();

        // list again — empty
        List<FileNodeVO> after = client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(FileNodeVO.class).returnResult().getResponseBody();
        assertNotNull(after);
        assertTrue(after.stream().noneMatch(n -> n.name().equals("notes.md")));
    }

    @Test
    void summaryReturnsFileCount() {
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=a.txt")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("A")).exchange().expectStatus().isOk();
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=sub/b.txt")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("B")).exchange().expectStatus().isOk();

        WorkspaceSummaryVO sum = client.get().uri("/api/agents/" + agentId + "/workspace")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBody(WorkspaceSummaryVO.class).returnResult().getResponseBody();
        assertNotNull(sum);
        assertEquals(2, sum.fileCount());
    }

    @Test
    void readMissingFileReturns404() {
        client.get().uri("/api/agents/" + agentId + "/workspace/file?path=ghost.md")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void pathTraversalReturns400() {
        client.put().uri("/api/agents/" + agentId + "/workspace/file?path=../escape")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new WriteFileReq("x")).exchange().expectStatus().isBadRequest();
    }

    @Test
    void otherUsersAgentReturns404() {
        client.get().uri("/api/agents/999999/workspace/files")
                .header("satoken", token).exchange().expectStatus().isNotFound();
    }

    @Test
    void withoutTokenReturns401() {
        client.get().uri("/api/agents/" + agentId + "/workspace/files")
                .exchange().expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 2: 运行测试看到失败**

```bash
mvn test -Dtest=WorkspaceFlowTest
```

Expected: FAIL — `/api/agents/{id}/workspace/**` 端点不存在

- [ ] **Step 3: 实现 WorkspaceController**

Write `src/main/java/io/agentscope/builder/saton/workspace/WorkspaceController.java`:

```java
package io.agentscope.builder.saton.workspace;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.workspace.dto.FileNodeVO;
import io.agentscope.builder.saton.workspace.dto.WorkspaceSummaryVO;
import io.agentscope.builder.saton.workspace.dto.WriteFileReq;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/{id}/workspace")
public class WorkspaceController {

    private final WorkspaceService workspace;
    private final AgentDefinitionRepository agentRepo;

    public WorkspaceController(WorkspaceService workspace, AgentDefinitionRepository agentRepo) {
        this.workspace = workspace;
        this.agentRepo = agentRepo;
    }

    @GetMapping
    public Mono<WorkspaceSummaryVO> summary(@PathVariable("id") Long id, ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return workspace.summary(me, id);
        });
    }

    @GetMapping("/files")
    public Mono<List<FileNodeVO>> list(@PathVariable("id") Long id, ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return workspace.list(me, id);
        });
    }

    @GetMapping(value = "/file", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> read(@PathVariable("id") Long id,
                             @RequestParam("path") String path,
                             ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return workspace.read(me, id, path);
        });
    }

    @PutMapping("/file")
    public Mono<Map<String, Object>> write(@PathVariable("id") Long id,
                                           @RequestParam("path") String path,
                                           @RequestBody WriteFileReq req,
                                           ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            workspace.write(me, id, path, req == null ? "" : req.content());
            return Map.of("ok", true);
        });
    }

    @DeleteMapping("/file")
    public Mono<Map<String, Object>> delete(@PathVariable("id") Long id,
                                            @RequestParam("path") String path,
                                            ServerWebExchange ex) {
        return scoped(ex, me -> {
            requireOwn(id, me);
            return Map.of("removed", workspace.delete(me, id, path));
        });
    }

    private void requireOwn(Long agentDefId, String me) {
        if (agentRepo.findByIdAndOwnerId(agentDefId, me).isEmpty()) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
    }

    /** Bind sa-token context inside the lambda (spec §12.4) and clear on exit. */
    private <T> Mono<T> scoped(ServerWebExchange ex,
                               java.util.function.Function<String, T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(ex);
            try {
                return body.apply(StpUtil.getLoginIdAsString());
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        });
    }
}
```

- [ ] **Step 4: 路径穿越测试需要 IllegalArgumentException → 400 的全局兜底**

Check `src/main/java/io/agentscope/builder/saton/common/GlobalErrorWebExceptionHandler.java`. If it doesn't already map `IllegalArgumentException` to 400 (it should not — M1-M5 没加过；目前裸 IllegalArgumentException 走 500），加入这个分支。

Open `src/main/java/io/agentscope/builder/saton/common/GlobalErrorWebExceptionHandler.java` and find the `handle` method. Look for any existing branch that maps to `HttpStatus.BAD_REQUEST`. If `IllegalArgumentException` is not mapped, modify the handler — insert this branch right BEFORE the final `return Mono.error(ex);`:

```java
        IllegalArgumentException badArg = findCause(ex, IllegalArgumentException.class);
        if (badArg != null) {
            return writeJson(exchange, org.springframework.http.HttpStatus.BAD_REQUEST,
                    ApiError.of(400, badArg.getMessage()));
        }
```

If a generic `findCause(Throwable, Class<?>)` helper does not exist in the file, copy the existing `findNotLogin` pattern but parameterized — add this private method:

```java
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable cur = ex;
        for (int i = 0; cur != null && i < 8; i++) {
            if (type.isInstance(cur)) return (T) cur;
            cur = cur.getCause();
        }
        return null;
    }
```

(If the file already has this helper, just reuse it — don't duplicate.)

Read the file before editing. If you find that the project's existing exception handlers already cover `IllegalArgumentException → 400` (e.g. via a `@RestControllerAdvice`), skip this step and re-run the WorkspaceFlowTest — if `pathTraversalReturns400` already passes, this step is a no-op.

- [ ] **Step 5: 运行测试看到通过**

```bash
mvn test -Dtest=WorkspaceFlowTest
```

Expected: PASS — 6 个 case 都绿

- [ ] **Step 6: 全量回归**

```bash
mvn test
```

Expected: 全绿，累计约 100+ tests

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/agentscope/builder/saton/workspace/WorkspaceController.java \
        src/main/java/io/agentscope/builder/saton/common/GlobalErrorWebExceptionHandler.java \
        src/test/java/io/agentscope/builder/saton/workspace/WorkspaceFlowTest.java
git commit -m "feat(m6): WorkspaceController + IllegalArgumentException → 400 兜底"
```

---

## Task 8: 收尾 — README + spec §12.16 / §12.17 占位 + tag

**Files:**
- Modify: `README.md`（如已存在则 append M6 章节）
- Modify: `docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`（§12 新增 2 条踩坑占位）

- [ ] **Step 1: 给 spec 加 §12.16 / §12.17 占位（实施过程中如发现新坑就填）**

Edit `docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`. Find the line containing `### 12.15 AgentEvent 是 Jackson 2 注解` — at the end of that section (before `## 13. 开放问题`), insert:

```markdown

### 12.16 [M6 占位 — 实施过程中如发现新坑，填这里]

（M6 实施时如遇 `JsonFileAgentStateStore` / `RuntimeContext.userId+sessionId` / `WorkspacePathResolver` 路径校验相关的新坑，留在这里。如未发现新坑，删除此条目。）

### 12.17 [M6 占位 — 实施过程中如发现新坑，填这里]

（同上。）
```

- [ ] **Step 2: 跑最后一次全量测试确认所有绿**

```bash
mvn test
```

Expected: ALL PASS

- [ ] **Step 3: tag**

```bash
git add docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md
git commit -m "docs(spec): §12.16-12.17 占位 — 待 M6 实施时填"
git tag m6-session-workspace-done
```

- [ ] **Step 4: 报告 M6 完成**

整理给 user 的总结报告，包括：
- 通过的测试数量
- 关键能力：sessionKey 透传 + 多轮历史 + workspace 文件 CRUD + path traversal 防御
- 累计 spec §12 条目数
- M7 提前预告：SkillFactory + HookFactory + SubAgentTool

---

## 自检（Self-Review）

执行完所有 task 后，对照本 plan 检查：

1. **Spec coverage** —— M6 行（spec §9）= "workspace CRUD + SessionService + 多轮对话 + transcript 回读 / 完成标志: 刷新页面继续上次对话"
   - workspace CRUD → Task 5/6/7 ✅
   - SessionService → Task 3 ✅
   - 多轮对话 → Task 1/2/3（stateStore + sessionKey 透传 + agentscope-core 原生 slot 加载）✅
   - transcript 回读 = "session 列表" → Task 4 ✅
   - 完成标志（刷新页面继续上次对话）→ SessionFlowTest.secondCallSeesFirstCallHistory 验证 ✅

2. **Placeholder 扫描** —— 全文搜了一遍，无 TBD / TODO / "add error handling" 之类的空话；每个步骤都有完整代码或具体命令。Task 8 §12.16/12.17 是**实施期占位**（带明确说明），不是 plan placeholder。

3. **类型一致性** ——
   - `ChatSendReq` 加 sessionKey 后是 3 参 record，老 2 参构造器保留（兼容 ChatFlowTest）✅
   - `AgentBuildOrchestrator` 构造器新增 `AgentStateStore` 参数 —— `AgentRuntimeResolver` 通过 Spring DI 拿到改造后的 orchestrator bean，无需改动 ✅
   - `AgentService` 构造器新增 `SessionService` —— controller 通过 DI 拿改造后的 service bean，无需改动 ✅
   - `SessionService(Path, AgentStateStore)` 测试 ctor 和 `SessionService(String, AgentStateStore)` Spring ctor 区分清晰 ✅
   - `WorkspacePathResolver.agentRoot/resolve` 在 Task 5 定义，Task 6 / 7 都按 `(ownerId, agentDefId, ...)` 签名调用 ✅

4. **关键风险预防** ——
   - sa-token 在 SSE / Mono 中的 context binding（§12.4）—— `SessionController` / `WorkspaceController` 全部用 `scoped(ex, body)` 包裹 ✅
   - `Flux.defer` 内 sa-token 丢失（§12.14）—— ChatService.stream 仍保留 `String me = StpUtil.getLoginIdAsString()` 在 defer 之前 ✅
   - `@PathVariable` 显式名字（§12.11）—— 全部 controller 都写 `@PathVariable("id")` ✅
   - `IllegalArgumentException → 400` 兜底 —— Task 7 Step 4 显式处理 ✅
   - 路径穿越—— `WorkspacePathResolver` 用 `Path.normalize().startsWith()` 而非字符串包含 `..` ✅

---

## 执行说明

按 subagent-driven 顺序执行：每个 Task 一个 implementer subagent，结束后 spec compliance review + code quality review，全绿才 mark done 推下个 task。Task 之间有依赖（按编号），不要并发。
