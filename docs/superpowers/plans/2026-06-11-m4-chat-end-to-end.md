# M4 第一个能聊的 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agentscope-builder-saton` 跑通"创建 agent → POST 消息 → 拿到 LLM 回复"端到端流程。前端可以通过 `POST /api/agents` 创建一个引用 ModelProvider 的 agent 定义，然后 `POST /api/agents/{id}/chat/send` 发消息收到 reply。完成标志：
1. 集成测试 `ChatFlowTest` 用 mock model 拿到 "echo: hello" reply
2. 手动用 admin 账号 + 真 DashScope key 跑一遍真实 LLM 回复
3. M3 / M2 接口无回归

**Architecture:**
- 新增 `agent_definition` 表（per-owner，引用 `model_provider`），子配置 JSON 列（M4 只填 sys_prompt + default_model_provider_id，工具/技能等留空）
- `AgentService` CRUD（owner 隔离 + 唯一约束 + 删除时联动 runtime cache invalidate）
- `AgentBuildOrchestrator` —— 调用 `ModelFactory.instantiate(modelEntity)` 得到 `Model`，构造 `ReActAgent`（无 toolkit、无 skill；HarnessAgent 留 M6）
- `AgentRuntimeResolver` —— `Map<RuntimeKey, ReActAgent>` 缓存，`RuntimeKey = (agentDefId, modelProviderId)`；`invalidate(agentId)` 用于改 / 删 agent 后丢弃缓存
- `ChatController` —— `POST /api/agents/{id}/chat/send` 非流式同步返回；可选 `overrideModelProviderId`

**关键决策**（仅本里程碑相关）：
- **用 `ReActAgent` 而非 `HarnessAgent`** —— ReActAgent builder 只要 `sysPrompt + model + toolkit + maxIters`，无 workspace / skills / state store。HarnessAgent 要等 M6 workspace 上线再上。
- **不做流式 SSE** —— M5 才做。M4 用 `POST /api/agents/{id}/chat/send` 同步返回完整文本。
- **`agent_share` 表暂不建** —— M9 分享时再加。M4 只需 owner 自己用。
- **工具列表 `tool_specs_json` 字段保留**（schema 完整），但 M4 创建/聊天时**忽略**它的值；ToolFactory 留 M5。
- **测试 LLM 用 stub** —— 不发外部 HTTP。在 `src/test/java` 加一个 `TestStubModelProviderType` + `TestStubModel`，被 Spring 自动收集进 registry，type 名 `"test-stub"`。

**Tech Stack:** 沿用，无新依赖（`agentscope-core:2.0.0-RC2` 已引入）。

**踩坑预防（spec §12，全部已在前 3 个 milestone 验证）**：
- `@PathVariable("xxx")` 必须写显式名字（§12.11）
- ObjectMapper import `tools.jackson.databind` —— 但本里程碑用 `JsonUtil.mapper()` 统一封装，不直接 import（§12.3）
- Controller 调 `StpUtil.*` 必须 `SaReactorSyncHolder.setContext(exchange)/clearContext()` 包裹（§12.4）—— **本里程碑 AgentController 和 ChatController 都要**
- `@DataJpaTest` 用 `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` import（§12.2）
- agent_definition.tool_specs_json 等列**不加** `EncryptedJsonConverter`（这些字段不含 secret，不需要加密，省一层 JSON 解析开销）
- 测试用 `@LocalServerPort + WebTestClient.bindToServer()`（§12.2）

---

## File Structure

```
agentscope-builder-saton/
└── src/main/java/io/agentscope/builder/saton/
    └── agent/
        ├── AgentDefinitionEntity.java                # @Entity 'agent_definition'
        ├── AgentDefinitionRepository.java            # owner-scoped CRUD
        ├── AgentService.java                          # 业务，调 invalidate
        ├── AgentController.java                       # /api/agents CRUD
        ├── dto/
        │   ├── AgentUpsertReq.java                    # name/sysPrompt/agentType/defaultModelProviderId/maxIters/...
        │   └── AgentVO.java                           # 返回前端，不含 props 加密之类
        ├── runtime/
        │   ├── AgentBuildOrchestrator.java            # 调 ModelFactory + 构 ReActAgent
        │   ├── AgentRuntimeResolver.java              # (agentDefId, modelProviderId) 缓存 + invalidate
        │   └── RuntimeKey.java                        # record
        └── chat/
            ├── ChatController.java                    # POST /api/agents/{id}/chat/send
            ├── ChatService.java                       # 业务：调 resolver, 收 reply
            └── dto/
                ├── ChatSendReq.java                   # { message, overrideModelProviderId? }
                └── ChatSendResp.java                  # { reply, agentId, modelProviderId(actually used) }

src/test/java/io/agentscope/builder/saton/
└── agent/
    ├── AgentDefinitionRepositoryTest.java
    ├── AgentFlowTest.java                             # 集成 CRUD 测试
    ├── runtime/
    │   ├── TestStubModelProviderType.java             # @Component, type="test-stub"
    │   ├── TestStubModel.java                          # implements Model, canned echo reply
    │   └── AgentRuntimeResolverTest.java
    └── chat/
        └── ChatFlowTest.java                           # 端到端 chat with stub model
```

**职责说明**：

