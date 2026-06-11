# M7 切换 HarnessAgent + SkillFactory + HookFactory + SubAgent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 agent 运行时从轻量 `ReActAgent` 切换到 `HarnessAgent`，并接入 spec §4 剩余 3 个工厂：
1. **HarnessAgent 替换**：`AgentBuildOrchestrator` 输出 `HarnessAgent` 而不是 `ReActAgent`；保留 ChatService / SessionService / WorkspaceService 现有签名（HarnessAgent 的 `call(Msg, ctx)` / `streamEvents(Msg, ctx)` 接口和 ReActAgent 一致）
2. **SkillFactory**（2.0 推荐路线 `AgentSkillRepository`）：内置 2 个 type —— `local` (FileSystemSkillRepository，指向 workspace `skills/`) + `git`（反射加载 GitSkillRepository，无 dep 时优雅降级）
3. **HookFactory**：内置 2 个 type —— `logging`（SLF4J 记录 PreCall/PostCall）+ `audit-jsonl`（写 `<workspace>/activity/activity.jsonl`，为 M10 audit 铺路）
4. **SubagentTool**（harness 原生 `subagentFactory(name, Function<String,Agent>)`）：agent 把 `subagent_refs_json` 里的 agent_definition.id 列表通过递归 `AgentBuildOrchestrator.build(...)` 拼起来注入 HarnessAgent

**完成定义：**
1. `HarnessAgentSwitchTest` — 现有 M4-M6 测试全部仍绿，证明 HarnessAgent 兼容当前 chat / session / workspace 所有功能
2. `SkillFactoryTest` — `local` 类型挂 workspace skills 目录；`git` 类型在 dep 缺失时优雅降级（抛 IllegalStateException + 日志 WARN）
3. `HookFactoryTest` — `logging` / `audit-jsonl` 都能实例化；`audit-jsonl` 写文件验证
4. `SubagentTreeTest` — agent A 引用 agent B (subagentRefs=[B.id])，build A 时 HarnessAgent 包含 B 的子 agent 工厂，整体 build 成功

**Architecture:**
- pom 新增：`agentscope-harness:2.0.0-RC2`（核心切换）；`agentscope-extensions-skill-git-repository` 不显式声明，运行时反射加载
- **AgentBuildOrchestrator.build(...) 返回类型从 `ReActAgent` 改成 `HarnessAgent`**：影响 `AgentRuntimeResolver` 缓存值类型、`ChatService` 调用类型
- HarnessAgent.Builder 额外能用：`.workspace(Path)` `.skillRepository(...)` `.subagentFactory(name, fn)` `.hook(...)` `.stateStore(...).defaultSessionId(...)`
- SkillFactory 模式跟 ToolFactory 一样：SPI `SkillRepoType extends Provider` → `SkillRepoTypeRegistry` → `SkillFactory.instantiate(type, props, workspaceRoot) → AgentSkillRepository`
- HookFactory 同样模板：SPI `HookType extends Provider`，`HookFactory.instantiate(type, props, activityDir) → Hook`
- Subagent 走 `subagentFactory(name, Function<String, Agent>)` —— 注入回调：name = 子 agent_id → 查 agent_definition by (ownerId, agent_id) → 递归构建一个 HarnessAgent

**关键决策：**
- **不引入 SubAgentTool (core 原生 ToolType)** —— HarnessAgent 的 `subagentFactory` 是更原生的接入点
- **subagent_refs_json 格式**：`["agent_id_1", "agent_id_2"]` 简单字符串列表，每个值是另一 agent 的 `agent_id`（同 owner）
- **skill_refs_json M7 暂不消费** —— 留 M8 marketplace install 才有意义
- **skill_repositories_json 格式**：`[{type:"local",props:{path:"skills"}}, {type:"git",props:{remoteUrl:"...",branch:"main"}}]`
- **hook_specs_json 格式**：`[{type:"logging",props:{}}, {type:"audit-jsonl",props:{logDir:"activity"}}]`
- **audit-jsonl 写哪里**：复用 M6 的 `<workspaceRoot>/<ownerId>/<agentId>/activity.jsonl`（per agent per owner），跟 workspace files 同层
- **agent_definition.workspacePath 列暂不让用户改** —— 强制按 ownerId/agentId 命名（同 M6 决策）
- **AgentBuildOrchestrator 现在需要 ownerId 参数** —— workspace 路径要 ownerId。新签名 `build(def, model, ownerId)`。AgentRuntimeResolver.resolve 也加 ownerId

**Tech Stack:** +`agentscope-harness:2.0.0-RC2`（已存在本地仓库）；可选反射加载 `agentscope-extensions-skill-git-repository`

**踩坑预防（spec §12.1-12.17）**：
- §12.4: sa-token + WebFlux 不变（HarnessAgent 跟 ReActAgent 异步行为一致）
- §12.14: `Flux.defer` 内 sa-token 丢失 —— ChatService 仍 capture me before defer
- §12.16: SB4 双 public ctor 必须显式 @Autowired
- §12.17: IllegalArgumentException → 400 兜底已在 AuthExceptionHandler，新代码抛 IAE 自动走 400
- **新预判**：HarnessAgent 的 `.workspace(Path)` 不一定自动 mkdir —— plan 让 orchestrator 先 Files.createDirectories(...)

---

## File Structure

```
agentscope-builder-saton/
├── pom.xml                                                    # MODIFY: 加 agentscope-harness dep
└── src/main/java/io/agentscope/builder/saton/
    ├── agent/
    │   ├── SkillRepoSpec.java                                 # NEW: record(String type, Map<String,Object> props)
    │   ├── HookSpec.java                                      # NEW: record(String type, Map<String,Object> props)
    │   ├── runtime/
    │   │   ├── AgentBuildOrchestrator.java                    # MODIFY: 返回 HarnessAgent + 接 ownerId + 接 skill/hook/subagent JSON
    │   │   └── AgentRuntimeResolver.java                      # MODIFY: cache HarnessAgent + resolve 接 ownerId
    │   └── chat/
    │       └── ChatService.java                               # MODIFY: 引用 HarnessAgent 类型；call/stream 签名不变
    └── factory/
        ├── skill/                                             # NEW 包
        │   ├── SkillRepoType.java                             # SPI extends Provider
        │   ├── SkillRepoTypeRegistry.java                     # ProviderRegistry<SkillRepoType>
        │   ├── SkillFactory.java
        │   └── impl/
        │       ├── LocalSkillRepoType.java                    # FileSystemSkillRepository(workspaceRoot.resolve(path))
        │       └── GitSkillRepoType.java                      # 反射加载 GitSkillRepository
        ├── hook/                                              # NEW 包
        │   ├── HookType.java                                  # SPI extends Provider
        │   ├── HookTypeRegistry.java
        │   ├── HookFactory.java
        │   └── impl/
        │       ├── LoggingHookType.java                       # SLF4J pre/post call log
        │       └── AuditJsonlHookType.java                    # 写 <activityDir>/activity.jsonl
        └── api/
            └── FactoriesController.java                       # MODIFY: 加 GET /skill-repo-types + /hook-types

src/test/java/io/agentscope/builder/saton/
├── factory/
│   ├── skill/
│   │   ├── SkillFactoryTest.java                              # NEW
│   │   └── impl/
│   │       ├── LocalSkillRepoTypeTest.java                    # NEW
│   │       └── GitSkillRepoTypeTest.java                      # NEW (反射降级)
│   ├── hook/
│   │   ├── HookFactoryTest.java                               # NEW
│   │   └── impl/
│   │       └── AuditJsonlHookTypeTest.java                    # NEW
│   └── api/
│       └── FactoriesControllerFlowTest.java                   # MODIFY: 加 skill-repo-types / hook-types 断言
└── agent/
    └── runtime/
        ├── SubagentTreeTest.java                              # NEW: 双层 agent 构建
        └── HarnessAgentSwitchTest.java                        # NEW: HarnessAgent build 烟测
```