- `AgentDefinitionEntity` —— 字段含：`id, owner_id, agent_id (业务唯一), name, description, sys_prompt, agent_type, default_model_provider_id, max_iters, workspace_path, tool_specs_json, skill_refs_json, hook_specs_json, subagent_refs_json, skill_repositories_json, sandbox_mode, sandbox_scope, run_as, fork_of, created_at, updated_at`。所有 JSON 列**不加密**（不含 secret）。
- `AgentDefinitionRepository` —— `findByOwnerIdOrderByCreatedAtDesc / findByIdAndOwnerId / existsByOwnerIdAndAgentId / deleteByIdAndOwnerId`。
- `AgentService` —— CRUD + `delete` 时调 `runtimeResolver.invalidate(agentDefId)`；`update` 同理（因为可能改了 default_model_provider_id 或 sys_prompt）。
- `AgentController` —— `@RequestMapping("/api/agents")`，5 个端点（list / get / create / update / delete）；模板复用 M2 套路。
- `AgentBuildOrchestrator` —— `build(AgentDefinitionEntity def, ModelProviderEntity model)` → `ReActAgent`。构造：`ReActAgent.builder().sysPrompt(def.getSysPrompt()).model(modelFactory.instantiate(model)).toolkit(new Toolkit()).maxIters(def.getMaxIters() != null ? def.getMaxIters() : 10).build()`.
- `RuntimeKey` —— `record(Long agentDefId, Long modelProviderId)`。
- `AgentRuntimeResolver` —— 内部 `Map<RuntimeKey, ReActAgent>`（ConcurrentHashMap）+ `Map<Long, Set<RuntimeKey>> byAgentDefId`（反向索引）。`resolve(agentId, overrideModelProviderId)` 加锁拿 def + model entity + cached agent 或新建。`invalidate(agentDefId)` 用反向索引找出所有 key 移除。
- `ChatService` —— `send(agentDefId, message, overrideModelProviderId)` 返回 `ChatSendResp`。内部：`StpUtil.getLoginIdAsString()` 拿 me → load agent def via repo → resolver.resolve → 构 `Msg` → `agent.call(msg)` → block on Mono → 提取 TextBlock 文本。
- `ChatController` —— 一个 POST 端点，body `ChatSendReq`，返回 `ChatSendResp`。SaReactorSyncHolder 必须。

---

## Task 1: agent_definition 表 + Entity + Repository

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionEntity.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/AgentDefinitionRepositoryTest.java`

- [ ] **Step 1: 写失败测试**

Write `src/test/java/io/agentscope/builder/saton/agent/AgentDefinitionRepositoryTest.java`:

```java
package io.agentscope.builder.saton.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AgentDefinitionRepositoryTest {

    @Autowired AgentDefinitionRepository repo;

    @Test
    void crudByOwner() {
        AgentDefinitionEntity a = newDef("alice", "alice-bot");
        repo.save(a);
        AgentDefinitionEntity b = newDef("bob", "bob-bot");
        repo.save(b);

        List<AgentDefinitionEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-bot", aliceList.get(0).getAgentId());

        assertTrue(repo.existsByOwnerIdAndAgentId("alice", "alice-bot"));
        assertFalse(repo.existsByOwnerIdAndAgentId("alice", "bob-bot"));

        Optional<AgentDefinitionEntity> crossOwner = repo.findByIdAndOwnerId(a.getId(), "bob");
        assertTrue(crossOwner.isEmpty());

        Optional<AgentDefinitionEntity> mine = repo.findByIdAndOwnerId(a.getId(), "alice");
        assertTrue(mine.isPresent());
        assertEquals(7L, mine.get().getDefaultModelProviderId());
    }

    @Test
    void deleteByIdAndOwnerReturnsCount() {
        AgentDefinitionEntity a = newDef("alice", "to-delete");
        repo.save(a);
        long deleted = repo.deleteByIdAndOwnerId(a.getId(), "alice");
        assertEquals(1, deleted);
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isEmpty());
    }

    private AgentDefinitionEntity newDef(String owner, String agentId) {
        AgentDefinitionEntity e = new AgentDefinitionEntity();
        e.setOwnerId(owner);
        e.setAgentId(agentId);
        e.setName(agentId);
        e.setSysPrompt("you are a helpful assistant");
        e.setAgentType("react");
        e.setDefaultModelProviderId(7L);
        e.setMaxIters(10);
        long now = System.currentTimeMillis();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}
```

- [ ] **Step 2: 运行测试，预期编译失败**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn -q test -Dtest=AgentDefinitionRepositoryTest
```

Expected: 编译错 `AgentDefinitionEntity` / `AgentDefinitionRepository` 不存在。

- [ ] **Step 3: 写 AgentDefinitionEntity**

Write `src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionEntity.java`:

```java
package io.agentscope.builder.saton.agent;

import jakarta.persistence.*;

/**
 * Agent 配置。表 {@code agent_definition}。
 *
 * <p>所有 JSON 列（tool_specs_json / skill_refs_json / hook_specs_json / subagent_refs_json /
 * skill_repositories_json）都 <b>不</b>加密 —— 它们不含 secret，引用的 secret 都在
 * {@code model_provider} / {@code mcp_server} 等独立资源表里。
 *
 * <p>M4 仅使用 {@code sys_prompt + default_model_provider_id + max_iters} 三字段构建 ReActAgent；
 * tool / skill / hook / subagent_refs JSON 列在 M5-M7 才会被消费。
 */
@Entity
@Table(
        name = "agent_definition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_definition_owner_agent",
                columnNames = {"owner_id", "agent_id"}),
        indexes = {
                @Index(name = "ix_agent_definition_owner", columnList = "owner_id"),
                @Index(name = "ix_agent_definition_agent_id", columnList = "agent_id")
        }
)
public class AgentDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    /** 业务唯一标识，per-owner 唯一。 */
    @Column(name = "agent_id", length = 128, nullable = false)
    private String agentId;

    @Column(name = "name", length = 200)
    private String name;

    @Lob
    @Column(name = "description")
    private String description;

    @Lob
    @Column(name = "sys_prompt")
    private String sysPrompt;

    /** "react"（M4 默认）/ "harness"（M6+）。 */
    @Column(name = "agent_type", length = 50, nullable = false)
    private String agentType;

    @Column(name = "default_model_provider_id", nullable = false)
    private Long defaultModelProviderId;

    @Column(name = "max_iters")
    private Integer maxIters;

    @Column(name = "workspace_path", length = 1024)
    private String workspacePath;

    @Lob @Column(name = "tool_specs_json") private String toolSpecsJson;
    @Lob @Column(name = "skill_refs_json") private String skillRefsJson;
    @Lob @Column(name = "hook_specs_json") private String hookSpecsJson;
    @Lob @Column(name = "subagent_refs_json") private String subagentRefsJson;
    @Lob @Column(name = "skill_repositories_json") private String skillRepositoriesJson;

    @Column(name = "sandbox_mode", length = 16)
    private String sandboxMode;

    @Column(name = "sandbox_scope", length = 16)
    private String sandboxScope;

    @Column(name = "run_as", length = 20)
    private String runAs;

    @Column(name = "fork_of", length = 128)
    private String forkOf;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSysPrompt() { return sysPrompt; }
    public void setSysPrompt(String sysPrompt) { this.sysPrompt = sysPrompt; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Long getDefaultModelProviderId() { return defaultModelProviderId; }
    public void setDefaultModelProviderId(Long defaultModelProviderId) {
        this.defaultModelProviderId = defaultModelProviderId;
    }
    public Integer getMaxIters() { return maxIters; }
    public void setMaxIters(Integer maxIters) { this.maxIters = maxIters; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public String getToolSpecsJson() { return toolSpecsJson; }
    public void setToolSpecsJson(String toolSpecsJson) { this.toolSpecsJson = toolSpecsJson; }
    public String getSkillRefsJson() { return skillRefsJson; }
    public void setSkillRefsJson(String skillRefsJson) { this.skillRefsJson = skillRefsJson; }
    public String getHookSpecsJson() { return hookSpecsJson; }
    public void setHookSpecsJson(String hookSpecsJson) { this.hookSpecsJson = hookSpecsJson; }
    public String getSubagentRefsJson() { return subagentRefsJson; }
    public void setSubagentRefsJson(String subagentRefsJson) { this.subagentRefsJson = subagentRefsJson; }
    public String getSkillRepositoriesJson() { return skillRepositoriesJson; }
    public void setSkillRepositoriesJson(String skillRepositoriesJson) {
        this.skillRepositoriesJson = skillRepositoriesJson;
    }
    public String getSandboxMode() { return sandboxMode; }
    public void setSandboxMode(String sandboxMode) { this.sandboxMode = sandboxMode; }
    public String getSandboxScope() { return sandboxScope; }
    public void setSandboxScope(String sandboxScope) { this.sandboxScope = sandboxScope; }
    public String getRunAs() { return runAs; }
    public void setRunAs(String runAs) { this.runAs = runAs; }
    public String getForkOf() { return forkOf; }
    public void setForkOf(String forkOf) { this.forkOf = forkOf; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: 写 AgentDefinitionRepository**

Write `src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java`:

```java
package io.agentscope.builder.saton.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<AgentDefinitionEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndAgentId(String ownerId, String agentId);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
```

- [ ] **Step 5: 跑测试 PASS**

```powershell
mvn test -Dtest=AgentDefinitionRepositoryTest
```

Expected: `Tests run: 2, Failures: 0`.

- [ ] **Step 6: Commit**

```powershell
git add src/
git commit -m "feat(agent): AgentDefinitionEntity + Repository (owner-scoped JSON cols)"
```

---

## Task 2: AgentService CRUD + DTOs + AgentController + 集成测试

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentUpsertReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentController.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java`

> Note: M4-2 不引入 AgentRuntimeResolver；invalidate 调用先留空（M4-4 加）。

- [ ] **Step 1: 写两个 DTO**

Write `src/main/java/io/agentscope/builder/saton/agent/dto/AgentUpsertReq.java`:

```java
package io.agentscope.builder.saton.agent.dto;

/**
 * Create / update agent 的请求体。M4 字段最小集 —— tool/skill/hook 等扩展字段以后再加。
 */
public record AgentUpsertReq(
        String agentId,             // 业务唯一标识（per-owner）
        String name,
        String description,
        String sysPrompt,
        String agentType,           // "react" / "harness"（M4 默认 react）
        Long defaultModelProviderId,
        Integer maxIters
) {}
```

Write `src/main/java/io/agentscope/builder/saton/agent/dto/AgentVO.java`:

```java
package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;

public record AgentVO(
        Long id,
        String agentId,
        String name,
        String description,
        String sysPrompt,
        String agentType,
        Long defaultModelProviderId,
        Integer maxIters,
        long createdAt,
        long updatedAt
) {
    public static AgentVO from(AgentDefinitionEntity e) {
        return new AgentVO(
                e.getId(), e.getAgentId(), e.getName(), e.getDescription(),
                e.getSysPrompt(), e.getAgentType(),
                e.getDefaultModelProviderId(), e.getMaxIters(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

- [ ] **Step 2: 写 AgentService**

Write `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`:

```java
package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentService {

    private final AgentDefinitionRepository repo;
    private final ModelProviderRepository modelRepo;

    public AgentService(AgentDefinitionRepository repo, ModelProviderRepository modelRepo) {
        this.repo = repo;
        this.modelRepo = modelRepo;
    }

    public List<AgentVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me).stream().map(AgentVO::from).toList();
    }

    public AgentVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return AgentVO.from(loadMine(id, me));
    }

    @Transactional
    public AgentVO create(AgentUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        requireModelOwned(req.defaultModelProviderId(), me);
        if (repo.existsByOwnerIdAndAgentId(me, req.agentId())) {
            throw new ConflictException("agentId already exists: " + req.agentId());
        }
        long now = System.currentTimeMillis();
        AgentDefinitionEntity e = new AgentDefinitionEntity();
        e.setOwnerId(me);
        e.setAgentId(req.agentId());
        e.setName(req.name() != null ? req.name() : req.agentId());
        e.setDescription(req.description());
        e.setSysPrompt(req.sysPrompt());
        e.setAgentType(req.agentType() != null ? req.agentType() : "react");
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return AgentVO.from(repo.save(e));
    }

    @Transactional
    public AgentVO update(Long id, AgentUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        requireModelOwned(req.defaultModelProviderId(), me);
        AgentDefinitionEntity e = loadMine(id, me);
        if (!e.getAgentId().equals(req.agentId())
                && repo.existsByOwnerIdAndAgentId(me, req.agentId())) {
            throw new ConflictException("agentId already exists: " + req.agentId());
        }
        e.setAgentId(req.agentId());
        e.setName(req.name() != null ? req.name() : req.agentId());
        e.setDescription(req.description());
        e.setSysPrompt(req.sysPrompt());
        e.setAgentType(req.agentType() != null ? req.agentType() : "react");
        e.setDefaultModelProviderId(req.defaultModelProviderId());
        e.setMaxIters(req.maxIters() != null ? req.maxIters() : 10);
        e.setUpdatedAt(System.currentTimeMillis());
        // M4-4 完成后这里调 runtimeResolver.invalidate(e.getId())；
        // 本步骤先不调（解耦本任务）。
        return AgentVO.from(e);
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("agent not found: " + id);
        }
        // M4-4 完成后这里调 runtimeResolver.invalidate(id)；本步骤先不调。
    }

    private AgentDefinitionEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("agent not found: " + id));
    }

    private void requireFields(AgentUpsertReq req) {
        if (req == null
                || req.agentId() == null || req.agentId().isBlank()
                || req.defaultModelProviderId() == null) {
            throw new IllegalArgumentException("agentId and defaultModelProviderId required");
        }
    }

    private void requireModelOwned(Long modelId, String me) {
        if (modelRepo.findByIdAndOwnerId(modelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + modelId);
        }
    }
}
```

- [ ] **Step 3: 写 AgentController**

Write `src/main/java/io/agentscope/builder/saton/agent/AgentController.java`:

```java
package io.agentscope.builder.saton.agent;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.agent.dto.AgentUpsertReq;
import io.agentscope.builder.saton.agent.dto.AgentVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<AgentVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<AgentVO> get(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<AgentVO> create(@RequestBody AgentUpsertReq req, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<AgentVO> update(@PathVariable("id") Long id,
                                @RequestBody AgentUpsertReq req,
                                ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return null; });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
```

- [ ] **Step 4: 写集成测试**

Write `src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java`:

```java
package io.agentscope.builder.saton.agent;

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
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AgentFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;
    Long modelId;

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

        // 先建一个 model provider 作为依赖
        ModelProviderVO mp = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("agent-test-model", "dashscope",
                        Map.of("apiKey", "sk-not-real", "modelName", "qwen-max")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(mp);
        this.modelId = mp.id();
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        AgentVO created = client.post().uri("/api/agents")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "my-agent", "My Agent", "test", "you are helpful",
                        "react", modelId, 5))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-agent", created.agentId());
        assertEquals(modelId, created.defaultModelProviderId());
        assertEquals(5, created.maxIters());

        // list
        List<AgentVO> list = client.get().uri("/api/agents")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<AgentVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(a -> "my-agent".equals(a.agentId())));

        // get
        AgentVO got = client.get().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("you are helpful", got.sysPrompt());

        // update
        AgentVO updated = client.put().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq(
                        "my-agent", "Renamed", "x", "you are now strict",
                        "react", modelId, 8))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgentVO.class)
                .returnResult().getResponseBody();
        assertNotNull(updated);
        assertEquals("you are now strict", updated.sysPrompt());
        assertEquals(8, updated.maxIters());

        // delete
        client.delete().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        // get → 404
        client.get().uri("/api/agents/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateAgentIdReturns409() {
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq("dup-agent", null, null, null,
                        "react", modelId, null))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq("dup-agent", null, null, null,
                        "react", modelId, null))
                .exchange().expectStatus().is4xxClientError();   // 409
    }

    @Test
    void createWithNonExistentModelReturns404() {
        client.post().uri("/api/agents").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AgentUpsertReq("bad-agent", null, null, null,
                        "react", 999999L, null))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/agents")
                .exchange().expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 5: 跑测试**

```powershell
mvn test -Dtest=AgentFlowTest
```

Expected: `Tests run: 4, Failures: 0`.

- [ ] **Step 6: Commit**

```powershell
git add src/
git commit -m "feat(agent): AgentService + AgentController CRUD + integration test"
```

---

## Task 3: AgentBuildOrchestrator + AgentRuntimeResolver + 单测

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/runtime/RuntimeKey.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolver.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java` (wire invalidate calls)
- Test: `src/test/java/io/agentscope/builder/saton/agent/runtime/TestStubModel.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/runtime/TestStubModelProviderType.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolverTest.java`

> 注意：TestStubModel + TestStubModelProviderType 在 `src/test/java` 下，被 `@SpringBootTest` 自动扫到，本里程碑及以后的所有 Spring 测试都会多一个 type=`test-stub`。这是预期。

- [ ] **Step 1: 写 RuntimeKey**

Write `src/main/java/io/agentscope/builder/saton/agent/runtime/RuntimeKey.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