**职责说明：**

- `SkillRepoType` —— `Provider` 子接口，增加 `AgentSkillRepository instantiate(Map<String,Object> props, Path workspaceRoot)`
- `SkillFactory.instantiate(type, props, workspaceRoot) → AgentSkillRepository`
- `LocalSkillRepoType` —— props 读 `path` 字段（默认 `"skills"`）；workspaceRoot.resolve(path) → `new FileSystemSkillRepository(path)`
- `GitSkillRepoType` —— props 读 `remoteUrl` (必填) `branch` (可选) `localPath` (可选 workspaceRoot.resolve(...))；用 `Class.forName("io.agentscope.core.skill.repository.GitSkillRepository")` 反射构造
- `HookType` —— `Provider` 子接口，增加 `Hook instantiate(Map<String,Object> props, Path activityDir)`
- `LoggingHookType` —— 匿名 Hook，PreCallEvent / PostCallEvent 输出 SLF4J INFO
- `AuditJsonlHookType` —— props 读 `logDir`（默认 `"activity"`）；PostCallEvent 把 final msg 序列化追加到 `activityDir/activity.jsonl`
- `SkillRepoSpec` / `HookSpec` —— `record(String type, Map<String,Object> props)`
- `AgentBuildOrchestrator.build(def, model, ownerId)` 新签名：
  - 解析 `skillRepositoriesJson` → `List<SkillRepoSpec>` → `skillFactory.instantiate(..)` × N → `.skillRepositories(list)`
  - 解析 `hookSpecsJson` → `List<HookSpec>` → `hookFactory.instantiate(..)` × N → `.hook(hook)` × N
  - 解析 `subagentRefsJson` → `List<String>` agentIds → 每个用 `.subagentFactory(name, n -> buildChild(n, ownerId))`
  - workspaceResolver.agentRoot(ownerId, def.id) 传给 `.workspace(...)`（mkdir 后）
- `AgentRuntimeResolver.resolve(agentDefId, modelId, ownerId)` —— 多接一个 ownerId 参数；cache key 仍是 `(defId, modelId)`
- `ChatService.send/stream` 调用 `runtimeResolver.resolve(..)` 时多传 me

---

## Task 1: HarnessAgent dep + orchestrator/resolver/chat 切换

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolver.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/agent/runtime/HarnessAgentSwitchTest.java`

**目标**：把核心运行时从 `ReActAgent` 替换为 `HarnessAgent`，**不改变现有 ChatService / SessionService / WorkspaceService 公开行为**。M4-M6 的 118 个测试必须仍绿。

- [ ] **Step 1: 加 dependency 到 pom.xml**

在 `pom.xml` 中，找到 `agentscope-core` 的 dependency 块，**在它之后**追加：

```xml
        <!-- M7: HarnessAgent for skill repos + subagent factory + workspace context -->
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-harness</artifactId>
            <version>2.0.0-RC2</version>
        </dependency>
```

- [ ] **Step 2: 写"HarnessAgent build 烟测"测试**

Create `src/test/java/io/agentscope/builder/saton/agent/runtime/HarnessAgentSwitchTest.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HarnessAgentSwitchTest {

    @Autowired AgentBuildOrchestrator orchestrator;

    @Test
    void buildReturnsHarnessAgent() {
        AgentDefinitionEntity def = new AgentDefinitionEntity();
        def.setId(1L);
        def.setAgentId("switch-test-" + System.nanoTime());
        def.setName("switch");
        def.setSysPrompt("hi");
        def.setAgentType("react");
        def.setMaxIters(3);

        ModelProviderEntity model = new ModelProviderEntity();
        model.setId(1L);
        model.setOwnerId("admin");
        model.setName("stub");
        model.setType("test-stub");
        model.setPropsJson("{}");

        HarnessAgent agent = orchestrator.build(def, model, "admin");
        assertNotNull(agent, "should return a HarnessAgent");
    }
}
```

- [ ] **Step 3: 跑测试看 fail**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=HarnessAgentSwitchTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: COMPILATION FAIL — `HarnessAgent` 类不在 classpath 上，或者 `orchestrator.build(...)` 是 2 参不是 3 参

- [ ] **Step 4: 改 AgentBuildOrchestrator —— 返回 HarnessAgent + 接 ownerId + 用 WorkspacePathResolver**

整文件替换 `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 用 agent_definition 行 + model_provider 行 + ownerId 装配一个真实可调的 {@link HarnessAgent}。
 *
 * <p>M7 起：返回类型从 ReActAgent 切到 HarnessAgent。HarnessAgent 接口和 ReActAgent 一致
 * （都有 call / streamEvents），ChatService 改个 import 即可；HarnessAgent 多出来的
 * skillRepository / subagentFactory / workspace context 等 M7 后续 task 接入。
 *
 * <p>workspace 路径由 {@link WorkspacePathResolver#agentRoot(String, Long)} 给出，
 * orchestrator 负责 mkdir 后传给 HarnessAgent.Builder.workspace(...)。
 */
@Component
public class AgentBuildOrchestrator {

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;
    private final AgentStateStore stateStore;
    private final WorkspacePathResolver workspaceResolver;

    public AgentBuildOrchestrator(ModelFactory modelFactory,
                                  ToolFactory toolFactory,
                                  AgentStateStore stateStore,
                                  WorkspacePathResolver workspaceResolver) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.stateStore = stateStore;
        this.workspaceResolver = workspaceResolver;
    }

    public HarnessAgent build(AgentDefinitionEntity def,
                              ModelProviderEntity model,
                              String ownerId) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            Object tool = toolFactory.instantiate(spec.type(), spec.props());
            toolkit.registerTool(tool);
        }

        Path workspace = workspaceResolver.agentRoot(ownerId, def.getId());
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to mkdir workspace: " + workspace, e);
        }

        return HarnessAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .stateStore(stateStore)
                .defaultSessionId("agent_" + def.getId() + "_default")
                .workspace(workspace)
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

- [ ] **Step 5: 改 AgentRuntimeResolver —— cache 类型 + resolve 接 ownerId**

整文件替换 `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolver.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (agentDefId, modelProviderId) → {@link HarnessAgent} 缓存。
 *
 * <p>M7 起：缓存值类型从 ReActAgent 切到 HarnessAgent。resolve 多接一个 ownerId 参数
 * （HarnessAgent 需要 workspace 路径，路径取决于 ownerId）；cache key 仍是 (defId, modelId)，
 * 因为 agent 本身已经隐含 ownership。
 */
@Component
public class AgentRuntimeResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeResolver.class);

    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;
    private final AgentBuildOrchestrator orchestrator;

    private final Map<RuntimeKey, HarnessAgent> cache = new ConcurrentHashMap<>();
    private final Map<Long, Set<RuntimeKey>> byAgent = new ConcurrentHashMap<>();
    private final Map<Long, Set<RuntimeKey>> byModel = new ConcurrentHashMap<>();

    public AgentRuntimeResolver(AgentDefinitionRepository agentRepo,
                                ModelProviderRepository modelRepo,
                                AgentBuildOrchestrator orchestrator) {
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
        this.orchestrator = orchestrator;
    }

    public HarnessAgent resolve(Long agentDefId, Long modelProviderId, String ownerId) {
        RuntimeKey key = new RuntimeKey(agentDefId, modelProviderId);
        return cache.computeIfAbsent(key, k -> {
            AgentDefinitionEntity def = agentRepo.findById(agentDefId)
                    .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
            ModelProviderEntity model = modelRepo.findById(modelProviderId)
                    .orElseThrow(() -> new NotFoundException("model provider not found: " + modelProviderId));
            HarnessAgent built = orchestrator.build(def, model, ownerId);
            byAgent.computeIfAbsent(agentDefId, x -> ConcurrentHashMap.newKeySet()).add(k);
            byModel.computeIfAbsent(modelProviderId, x -> ConcurrentHashMap.newKeySet()).add(k);
            log.debug("built HarnessAgent for {}/{} (owner={})", agentDefId, modelProviderId, ownerId);
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

    int cacheSize() {
        return cache.size();
    }
}
```