/**
 * AgentRuntimeResolver 的缓存 key：{@code (agentDefId, modelProviderId)}。
 *
 * <p>同一个 agent 切换模型时，两个不同 key 各自缓存一个 ReActAgent 实例；
 * agent 自身 invalidate 时按 agentDefId 一并丢弃所有相关 key。
 */
public record RuntimeKey(Long agentDefId, Long modelProviderId) {}
```

- [ ] **Step 2: 写 AgentBuildOrchestrator**

Write `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

/**
 * 用 agent_definition 行 + model_provider 行装配一个真实可调的 {@link ReActAgent}。
 *
 * <p>M4 只接 model；toolkit 留空（{@code new Toolkit()}）；workspace / skills / hooks 待 M5+。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;

    public AgentBuildOrchestrator(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    public ReActAgent build(AgentDefinitionEntity def, ModelProviderEntity model) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;
        return ReActAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(new Toolkit())
                .maxIters(maxIters)
                .build();
    }
}
```

> If `ReActAgent.builder().name(...)` doesn't exist (unlikely) or `.maxIters(int)` has different signature, adjust per compiler. The shape was verified in codegraph exploration — name/sysPrompt/model/toolkit/maxIters all exist.

- [ ] **Step 3: 写 AgentRuntimeResolver**

Write `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolver.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.core.ReActAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (agentDefId, modelProviderId) → {@link ReActAgent} 缓存。
 *
 * <p>语义：
 * <ul>
 *   <li>{@link #resolve(Long, Long)} 拿到（或惰性构造）缓存的 agent</li>
 *   <li>{@link #invalidateByAgent(Long)} agent 自身改/删时，移除所有相关 key</li>
 *   <li>{@link #invalidateByModel(Long)} model 改/删时，移除所有用这个 model 的 key</li>
 * </ul>
 *
 * <p>简化决策（M4）：
 * <ul>
 *   <li>缓存条目无 TTL；进程重启即 lost</li>
 *   <li>构造期阻塞主线程，由 Controller {@code Mono.fromCallable} 包到弹性线程</li>
 *   <li>不验证 model 是 agent owner 的 —— 这层校验在 AgentService / ChatService 入口已做</li>
 * </ul>
 */
@Component
public class AgentRuntimeResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeResolver.class);

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentBuildOrchestrator orchestrator;

    private final Map<RuntimeKey, ReActAgent> cache = new ConcurrentHashMap<>();
    /** 反向索引：byAgent[defId] 集合中所有 RuntimeKey 用了这个 defId。 */
    private final Map<Long, Set<RuntimeKey>> byAgent = new ConcurrentHashMap<>();
    /** 反向索引：byModel[modelId] 集合中所有 RuntimeKey 用了这个 modelId。 */
    private final Map<Long, Set<RuntimeKey>> byModel = new ConcurrentHashMap<>();

    public AgentRuntimeResolver(AgentDefinitionRepository agentRepo,
                                ModelProviderRepository modelRepo,
                                AgentBuildOrchestrator orchestrator) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.orchestrator = orchestrator;
    }

    /**
     * 拿（或惰性构造）某 agent 在某 model 下的 ReActAgent。
     *
     * @param agentDefId         agent_definition 主键
     * @param modelProviderId    要使用的 model_provider id（可能与 default 不同 = 临时切换）
     */
    public ReActAgent resolve(Long agentDefId, Long modelProviderId) {
        RuntimeKey key = new RuntimeKey(agentDefId, modelProviderId);
        return cache.computeIfAbsent(key, k -> {
            AgentDefinitionEntity def = agentRepo.findById(agentDefId)
                    .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
            ModelProviderEntity model = modelRepo.findById(modelProviderId)
                    .orElseThrow(() -> new NotFoundException("model provider not found: " + modelProviderId));
            ReActAgent built = orchestrator.build(def, model);
            byAgent.computeIfAbsent(agentDefId, x -> ConcurrentHashMap.newKeySet()).add(k);
            byModel.computeIfAbsent(modelProviderId, x -> ConcurrentHashMap.newKeySet()).add(k);
            log.debug("built ReActAgent for {}/{}", agentDefId, modelProviderId);
            return built;
        });
    }

    public void invalidateByAgent(Long agentDefId) {
        Set<RuntimeKey> keys = byAgent.remove(agentDefId);
        if (keys == null) return;
        for (RuntimeKey k : keys) {
            cache.remove(k);
            Set<RuntimeKey> mKeys = byModel.get(k.modelProviderId());
            if (mKeys != null) mKeys.remove(k);
        }
        log.debug("invalidated {} cache entries for agent {}", keys.size(), agentDefId);
    }

    public void invalidateByModel(Long modelProviderId) {
        Set<RuntimeKey> keys = byModel.remove(modelProviderId);
        if (keys == null) return;
        for (RuntimeKey k : keys) {
            cache.remove(k);
            Set<RuntimeKey> aKeys = byAgent.get(k.agentDefId());
            if (aKeys != null) aKeys.remove(k);
        }
        log.debug("invalidated {} cache entries for model {}", keys.size(), modelProviderId);
    }

    /** 测试用：当前缓存条目数量。 */
    int cacheSize() {
        return cache.size();
    }
}
```

- [ ] **Step 4: 改 AgentService 调 invalidate**

Edit `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`:

- Add field `private final io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver;`
- Inject via constructor (add it as 3rd param, update body)
- In `update(...)` 方法体的 `e.setUpdatedAt(...)` 之后，加上 `runtimeResolver.invalidateByAgent(e.getId());`
- In `delete(...)` 方法体的 `if (n == 0) throw ...` 之后，加上 `runtimeResolver.invalidateByAgent(id);`

Final AgentService.java header / ctor area looks like:

```java
@Service
public class AgentService {

    private final AgentDefinitionRepository repo;
    private final ModelProviderRepository modelRepo;
    private final io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver;

    public AgentService(AgentDefinitionRepository repo,
                        ModelProviderRepository modelRepo,
                        io.agentscope.builder.saton.agent.runtime.AgentRuntimeResolver runtimeResolver) {
        this.repo = repo;
        this.modelRepo = modelRepo;
        this.runtimeResolver = runtimeResolver;
    }
    // ... rest unchanged ...
```

> 使用 FQN 避免改 import 列表。

- [ ] **Step 5: 写 TestStubModel**

Write `src/test/java/io/agentscope/builder/saton/agent/runtime/TestStubModel.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 测试桩 Model：忽略输入，无条件 echo 一段固定文本。
 *
 * <p>实现策略：用 codegraph_explore 验证 Model 接口的具体签名，按编译器报错调。
 * 如果接口方法不止 stream / call / 等，复制 super 默认或抛 UnsupportedOperationException。
 */
public class TestStubModel implements Model {

    public static final String CANNED_REPLY = "echo: hello";

    // The Model interface in agentscope-core 2.0.0-RC2 has these abstract methods (verify via
    // codegraph_explore if compilation fails):
    //   - stream(List<Msg> msgs, ...) -> Flux<ChatResponse>
    //   - call(List<Msg> msgs, ...) -> Mono<ChatResponse>
    // The shape of "..." may include Toolkit, GenerateOptions, etc.
    //
    // SUBAGENT: at implementation time, use codegraph_explore("Model interface methods") to
    // discover the exact required method signatures, then implement them all returning a
    // canned ChatResponse containing TextBlock(CANNED_REPLY). Do NOT guess.