- [ ] **Step 6: 改 ChatService —— 引用 HarnessAgent 类型，调用 resolve 时传 me**

Edit `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`:

**Find** import:
```java
import io.agentscope.core.ReActAgent;
```
**Replace** with:
```java
import io.agentscope.harness.agent.HarnessAgent;
```

**Find** in `send` method:
```java
        ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);
```
**Replace** with:
```java
        HarnessAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId, me);
```

**Find** in `stream` method (在 Flux.defer 内):
```java
            ReActAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId);
```
**Replace** with:
```java
            HarnessAgent agent = runtimeResolver.resolve(def.getId(), effectiveModelId, me);
```

（注意 `me` 已经在两个方法的开头 capture 好了 —— spec §12.14。）

- [ ] **Step 7: 跑全量回归看绿**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 119 PASS（118 + 1 新 HarnessAgentSwitchTest）

如果有 fail，最可能是：
- `AgentRuntimeResolverTest`（M4）—— 检查它有没有调 `resolve(defId, modelId)` 2 参版本，如果有，更新成 3 参传一个 dummy `"admin"`
- HarnessAgentSwitchTest 自身 fail（HarnessAgent.builder() 抛异常）—— 看错误信息是否要补 Builder 字段。常见：缺 workspace 路径（plan 已传）、缺 stateStore（plan 已传）

- [ ] **Step 8: Commit**

```powershell
git add pom.xml `
        src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java `
        src/main/java/io/agentscope/builder/saton/agent/runtime/AgentRuntimeResolver.java `
        src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java `
        src/test/java/io/agentscope/builder/saton/agent/runtime/HarnessAgentSwitchTest.java
git commit -m "feat(m7): switch ReActAgent -> HarnessAgent + ownerId-aware workspace"
```

---

## Task 2: SkillFactory + LocalSkillRepoType + GitSkillRepoType (反射)

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/factory/skill/SkillRepoType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/skill/SkillRepoTypeRegistry.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/skill/SkillFactory.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/skill/impl/LocalSkillRepoType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/skill/impl/GitSkillRepoType.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/factory/skill/SkillFactoryTest.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/factory/skill/impl/LocalSkillRepoTypeTest.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/factory/skill/impl/GitSkillRepoTypeTest.java`

**目标**：复用 M3-M5 的 ProviderRegistry 模板，新增 SkillFactory。本 task 不接 AgentBuildOrchestrator —— 仅独立工厂 + 2 内置类型 + 单测。Task 4 才接 orchestrator。

- [ ] **Step 1: 写 SkillRepoType SPI 接口**

Create `src/main/java/io/agentscope/builder/saton/factory/skill/SkillRepoType.java`:

```java
package io.agentscope.builder.saton.factory.skill;

import io.agentscope.builder.saton.factory.core.Provider;
import io.agentscope.core.skill.repository.AgentSkillRepository;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for skill repository providers. Each implementation declares a unique {@code type()}
 * string used in {@code skill_repositories_json} entries.
 *
 * <p>The factory passes {@code workspaceRoot} so local repos can resolve relative paths
 * to the agent's workspace.
 */
public interface SkillRepoType extends Provider {

    /** Instantiate an {@link AgentSkillRepository} from props + agent workspace root. */
    AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot);
}
```

- [ ] **Step 2: 写 SkillRepoTypeRegistry**

Create `src/main/java/io/agentscope/builder/saton/factory/skill/SkillRepoTypeRegistry.java`:

```java
package io.agentscope.builder.saton.factory.skill;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring-collected registry of all {@link SkillRepoType} beans. */
@Component
public class SkillRepoTypeRegistry extends ProviderRegistry<SkillRepoType> {

    public SkillRepoTypeRegistry(List<SkillRepoType> types) {
        super(types);
    }
}
```

- [ ] **Step 3: 写 SkillFactory 门面**

Create `src/main/java/io/agentscope/builder/saton/factory/skill/SkillFactory.java`:

```java
package io.agentscope.builder.saton.factory.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * Facade for instantiating {@link AgentSkillRepository} by type string + props + workspaceRoot.
 */
@Component
public class SkillFactory {

    private final SkillRepoTypeRegistry registry;

    public SkillFactory(SkillRepoTypeRegistry registry) {
        this.registry = registry;
    }

    public AgentSkillRepository instantiate(String type,
                                            Map<String, Object> props,
                                            Path workspaceRoot) {
        SkillRepoType impl = registry.get(type);
        if (impl == null) {
            throw new IllegalArgumentException("unknown skill repo type: " + type);
        }
        return impl.instantiate(props == null ? Map.of() : props, workspaceRoot);
    }
}
```

- [ ] **Step 4: 写 LocalSkillRepoType (FileSystemSkillRepository)**

Create `src/main/java/io/agentscope/builder/saton/factory/skill/impl/LocalSkillRepoType.java`:

```java
package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.skill.SkillRepoType;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-filesystem skill repository under {@code <workspaceRoot>/<path>}. {@code path} defaults
 * to {@code "skills"} when omitted from props.
 */
@Component
public class LocalSkillRepoType implements SkillRepoType {

    private static final String DEFAULT_PATH = "skills";

    @Override
    public String type() {
        return "local";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> pathField = new LinkedHashMap<>();
        pathField.put("type", "string");
        pathField.put("description", "workspace-relative directory (default: skills)");
        properties.put("path", pathField);
        schema.put("properties", properties);
        return new TypeMeta(type(), "本地 skills 目录", "挂载 workspace 子目录作为 skill overlay", schema);
    }

    @Override
    public AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot) {
        String relPath = optionalString(props, "path", DEFAULT_PATH);
        Path dir = workspaceRoot.resolve(relPath).normalize();
        return new FileSystemSkillRepository(dir);
    }

    private static String optionalString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object v = props.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
```

- [ ] **Step 5: 写 GitSkillRepoType (反射加载)**

Create `src/main/java/io/agentscope/builder/saton/factory/skill/impl/GitSkillRepoType.java`:

```java
package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.skill.SkillRepoType;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Git-backed skill repository. Loaded via reflection so the project can run without the
 * optional {@code agentscope-extensions-skill-git-repository} dependency on classpath.
 *
 * <p>Missing dependency surfaces as {@link IllegalStateException} at instantiate time —
 * 由 spec §12.17 加的 IllegalArgumentException 处理映射到 HTTP 400 不适用；这是后端
 * 配置错误，500 比 400 更对。
 */
@Component
public class GitSkillRepoType implements SkillRepoType {

    private static final Logger log = LoggerFactory.getLogger(GitSkillRepoType.class);
    private static final String GIT_REPO_CLASS = "io.agentscope.core.skill.repository.GitSkillRepository";