    // PLACEHOLDER — replace with actual implementation per discovered signatures:
    // @Override public Flux<ChatResponse> stream(...) { ... }
    // @Override public Mono<ChatResponse> call(...) { ... }
}
```

**IMPORTANT note to implementing subagent**: `Model` 接口的真实 abstract methods 必须实地查证。用 `mcp__codegraph__codegraph_node` 看 `Model` interface 拿全部 abstract method 签名，然后**每个**都 implement，返回一个 `ChatResponse` 含 single `TextBlock(CANNED_REPLY)`。如果签名比想象的复杂（例如带 Toolkit 参数），仍然忽略输入返回 canned reply。不允许 stub 出错（throws UnsupportedOperationException）—— 整个 stub 必须能让 ReActAgent.call() 流程跑完。

如果 `ChatResponse` 的构造方式不清楚，用 `codegraph_explore("ChatResponse builder")` 查。

- [ ] **Step 6: 写 TestStubModelProviderType**

Write `src/test/java/io/agentscope/builder/saton/agent/runtime/TestStubModelProviderType.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

/**
 * 测试专用 ModelProviderType。在 src/test/java 下，仅被 @SpringBootTest 启用上下文时
 * 自动扫到并注册进 ModelProviderTypeRegistry，type 名 "test-stub"。
 *
 * <p>副作用：所有 @SpringBootTest 的 GET /api/factories/model-types 返回结果会多出
 * 一个 "test-stub"。如有断言"5 个内置类型"的旧测试，需扩展为"至少包含这 5 个"或显式
 * 忽略 test-stub。M3 的 FactoriesControllerFlowTest 用了 assertEquals(Set.of(...)) —
 * 在 M4-3 实现时需把它改成 assertTrue(names.containsAll(Set.of(...))).
 */
@Component
public class TestStubModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "test-stub";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "test-stub",
                "Test Stub Model",
                "Returns canned response, used in tests only",
                JsonSchemaUtil.object().build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        return new TestStubModel();
    }
}
```

- [ ] **Step 7: 修 M3 的 FactoriesControllerFlowTest 适配 test-stub**

Edit `src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java`:

In `listAllFiveModelTypes`, change:
```java
assertEquals(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai"), names);
```
to:
```java
assertTrue(names.containsAll(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai")),
        "missing some builtin types; got " + names);
```

Same change in `ModelFactoryTest.allFiveBuiltinTypesRegistered` and `typesAreSortedAlphabetically` 中如果检查"全等于 5 个"或排序数组等于固定列表 —— 都改成"包含 5 个内置"。

> 这是技术债 —— 因为我们加了 test-stub。后续如果想"在某些测试隔离 test-stub"，可以用 `@ActiveProfiles` 给 stub 加 `@Profile("test")` 守卫。M4 不做这一步。

- [ ] **Step 8: 写 AgentRuntimeResolverTest**

Write `src/test/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolverTest.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.core.ReActAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AgentRuntimeResolverTest {

    @Autowired AgentDefinitionRepository agentRepo;
    @Autowired ModelProviderRepository modelRepo;
    @Autowired AgentRuntimeResolver resolver;

    Long agentId;
    Long modelId;

    @BeforeEach
    void setUp() {
        ModelProviderEntity mp = new ModelProviderEntity();
        mp.setOwnerId("admin");
        mp.setName("rrt-model-" + System.nanoTime());
        mp.setType("test-stub");
        mp.setPropsJson("{}");
        long now = System.currentTimeMillis();
        mp.setCreatedAt(now);
        mp.setUpdatedAt(now);
        modelRepo.save(mp);
        this.modelId = mp.getId();

        AgentDefinitionEntity a = new AgentDefinitionEntity();
        a.setOwnerId("admin");
        a.setAgentId("rrt-agent-" + System.nanoTime());
        a.setAgentType("react");
        a.setSysPrompt("hi");
        a.setDefaultModelProviderId(modelId);
        a.setMaxIters(5);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        agentRepo.save(a);
        this.agentId = a.getId();
    }

    @Test
    void resolveCachesByKey() {
        ReActAgent a1 = resolver.resolve(agentId, modelId);
        ReActAgent a2 = resolver.resolve(agentId, modelId);
        assertSame(a1, a2);
    }

    @Test
    void differentModelGivesDifferentAgent() {
        ModelProviderEntity mp2 = new ModelProviderEntity();
        mp2.setOwnerId("admin");
        mp2.setName("rrt-model2-" + System.nanoTime());
        mp2.setType("test-stub");
        mp2.setPropsJson("{}");
        long now = System.currentTimeMillis();
        mp2.setCreatedAt(now);
        mp2.setUpdatedAt(now);
        modelRepo.save(mp2);
        ReActAgent a1 = resolver.resolve(agentId, modelId);
        ReActAgent a2 = resolver.resolve(agentId, mp2.getId());
        assertNotSame(a1, a2);
    }

    @Test
    void invalidateByAgentClearsCache() {
        resolver.resolve(agentId, modelId);
        int before = resolver.cacheSize();
        resolver.invalidateByAgent(agentId);
        int after = resolver.cacheSize();
        assertEquals(before - 1, after);
    }

    @Test
    void invalidateByModelClearsCache() {
        resolver.resolve(agentId, modelId);
        int before = resolver.cacheSize();
        resolver.invalidateByModel(modelId);
        int after = resolver.cacheSize();
        assertEquals(before - 1, after);
    }
}
```

- [ ] **Step 9: 跑测试**

```powershell
mvn test -Dtest=AgentRuntimeResolverTest,FactoriesControllerFlowTest,ModelFactoryTest
```

Expected: all PASS. AgentRuntimeResolverTest 4 个；FactoriesControllerFlowTest 3 个；ModelFactoryTest 8 个。

If `TestStubModel` doesn't compile because Model interface signatures are different than guessed, fix them per codegraph exploration (see Step 5 note).

- [ ] **Step 10: 全量回归 + Commit**

```powershell
mvn test
git add src/
git commit -m "feat(agent/runtime): AgentBuildOrchestrator + AgentRuntimeResolver + test stub model

AgentService now invalidates runtime cache on update/delete."
```

---

## Task 4: ChatController + ChatService + 端到端 ChatFlowTest

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendResp.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatController.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/chat/ChatFlowTest.java`

- [ ] **Step 1: 写 DTOs**

Write `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendReq.java`:

```java
package io.agentscope.builder.saton.agent.chat.dto;

public record ChatSendReq(String message, Long overrideModelProviderId) {}
```

Write `src/main/java/io/agentscope/builder/saton/agent/chat/dto/ChatSendResp.java`:

```java
package io.agentscope.builder.saton.agent.chat.dto;

public record ChatSendResp(String reply, Long agentDefId, Long modelProviderIdUsed) {}
```

- [ ] **Step 2: 写 ChatService**

Write `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`:

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
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatService {

    /** 同步 send 最大等待时间。超时 = 抛 RuntimeException。 */
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(2);

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

        // 校验 override 模型也属于当前 owner
        if (modelRepo.findByIdAndOwnerId(effectiveModelId, me).isEmpty()) {
            throw new NotFoundException("model provider not found or not yours: " + effectiveModelId);
        }

        ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);

        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(req.message() == null ? "" : req.message()).build())
                .build();

        Msg reply = agent.call(userMsg).block(CALL_TIMEOUT);
        String text = extractText(reply);
        return new ChatSendResp(text, def.getId(), effectiveModelId);
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock t) {
                if (t.getText() != null) sb.append(t.getText());
            } else if (block instanceof ThinkingBlock th) {
                // 跳过思考块，不暴露给用户
                continue;
            }
        }
        return sb.toString();
    }
}
```

> If `Msg.builder()` / `MsgRole.USER` / `TextBlock.builder()` / `ContentBlock` API names differ, use `codegraph_explore("Msg builder TextBlock ContentBlock MsgRole")` to verify. The shape verified in M4 plan time: `Msg.builder().name(...).role(MsgRole.USER).content(TextBlock.builder().text(...).build()).build()`.

- [ ] **Step 3: 写 ChatController**

Write `src/main/java/io/agentscope/builder/saton/agent/chat/ChatController.java`:

```java
package io.agentscope.builder.saton.agent.chat;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendReq;
import io.agentscope.builder.saton.agent.chat.dto.ChatSendResp;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/agents")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/{id}/chat/send")
    public Mono<ChatSendResp> send(@PathVariable("id") Long id,
                                   @RequestBody ChatSendReq req,
                                   ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return service.send(id, req);
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        }).subscribeOn(Schedulers.boundedElastic());
        // ↑ 这次允许 subscribeOn —— ChatService.send 内部 .block() 会阻塞，必须挪出 Netty 线程。
        //    SaReactorSyncHolder.setContext 必须在 lambda 里执行（同一个 boundedElastic 线程）才有效。
    }
}
```

> 关键：`SaReactorSyncHolder.setContext(exchange)` 必须在 lambda 内部执行（即 boundedElastic 线程），而不是在 Mono.fromCallable 之前。lambda 是个闭包，捕获 exchange 引用，到执行时再绑定到当前线程的 ThreadLocal。这是为什么 sa-token 在 reactor + 阻塞 service 场景仍能正常工作。M1 把这条加进了 spec §12.4，本里程碑首次实战使用 subscribeOn + sa-token 组合。

- [ ] **Step 4: 写 ChatFlowTest**

Write `src/test/java/io/agentscope/builder/saton/agent/chat/ChatFlowTest.java`:

```java
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
                        "react", modelId, 3))
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
```

- [ ] **Step 5: 跑测试**

```powershell
mvn test -Dtest=ChatFlowTest
```

Expected: `Tests run: 4, Failures: 0`.

If `sendReturnsCannedReply` fails because ReActAgent.call() throws (e.g. NPE inside formatter when no proper Toolkit/system prompt), trace the stack — likely a TestStubModel signature mismatch (re-verify with codegraph). 

If the stub returns the canned reply but ReActAgent wraps it in extra processing that strips the text, inspect what `reply.getContent()` actually contains; you may need to fine-tune `extractText` in ChatService.

STOP and report BLOCKED if can't get it working in 2 attempts — implementing a fully working Model stub is non-trivial.

- [ ] **Step 6: Commit**

```powershell
git add src/
git commit -m "feat(agent/chat): ChatController + ChatService non-streaming end-to-end

Includes ChatFlowTest with stub model verifying full route from REST →
sa-token bind → ChatService → ReActAgent.call() → text extraction."
```

---

## Task 5: 全量回归 + smoke + tag

- [ ] **Step 1: 全量 mvn test**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn test
```

Expected: previous 52 + Task 1 (2) + Task 2 (4) + Task 3 (4) + Task 4 (4) = **66 tests PASS**.

捕获 aggregate 行。

- [ ] **Step 2: 手动 smoke —— 用 stub model 通过 REST 跑通**

启动后端：
```bash
cd "D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton" && mvn spring-boot:run 2>&1
```

> 注意：dev 模式下 test-stub type 不会被加载（因为它在 src/test/java），所以你必须用真实的 dashscope 等 type。或者切换到 test-stub —— **不可能**，dev 启动器不会扫 test classpath。

所以 smoke 走 dashscope，需要一个真实 API key：

```powershell
# Login
$resp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}'
$token = $resp.token

# 加一个真实 DashScope 模型（替换 sk-XXX 为真 key）
$mp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/models `
  -Headers @{ satoken=$token; "Content-Type"="application/json" } `
  -Body '{"name":"smoke-dashscope","type":"dashscope","props":{"apiKey":"sk-REPLACE-WITH-REAL-KEY","modelName":"qwen-max"}}'
$mp

# 加一个 agent
$ag = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/agents `
  -Headers @{ satoken=$token; "Content-Type"="application/json" } `
  -Body ('{"agentId":"smoke-agent","name":"smoke","sysPrompt":"你是一个简短回答的助手","agentType":"react","defaultModelProviderId":' + $mp.id + ',"maxIters":3}')
$ag

# Chat！
Invoke-RestMethod -Method Post -Uri ("http://localhost:8080/api/agents/" + $ag.id + "/chat/send") `
  -Headers @{ satoken=$token; "Content-Type"="application/json" } `
  -Body '{"message":"用一句话介绍 AgentScope"}'
```

Expected: 返回 `{ reply: "...", agentDefId: ..., modelProviderIdUsed: ... }`，reply 是 LLM 真实回答（如"AgentScope 是阿里通义实验室开源的多智能体框架..."）。

**如果没有真实 key**，跳过此 smoke，仅依赖 `ChatFlowTest` 的 stub model 验证。Report 中说明。

- [ ] **Step 3: 验证 M3 / M2 接口未回归**

`GET /api/factories/model-types` 应返回 5 个内置 + 0 个 test-stub（dev 模式不加载）。
`GET /api/models` 应正常列出。

- [ ] **Step 4: Tag**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
git log --oneline | Select-Object -First 12
git tag m4-chat-end-to-end-done
git tag --list
```

Expected: 看到所有四个 tag。

---

## Self-Review 检查表

- [x] **Spec coverage**：实现 spec §9 M4 全部 —— AgentDefinition CRUD + AgentBuildOrchestrator(仅 model) + ChatController 非流式 + ReActAgent 集成（HarnessAgent 推 M6）。
- [x] **No placeholders**：所有 code block 完整。**唯一一处显式留给 subagent 探索**：`TestStubModel` 的 Model 接口实现 —— 因为接口签名可能不完全跟我假设的对齐，要求 subagent 用 codegraph 实地验证。
- [x] **Type consistency**：跨任务命名一致 `AgentDefinitionEntity / AgentService / AgentBuildOrchestrator / AgentRuntimeResolver / ChatService / ChatController / RuntimeKey`。
- [x] **踩坑预防**：所有 SaReactorSyncHolder 包裹 / @PathVariable("xxx") / @DataJpaTest 正确 import。**新增 subscribeOn + sa-token 组合**（ChatController），spec §12.4 已有相关说明。
- [x] **测试粒度**：每层都有测试。Repository 单测 / Service 集成（通过 controller flow）/ Resolver 单测 / 端到端 ChatFlow。

---

## M4 完成定义

1. `mvn test` 全部 PASS（~66 个）
2. `POST /api/agents` 创建 agent 成功
3. `POST /api/agents/{id}/chat/send` body `{message, overrideModelProviderId?}` 用 stub model 返回 `{reply, agentDefId, modelProviderIdUsed}`
4. 切换 overrideModelProviderId 走的是新模型（VO.modelProviderIdUsed 反映）
5. Agent update / delete 触发 runtime cache invalidate
6. M2 + M3 全部接口未回归
7. （可选）真实 DashScope key 跑通一条端到端 LLM 回答
8. tag `m4-chat-end-to-end-done` 已打

M4 完成 = 第一次真正"做出了能聊的智能体"。后续 M5 加流式 + 工具。