    @Override
    public String type() {
        return "git";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> remoteUrl = new LinkedHashMap<>();
        remoteUrl.put("type", "string");
        remoteUrl.put("description", "git 仓库 URL");
        properties.put("remoteUrl", remoteUrl);
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put("type", "string");
        branch.put("description", "分支名（可选，默认 main）");
        properties.put("branch", branch);
        Map<String, Object> localPath = new LinkedHashMap<>();
        localPath.put("type", "string");
        localPath.put("description", "本地缓存路径（workspace-relative，可选）");
        properties.put("localPath", localPath);
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("remoteUrl"));
        return new TypeMeta(type(), "Git 仓库 skills", "从 git 拉 skill 仓库挂为 overlay（需 agentscope-extensions-skill-git-repository dep）", schema);
    }

    @Override
    public AgentSkillRepository instantiate(Map<String, Object> props, Path workspaceRoot) {
        String remoteUrl = stringProp(props, "remoteUrl");
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("git skill repo requires 'remoteUrl'");
        }
        String localPathStr = stringProp(props, "localPath");
        Path localPath = (localPathStr != null && !localPathStr.isBlank())
                ? workspaceRoot.resolve(localPathStr).normalize()
                : null;

        try {
            Class<?> cls = Class.forName(GIT_REPO_CLASS);
            // single-arg (remoteUrl) ctor 一定有；如果 localPath 传了，找 (String, Path) 重载
            if (localPath != null) {
                Constructor<?> ctor = cls.getConstructor(String.class, Path.class);
                return (AgentSkillRepository) ctor.newInstance(remoteUrl, localPath);
            } else {
                Constructor<?> ctor = cls.getConstructor(String.class);
                return (AgentSkillRepository) ctor.newInstance(remoteUrl);
            }
        } catch (ClassNotFoundException e) {
            log.warn("GitSkillRepository class not on classpath; add agentscope-extensions-skill-git-repository dep");
            throw new IllegalStateException("git skill repository support not installed (add agentscope-extensions-skill-git-repository dependency)", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to construct GitSkillRepository: " + e.getMessage(), e);
        }
    }

    private static String stringProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object v = props.get(key);
        return v instanceof String s ? s : null;
    }
}
```

- [ ] **Step 6: 写 LocalSkillRepoTypeTest**

Create `src/test/java/io/agentscope/builder/saton/factory/skill/impl/LocalSkillRepoTypeTest.java`:

```java
package io.agentscope.builder.saton.factory.skill.impl;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalSkillRepoTypeTest {

    @TempDir Path workspaceRoot;

    @Test
    void defaultPathIsSkillsSubdir() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("skills"));
        LocalSkillRepoType type = new LocalSkillRepoType();
        AgentSkillRepository repo = type.instantiate(Map.of(), workspaceRoot);
        assertTrue(repo instanceof FileSystemSkillRepository);
    }

    @Test
    void customPathIsResolvedAgainstWorkspaceRoot() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("my-skills"));
        LocalSkillRepoType type = new LocalSkillRepoType();
        AgentSkillRepository repo = type.instantiate(Map.of("path", "my-skills"), workspaceRoot);
        assertTrue(repo instanceof FileSystemSkillRepository);
    }

    @Test
    void typeIsLocal() {
        assertEquals("local", new LocalSkillRepoType().type());
    }

    @Test
    void metaIncludesPathSchema() {
        var meta = new LocalSkillRepoType().meta();
        assertEquals("local", meta.type());
        assertNotNull(meta.schema());
    }
}
```

- [ ] **Step 7: 写 GitSkillRepoTypeTest（验证反射降级行为）**

Create `src/test/java/io/agentscope/builder/saton/factory/skill/impl/GitSkillRepoTypeTest.java`:

```java
package io.agentscope.builder.saton.factory.skill.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitSkillRepoTypeTest {

    @TempDir Path workspaceRoot;

    @Test
    void typeIsGit() {
        assertEquals("git", new GitSkillRepoType().type());
    }

    @Test
    void requiresRemoteUrl() {
        GitSkillRepoType type = new GitSkillRepoType();
        assertThrows(IllegalArgumentException.class,
                () -> type.instantiate(Map.of(), workspaceRoot));
    }

    @Test
    void missingClasspathThrowsIllegalStateWithHelpfulMessage() {
        // GitSkillRepository class isn't on the test classpath (agentscope-extensions-skill-git-repository
        // is not added as a dep). Verify graceful degradation.
        GitSkillRepoType type = new GitSkillRepoType();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> type.instantiate(Map.of("remoteUrl", "https://example.com/x.git"), workspaceRoot));
        assertTrue(ex.getMessage().contains("git-repository"),
                "error should mention the missing dep; got: " + ex.getMessage());
    }
}
```

- [ ] **Step 8: 写 SkillFactoryTest**

Create `src/test/java/io/agentscope/builder/saton/factory/skill/SkillFactoryTest.java`:

```java
package io.agentscope.builder.saton.factory.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkillFactoryTest {

    @Autowired SkillFactory factory;

    @TempDir Path workspaceRoot;

    @Test
    void instantiateLocal() throws Exception {
        Files.createDirectories(workspaceRoot.resolve("skills"));
        AgentSkillRepository repo = factory.instantiate("local", Map.of(), workspaceRoot);
        assertNotNull(repo);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.instantiate("never-existed", Map.of(), workspaceRoot));
    }
}
```

- [ ] **Step 9: 跑所有 skill 测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test "-Dtest=SkillFactoryTest,LocalSkillRepoTypeTest,GitSkillRepoTypeTest" -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: PASS — 4 + 3 + 2 = 9 tests 全绿

- [ ] **Step 10: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 128 PASS（119 + 9）

- [ ] **Step 11: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/factory/skill/ `
        src/test/java/io/agentscope/builder/saton/factory/skill/
git commit -m "feat(m7): SkillFactory + local/git SkillRepoType (git via reflection)"
```

---

## Task 3: HookFactory + LoggingHookType + AuditJsonlHookType

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/factory/hook/HookType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/hook/HookTypeRegistry.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/hook/HookFactory.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/hook/impl/LoggingHookType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/hook/impl/AuditJsonlHookType.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/factory/hook/HookFactoryTest.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/factory/hook/impl/AuditJsonlHookTypeTest.java`

**目标**：复用 ProviderRegistry 模板，建立 HookFactory + 2 个内置 hook 类型。本 task 仅工厂 + 单测，不接 orchestrator（Task 4 接）。

注意：`io.agentscope.core.hook.Hook` 类标了 `@Deprecated(since = "2.0.0")` 但仍可用，HarnessAgent.Builder.hook(...) 也还接受。我们沿用 Hook —— 当 agentscope-core 真的删除时再迁移到 MiddlewareBase（中型重构，留给后期）。

- [ ] **Step 1: 写 HookType SPI**

Create `src/main/java/io/agentscope/builder/saton/factory/hook/HookType.java`:

```java
package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.factory.core.Provider;
import io.agentscope.core.hook.Hook;

import java.nio.file.Path;
import java.util.Map;

/**
 * SPI for hook providers. Each implementation declares a unique {@code type()} string used in
 * {@code hook_specs_json} entries.
 *
 * <p>{@code activityDir} is passed so hooks that write to disk (e.g. audit-jsonl) can target
 * a per-agent activity directory without needing global config.
 */
public interface HookType extends Provider {

    /** Instantiate a {@link Hook} from props + agent activity directory. */
    @SuppressWarnings("deprecation") // Hook is deprecated in core but still supported by HarnessAgent.Builder
    Hook instantiate(Map<String, Object> props, Path activityDir);
}
```

- [ ] **Step 2: 写 HookTypeRegistry**

Create `src/main/java/io/agentscope/builder/saton/factory/hook/HookTypeRegistry.java`:

```java
package io.agentscope.builder.saton.factory.hook;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring-collected registry of all {@link HookType} beans. */
@Component
public class HookTypeRegistry extends ProviderRegistry<HookType> {

    public HookTypeRegistry(List<HookType> types) {
        super(types);
    }
}
```

- [ ] **Step 3: 写 HookFactory**

Create `src/main/java/io/agentscope/builder/saton/factory/hook/HookFactory.java`:

```java
package io.agentscope.builder.saton.factory.hook;

import io.agentscope.core.hook.Hook;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * Facade for instantiating {@link Hook} by type string + props + activityDir.
 */
@Component
@SuppressWarnings("deprecation")
public class HookFactory {

    private final HookTypeRegistry registry;

    public HookFactory(HookTypeRegistry registry) {
        this.registry = registry;
    }

    public Hook instantiate(String type, Map<String, Object> props, Path activityDir) {
        HookType impl = registry.get(type);
        if (impl == null) {
            throw new IllegalArgumentException("unknown hook type: " + type);
        }
        return impl.instantiate(props == null ? Map.of() : props, activityDir);
    }
}
```

- [ ] **Step 4: 写 LoggingHookType**

Create `src/main/java/io/agentscope/builder/saton/factory/hook/impl/LoggingHookType.java`:

```java
package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.hook.HookType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLF4J pre/post call logging hook. Useful for debugging agent execution.
 */
@Component
@SuppressWarnings("deprecation")
public class LoggingHookType implements HookType {

    private static final Logger AGENT_LOG = LoggerFactory.getLogger("io.agentscope.builder.saton.agent");

    @Override
    public String type() {
        return "logging";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return new TypeMeta(type(), "SLF4J 日志", "PreCall / PostCall 输出 INFO 日志（无参）", schema);
    }

    @Override
    public Hook instantiate(Map<String, Object> props, Path activityDir) {
        return new Hook() {
            @Override
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PreCallEvent pre) {
                    AGENT_LOG.info("[PreCall] agent={} inputCount={}",
                            pre.getAgent() != null ? pre.getAgent().getName() : "?",
                            pre.getInputMessages() != null ? pre.getInputMessages().size() : 0);
                } else if (event instanceof PostCallEvent post) {
                    AGENT_LOG.info("[PostCall] agent={} finalMsgRole={}",
                            post.getAgent() != null ? post.getAgent().getName() : "?",
                            post.getFinalMessage() != null ? post.getFinalMessage().getRole() : "?");
                }
                return Mono.just(event);
            }
        };
    }
}
```

- [ ] **Step 5: 写 AuditJsonlHookType**

Create `src/main/java/io/agentscope/builder/saton/factory/hook/impl/AuditJsonlHookType.java`:

```java
package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.hook.HookType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only JSONL audit hook. Each PostCallEvent's final message is serialized as one line
 * to {@code <activityDir>/<logDir>/activity.jsonl}; {@code logDir} defaults to {@code "activity"}.
 *
 * <p>Designed for M10 audit workflow — UI lists this file via /api/agents/{id}/activity.
 */
@Component
@SuppressWarnings("deprecation")
public class AuditJsonlHookType implements HookType {

    private static final Logger log = LoggerFactory.getLogger(AuditJsonlHookType.class);
    private static final String DEFAULT_LOG_DIR = "activity";
    private static final String JSONL_FILE = "activity.jsonl";

    @Override
    public String type() {
        return "audit-jsonl";
    }

    @Override
    public TypeMeta meta() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> logDir = new LinkedHashMap<>();
        logDir.put("type", "string");
        logDir.put("description", "log 子目录（相对 activityDir，默认 activity）");
        properties.put("logDir", logDir);
        schema.put("properties", properties);
        return new TypeMeta(type(), "JSONL 审计", "PostCall 把 final msg 追加到 activity.jsonl", schema);
    }

    @Override
    public Hook instantiate(Map<String, Object> props, Path activityDir) {
        String subdir = optionalString(props, "logDir", DEFAULT_LOG_DIR);
        Path targetDir = activityDir.resolve(subdir).normalize();
        Path target = targetDir.resolve(JSONL_FILE);

        return new Hook() {
            @Override
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PostCallEvent post) {
                    try {
                        Files.createDirectories(targetDir);
                        String agentName = post.getAgent() != null ? post.getAgent().getName() : "?";
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("ts", System.currentTimeMillis());
                        entry.put("agent", agentName);
                        entry.put("finalMsg", post.getFinalMessage());
                        String line = JsonUtil.mapper().writeValueAsString(entry) + System.lineSeparator();
                        Files.writeString(target, line, StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        log.warn("audit-jsonl write failed: {}", e.getMessage());
                    }
                }
                return Mono.just(event);
            }
        };
    }

    private static String optionalString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object v = props.get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
```

- [ ] **Step 6: 写 AuditJsonlHookTypeTest（直接调 hook 验证文件写入）**

Create `src/test/java/io/agentscope/builder/saton/factory/hook/impl/AuditJsonlHookTypeTest.java`:

```java
package io.agentscope.builder.saton.factory.hook.impl;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class AuditJsonlHookTypeTest {

    @TempDir Path activityDir;

    @Test
    void postCallAppendsJsonlLine() throws Exception {
        AuditJsonlHookType type = new AuditJsonlHookType();
        Hook hook = type.instantiate(Map.of(), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("test-agent");

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("hello").build()).build();
        PostCallEvent event = new PostCallEvent(dummyAgent, msg);
        hook.onEvent(event).block();

        Path jsonl = activityDir.resolve("activity").resolve("activity.jsonl");
        assertTrue(Files.exists(jsonl), "jsonl file should exist at " + jsonl);
        List<String> lines = Files.readAllLines(jsonl);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"agent\":\"test-agent\""), "line should contain agent name; got: " + lines.get(0));
    }

    @Test
    void customLogDirIsRespected() throws Exception {
        AuditJsonlHookType type = new AuditJsonlHookType();
        Hook hook = type.instantiate(Map.of("logDir", "audit"), activityDir);

        Agent dummyAgent = Mockito.mock(Agent.class);
        Mockito.when(dummyAgent.getName()).thenReturn("a");

        Msg msg = Msg.builder().name("assistant").role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("x").build()).build();
        hook.onEvent(new PostCallEvent(dummyAgent, msg)).block();

        assertTrue(Files.exists(activityDir.resolve("audit").resolve("activity.jsonl")));
    }
}
```

- [ ] **Step 7: 写 HookFactoryTest**

Create `src/test/java/io/agentscope/builder/saton/factory/hook/HookFactoryTest.java`:

```java
package io.agentscope.builder.saton.factory.hook;

import io.agentscope.core.hook.Hook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
@SpringBootTest
class HookFactoryTest {

    @Autowired HookFactory factory;

    @TempDir Path activityDir;

    @Test
    void instantiateLogging() {
        Hook hook = factory.instantiate("logging", Map.of(), activityDir);
        assertNotNull(hook);
    }

    @Test
    void instantiateAuditJsonl() {
        Hook hook = factory.instantiate("audit-jsonl", Map.of(), activityDir);
        assertNotNull(hook);
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.instantiate("nope", Map.of(), activityDir));
    }
}
```

- [ ] **Step 8: 跑所有 hook 测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test "-Dtest=HookFactoryTest,AuditJsonlHookTypeTest" -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: PASS — 3 + 2 = 5 tests 全绿

- [ ] **Step 9: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 133 PASS（128 + 5）

- [ ] **Step 10: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/factory/hook/ `
        src/test/java/io/agentscope/builder/saton/factory/hook/
git commit -m "feat(m7): HookFactory + logging/audit-jsonl HookType"
```

---

## Task 4: 把 skill/hook/subagent 接入 AgentBuildOrchestrator + AgentService dto

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/SkillRepoSpec.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/HookSpec.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentUpsertReq.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentVO.java`
- Test (NEW): `src/test/java/io/agentscope/builder/saton/agent/runtime/SubagentTreeTest.java`

**目标**：让 agent_definition 真正消费 `skill_repositories_json` / `hook_specs_json` / `subagent_refs_json`，在 build 时按顺序挂上 skill 仓库、注册 hook、注册 subagent 工厂。

- [ ] **Step 1: 写 SkillRepoSpec / HookSpec record**

Create `src/main/java/io/agentscope/builder/saton/agent/SkillRepoSpec.java`:

```java
package io.agentscope.builder.saton.agent;

import java.util.Map;

/**
 * One entry in {@code skill_repositories_json}.
 *
 * <p>JSON shape: {@code {"type":"local","props":{"path":"skills"}}}
 */
public record SkillRepoSpec(String type, Map<String, Object> props) { }
```

Create `src/main/java/io/agentscope/builder/saton/agent/HookSpec.java`:

```java
package io.agentscope.builder.saton.agent;

import java.util.Map;

/**
 * One entry in {@code hook_specs_json}.
 *
 * <p>JSON shape: {@code {"type":"logging","props":{}}}
 */
public record HookSpec(String type, Map<String, Object> props) { }
```

- [ ] **Step 2: 写 SubagentTreeTest**

Create `src/test/java/io/agentscope/builder/saton/agent/runtime/SubagentTreeTest.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SubagentTreeTest {

    @Autowired AgentDefinitionRepository agentRepo;
    @Autowired ModelProviderRepository modelRepo;
    @Autowired AgentBuildOrchestrator orchestrator;

    @Test
    void buildParentWithChildSubagentRef() {
        // Seed model
        ModelProviderEntity model = new ModelProviderEntity();
        model.setOwnerId("admin");
        model.setName("stub-" + System.nanoTime());
        model.setType("test-stub");
        model.setPropsJson("{}");
        model.setCreatedAt(System.currentTimeMillis());
        model.setUpdatedAt(System.currentTimeMillis());
        model = modelRepo.save(model);

        // Seed child agent
        AgentDefinitionEntity child = new AgentDefinitionEntity();
        child.setOwnerId("admin");
        child.setAgentId("child-" + System.nanoTime());
        child.setName("child");
        child.setSysPrompt("I am child");
        child.setAgentType("react");
        child.setDefaultModelProviderId(model.getId());
        child.setMaxIters(3);
        child.setCreatedAt(System.currentTimeMillis());
        child.setUpdatedAt(System.currentTimeMillis());
        child = agentRepo.save(child);

        // Seed parent referencing child via subagentRefsJson
        AgentDefinitionEntity parent = new AgentDefinitionEntity();
        parent.setOwnerId("admin");
        parent.setAgentId("parent-" + System.nanoTime());
        parent.setName("parent");
        parent.setSysPrompt("I am parent");
        parent.setAgentType("react");
        parent.setDefaultModelProviderId(model.getId());
        parent.setMaxIters(3);
        parent.setSubagentRefsJson("[\"" + child.getAgentId() + "\"]");
        parent.setCreatedAt(System.currentTimeMillis());
        parent.setUpdatedAt(System.currentTimeMillis());
        parent = agentRepo.save(parent);

        HarnessAgent parentAgent = orchestrator.build(parent, model, "admin");
        assertNotNull(parentAgent, "parent should build successfully with subagent factory wired");
    }
}
```

- [ ] **Step 3: 跑测试看 fail（subagentRefsJson 字段没人消费）**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=SubagentTreeTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: PASS（因为 Task 1 写的 orchestrator 已经成功 build，subagentRefsJson 还没消费但也不阻塞 build）—— 即使绿了也继续 Step 4 接 subagent factory，让"功能性"有验证。

如果其实 fail，记下错误信息，Step 4 加完逻辑后再跑。

- [ ] **Step 4: 整文件替换 AgentBuildOrchestrator —— 加 skill/hook/subagent 三件套**

整文件替换 `src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java`:

```java
package io.agentscope.builder.saton.agent.runtime;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.HookSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.hook.HookFactory;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import io.agentscope.builder.saton.factory.skill.SkillFactory;
import io.agentscope.builder.saton.factory.tool.ToolFactory;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.builder.saton.resource.model.ModelProviderRepository;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link HarnessAgent} from agent_definition + model_provider + ownerId.
 *
 * <p>M7 wires skill repositories (skill_repositories_json), hooks (hook_specs_json),
 * and subagent factories (subagent_refs_json) on top of M5/M6's model + tools + state.
 *
 * <p>Subagent factories are recursive: parent declares child agent_id in subagent_refs;
 * orchestrator looks up child's AgentDefinitionEntity by (ownerId, agent_id) and recursively
 * builds a HarnessAgent for it.
 *
 * <p>@Lazy on AgentDefinitionRepository + ModelProviderRepository避免 Spring 启动期 DI 死循环
 * （orchestrator → resolver → AgentService → orchestrator 之类）。
 */
@Component
@SuppressWarnings("deprecation") // Hook is deprecated but still used by HarnessAgent.Builder
public class AgentBuildOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentBuildOrchestrator.class);

    private final ModelFactory modelFactory;
    private final ToolFactory toolFactory;
    private final SkillFactory skillFactory;
    private final HookFactory hookFactory;
    private final AgentStateStore stateStore;
    private final WorkspacePathResolver workspaceResolver;
    private final AgentDefinitionRepository agentRepo;
    private final ModelProviderRepository modelRepo;

    public AgentBuildOrchestrator(ModelFactory modelFactory,
                                  ToolFactory toolFactory,
                                  SkillFactory skillFactory,
                                  HookFactory hookFactory,
                                  AgentStateStore stateStore,
                                  WorkspacePathResolver workspaceResolver,
                                  @Lazy AgentDefinitionRepository agentRepo,
                                  @Lazy ModelProviderRepository modelRepo) {
        this.modelFactory = modelFactory;
        this.toolFactory = toolFactory;
        this.skillFactory = skillFactory;
        this.hookFactory = hookFactory;
        this.stateStore = stateStore;
        this.workspaceResolver = workspaceResolver;
        this.agentRepo = agentRepo;
        this.modelRepo = modelRepo;
    }

    public HarnessAgent build(AgentDefinitionEntity def,
                              ModelProviderEntity model,
                              String ownerId) {
        Model llm = modelFactory.instantiate(model);
        int maxIters = def.getMaxIters() != null ? def.getMaxIters() : 10;

        Toolkit toolkit = new Toolkit();
        for (ToolSpec spec : parseToolSpecs(def.getToolSpecsJson())) {
            toolkit.registerTool(toolFactory.instantiate(spec.type(), spec.props()));
        }

        Path workspace = workspaceResolver.agentRoot(ownerId, def.getId());
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("failed to mkdir workspace: " + workspace, e);
        }

        List<AgentSkillRepository> skillRepos = new ArrayList<>();
        for (SkillRepoSpec spec : parseSkillRepoSpecs(def.getSkillRepositoriesJson())) {
            try {
                skillRepos.add(skillFactory.instantiate(spec.type(), spec.props(), workspace));
            } catch (RuntimeException e) {
                log.warn("skip skill repo type={} due to {}", spec.type(), e.getMessage());
            }
        }

        List<Hook> hooks = new ArrayList<>();
        for (HookSpec spec : parseHookSpecs(def.getHookSpecsJson())) {
            try {
                hooks.add(hookFactory.instantiate(spec.type(), spec.props(), workspace));
            } catch (RuntimeException e) {
                log.warn("skip hook type={} due to {}", spec.type(), e.getMessage());
            }
        }

        HarnessAgent.Builder b = HarnessAgent.builder()
                .name(def.getAgentId())
                .sysPrompt(def.getSysPrompt() != null ? def.getSysPrompt() : "")
                .model(llm)
                .toolkit(toolkit)
                .maxIters(maxIters)
                .stateStore(stateStore)
                .defaultSessionId("agent_" + def.getId() + "_default")
                .workspace(workspace);

        if (!skillRepos.isEmpty()) {
            b.skillRepositories(skillRepos);
        }
        for (Hook h : hooks) {
            b.hook(h);
        }

        // Subagents: look up each ref by (ownerId, agentId), build recursively.
        for (String childAgentId : parseSubagentRefs(def.getSubagentRefsJson())) {
            b.subagentFactory(childAgentId, name -> buildChildAgent(name, ownerId));
        }

        return b.build();
    }

    private io.agentscope.core.agent.Agent buildChildAgent(String childAgentId, String ownerId) {
        AgentDefinitionEntity child = agentRepo.findByOwnerIdAndAgentId(ownerId, childAgentId)
                .orElseThrow(() -> new IllegalStateException(
                        "subagent not found: " + childAgentId + " (owner=" + ownerId + ")"));
        ModelProviderEntity childModel = modelRepo
                .findByIdAndOwnerId(child.getDefaultModelProviderId(), ownerId)
                .orElseThrow(() -> new IllegalStateException(
                        "subagent's model not found: " + child.getDefaultModelProviderId()));
        return build(child, childModel, ownerId);
    }

    private static List<ToolSpec> parseToolSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<ToolSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid tool_specs_json", e);
        }
    }

    private static List<SkillRepoSpec> parseSkillRepoSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<SkillRepoSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid skill_repositories_json", e);
        }
    }

    private static List<HookSpec> parseHookSpecs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<HookSpec>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid hook_specs_json", e);
        }
    }

    private static List<String> parseSubagentRefs(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("invalid subagent_refs_json", e);
        }
    }
}
```

注意：用了 `agentRepo.findByOwnerIdAndAgentId(...)`，需要确认这个方法存在；如果不存在 implementer 加一个：

```java
// 在 AgentDefinitionRepository.java 中加（如果不存在）：
java.util.Optional<AgentDefinitionEntity> findByOwnerIdAndAgentId(String ownerId, String agentId);
```

Spring Data JPA 会自动派生实现。

- [ ] **Step 5: 改 AgentUpsertReq —— 加 3 个新字段**

整文件替换 `src/main/java/io/agentscope/builder/saton/agent/dto/AgentUpsertReq.java`:

读现有文件确认当前 record 字段顺序后，加 skillRepositories / hookSpecs / subagentRefs 三个字段到末尾（保持向后兼容旧 ctor）。

最终文件示意：

```java
package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.HookSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;

import java.util.List;

/**
 * Agent 创建/更新请求。
 *
 * <p>M7 起新增 skillRepositories / hookSpecs / subagentRefs 三个可空字段；老 8 参 ctor
 * 保留作向后兼容（M5/M6 的测试还在用）。
 */
public record AgentUpsertReq(String agentId,
                             String name,
                             String description,
                             String sysPrompt,
                             String agentType,
                             Long defaultModelProviderId,
                             Integer maxIters,
                             List<ToolSpec> toolSpecs,
                             List<SkillRepoSpec> skillRepositories,
                             List<HookSpec> hookSpecs,
                             List<String> subagentRefs) {

    /** 兼容 M5/M6 老 8 参 ctor —— skill/hook/subagent 默认 null。 */
    public AgentUpsertReq(String agentId,
                          String name,
                          String description,
                          String sysPrompt,
                          String agentType,
                          Long defaultModelProviderId,
                          Integer maxIters,
                          List<ToolSpec> toolSpecs) {
        this(agentId, name, description, sysPrompt, agentType, defaultModelProviderId,
                maxIters, toolSpecs, null, null, null);
    }
}
```

- [ ] **Step 6: 改 AgentVO 同步加 3 字段**

整文件替换 `src/main/java/io/agentscope/builder/saton/agent/dto/AgentVO.java` —— 在现有 11 字段基础上再加 skillRepositories / hookSpecs / subagentRefs；`from(entity)` 静态方法反序列化 JSON 列。

实施者先 Read 当前 AgentVO 文件确认结构，然后整文件替换；以下示意（按现有 from 方法的风格）：

```java
package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.HookSpec;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.ToolSpec;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.core.type.TypeReference;

import java.util.List;

public record AgentVO(Long id,
                      String ownerId,
                      String agentId,
                      String name,
                      String description,
                      String sysPrompt,
                      String agentType,
                      Long defaultModelProviderId,
                      Integer maxIters,
                      List<ToolSpec> toolSpecs,
                      List<SkillRepoSpec> skillRepositories,
                      List<HookSpec> hookSpecs,
                      List<String> subagentRefs,
                      long createdAt,
                      long updatedAt) {

    public static AgentVO from(AgentDefinitionEntity e) {
        return new AgentVO(
                e.getId(), e.getOwnerId(), e.getAgentId(), e.getName(),
                e.getDescription(), e.getSysPrompt(), e.getAgentType(),
                e.getDefaultModelProviderId(), e.getMaxIters(),
                parseList(e.getToolSpecsJson(), new TypeReference<>() {}),
                parseList(e.getSkillRepositoriesJson(), new TypeReference<>() {}),
                parseList(e.getHookSpecsJson(), new TypeReference<>() {}),
                parseList(e.getSubagentRefsJson(), new TypeReference<>() {}),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static <T> List<T> parseList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JsonUtil.mapper().readValue(json, ref);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
```

- [ ] **Step 7: 改 AgentService —— create/update 序列化新字段到 JSON 列**

Edit `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`. 跟 M5 处理 toolSpecs 一样：

**Find** `create(req)` 方法体中 `e.setToolSpecsJson(serializeToolSpecs(req.toolSpecs()));` 之后，**在它后面追加**：

```java
        e.setSkillRepositoriesJson(serializeJson(req.skillRepositories()));
        e.setHookSpecsJson(serializeJson(req.hookSpecs()));
        e.setSubagentRefsJson(serializeJson(req.subagentRefs()));
```

**Find** `update(...)` 方法体中同样位置，**同样追加上面 3 行**。

**Find** 现有 `serializeToolSpecs(...)` 静态方法（在文件末尾），**在它之后追加** 通用版本：

```java
    /** 通用 list → JSON 字符串；null/empty → null。 */
    private static String serializeJson(java.util.List<?> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return io.agentscope.builder.saton.common.json.JsonUtil.mapper().writeValueAsString(list);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid json payload", ex);
        }
    }
```

- [ ] **Step 8: 跑 SubagentTreeTest 看 PASS**

```powershell
mvn --% test -Dtest=SubagentTreeTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: PASS

- [ ] **Step 9: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 134 PASS（133 + 1 SubagentTreeTest）。M5 的 AgentUpsertReq 8 参 ctor 兼容旧 ChatFlowTest / SessionFlowTest 等，**不应**有 regression。

如果 fail：
- 编译错 `AgentDefinitionRepository.findByOwnerIdAndAgentId` not found → 加上面 §Step 4 提到的方法
- AgentService 序列化错误 → 检查 serializeJson 是否真在 AgentService 类里（不是 helper 类）

- [ ] **Step 10: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/agent/SkillRepoSpec.java `
        src/main/java/io/agentscope/builder/saton/agent/HookSpec.java `
        src/main/java/io/agentscope/builder/saton/agent/runtime/AgentBuildOrchestrator.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentService.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java `
        src/main/java/io/agentscope/builder/saton/agent/dto/AgentUpsertReq.java `
        src/main/java/io/agentscope/builder/saton/agent/dto/AgentVO.java `
        src/test/java/io/agentscope/builder/saton/agent/runtime/SubagentTreeTest.java
git commit -m "feat(m7): orchestrator consumes skill/hook/subagent JSON; dto round-trip"
```

---

## Task 5: FactoriesController 暴露 skill-repo-types / hook-types + 收尾

**Files:**
- Modify: `src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java`
- Modify: `src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java`
- Modify: `docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`（§12 占位）

**目标**：暴露 spec §4.3 缺失的两个目录端点；扩 FactoriesControllerFlowTest 验证；spec 加 §12.18-12.19 占位（M7 实施新发现）；tag。

- [ ] **Step 1: Read 当前 FactoriesController + FactoriesControllerFlowTest 了解既有端点风格**

Read `src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java` 全文 —— 看 M5 是怎么暴露 model-types / tool-types 的，照同样风格加两个新方法即可。

- [ ] **Step 2: 改 FactoriesController —— 加 GET /skill-repo-types + /hook-types**

Edit `src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java`:

**Find** controller class，在已有 ctor 字段中（应该已有 `ModelProviderTypeRegistry` 和 `ToolProviderTypeRegistry`），加入两个新字段 + ctor 参数：

具体调整（以现有风格为准；如下示意）：

```java
    private final io.agentscope.builder.saton.factory.skill.SkillRepoTypeRegistry skillRegistry;
    private final io.agentscope.builder.saton.factory.hook.HookTypeRegistry hookRegistry;
```

构造器多接两个参数。

**在已有 `tool-types` GetMapping 之后**追加：

```java
    @GetMapping("/skill-repo-types")
    public List<io.agentscope.builder.saton.factory.core.TypeMeta> skillRepoTypes() {
        return skillRegistry.listMetas();
    }

    @GetMapping("/hook-types")
    public List<io.agentscope.builder.saton.factory.core.TypeMeta> hookTypes() {
        return hookRegistry.listMetas();
    }
```

（如果 `ProviderRegistry` 暴露的方法名不是 `listMetas()`，按实际 method 名调整。M3-M5 的 model-types/tool-types 已经用过，照搬。）

- [ ] **Step 3: 改 FactoriesControllerFlowTest 加 2 个新断言**

Edit `src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java`:

参考已有 `modelTypesReturnsBuiltins` / `toolTypesReturnsBuiltins` 的写法，**在文件末尾追加**：

```java
    @Test
    void skillRepoTypesReturnsBuiltins() {
        List<TypeMeta> types = client.get().uri("/api/factories/skill-repo-types")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(TypeMeta.class).returnResult().getResponseBody();
        assertNotNull(types);
        assertTrue(types.stream().anyMatch(t -> t.type().equals("local")));
        assertTrue(types.stream().anyMatch(t -> t.type().equals("git")));
    }

    @Test
    void hookTypesReturnsBuiltins() {
        List<TypeMeta> types = client.get().uri("/api/factories/hook-types")
                .header("satoken", token).exchange().expectStatus().isOk()
                .expectBodyList(TypeMeta.class).returnResult().getResponseBody();
        assertNotNull(types);
        assertTrue(types.stream().anyMatch(t -> t.type().equals("logging")));
        assertTrue(types.stream().anyMatch(t -> t.type().equals("audit-jsonl")));
    }
```

（如果 TypeMeta import 路径在测试里不可见，加 `import io.agentscope.builder.saton.factory.core.TypeMeta;`）

- [ ] **Step 4: 跑 FactoriesControllerFlowTest 看 PASS**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=FactoriesControllerFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: PASS（含 2 新 case）

- [ ] **Step 5: 全量回归确认 M1-M7 完整绿**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 136 PASS（134 + 2 new factory endpoints test）

- [ ] **Step 6: 给 spec 加 §12.18-12.19 占位**

Edit `docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`. Find `### 12.17 WebFlux Controller 兜底异常处理` section. **在它之后、`---` 分隔符之前**追加：

```markdown

### 12.18 [M7 占位 — 实施过程中如发现新坑，填这里]

（M7 实施时如遇 HarnessAgent.Builder 必填字段、subagent factory 递归路径死循环、@Lazy + ctor 注入交互、Hook 类已 deprecated 但仍生效等新坑，留在这里。）

### 12.19 [M7 占位 — 实施过程中如发现新坑，填这里]

（同上。）
```

- [ ] **Step 7: 提交 spec 更新到 outer repo**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java
git add docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md
git commit -m "docs(spec): §12.18-12.19 占位 — 待 M7 实施时填"
```

- [ ] **Step 8: 提交 FactoriesController 到 inner repo + tag**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
git add src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java `
        src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java
git commit -m "feat(m7): expose /skill-repo-types + /hook-types in FactoriesController"
git tag m7-harness-skill-hook-subagent-done
```

- [ ] **Step 9: 报告 M7 完成**

整理给 user 的总结报告：
- 通过的测试数量（应 136 累计）
- 关键能力：HarnessAgent 切换完成、3 个新工厂上线、subagent 递归构建 OK
- spec §12 现累计条目数
- M8 预告：marketplace 集成（git/nacos）+ skill install to workspace

---

## 自检（Self-Review）

执行完所有 task 后，对照本 plan 检查：

1. **Spec coverage** —— M7 行（spec §9）= "SkillFactory + HookFactory + 3 类 hook + SubAgentTool + sessions_spawn" / 完成标志: "agent 用 skill 文件、调子 agent"
   - SkillFactory + 2 内置（local/git）→ Task 2 ✅
   - HookFactory + 2 内置（logging/audit-jsonl）→ Task 3 ✅（spec 列 4 个，M7 做 2 个最有用的；tracing-otel / tool-notification 留 M10 polish）
   - SubAgent 走 HarnessAgent.subagentFactory（不是 SubAgentTool 也不是 SessionsTool；架构决策见 plan 头部）→ Task 4 ✅
   - 没做 "sessions_spawn" 子命令 —— spec §10 没单独定义，原 builder 是 SessionsTool 的一个内部 method；本 plan 走 subagentFactory 路径不需要单独的 spawn 入口
   - HarnessAgent 切换是大前提，Task 1 完成 ✅

2. **Placeholder 扫描** —— 每个 step 都有完整代码或具体命令。Task 5 §12.18/§12.19 是实施期占位（带说明），不是 plan placeholder。

3. **类型一致性** ——
   - `AgentBuildOrchestrator.build(def, model, ownerId)` 3 参签名贯穿 Task 1/4 ✅
   - `AgentRuntimeResolver.resolve(defId, modelId, ownerId)` 3 参签名 ✅
   - `SkillRepoSpec` / `HookSpec` record 用相同 `(String type, Map<String,Object> props)` shape ✅
   - HarnessAgent 类型贯穿 Task 1 之后所有改动 ✅
   - subagent_refs_json 用 `List<String>`（每元素是另一 agent 的 agent_id），跟 plan 头决策一致 ✅
   - Hook 接口的 deprecated 警告全部 @SuppressWarnings 抑制 ✅

4. **关键风险预防** ——
   - sa-token + WebFlux（§12.4 / §12.14）—— 本 plan 不动 controller 层（除 FactoriesController 加 GET）✅
   - SB4 双 public ctor（§12.16）—— 本 plan 所有新 service 都单 ctor，无问题 ✅
   - IllegalArgumentException → 400（§12.17）—— 新代码抛 IAE 自动走 400，例如 SkillFactory 未知 type ✅
   - HarnessAgent.Builder.workspace(Path) 必须存在 —— orchestrator 在传之前 Files.createDirectories(...) ✅
   - subagent 递归死循环 —— 没显式保护。如果 A.subagentRefs=[B] 且 B.subagentRefs=[A]，build 会无限递归。**留意**：当前 plan 不做防御，碰到了再加 visited set。spec §12.18 占位可记录。
   - skill/hook 工厂运行时异常 —— 全部捕获到 log.warn，不阻塞 build；类型未知（IllegalArgumentException）也只 warn 跳过，不抛到 build 调用方。这点跟 plan 决策一致。

---

## 执行说明

按 subagent-driven 顺序执行 5 个 task：每个 task 一个 implementer subagent，结束后 spec compliance review + code quality review。Task 之间有强依赖（按编号），不要并发：
- Task 1 (HarnessAgent 切换) 必须先做完
- Task 2/3 (Skill/Hook 工厂) 并列，可单独跑测，但合并 commit 顺序仍按编号
- Task 4 依赖 Task 1/2/3 全部完成（要用到 SkillFactory + HookFactory 实例）
- Task 5 收尾，依赖 Task 2/3 实现的 registry

预计累计测试数：M6 = 118 → +1 HarnessAgentSwitchTest → +9 skill 测试 → +5 hook 测试 → +1 SubagentTreeTest → +2 FactoriesController 新断言 = **136 PASS**。
