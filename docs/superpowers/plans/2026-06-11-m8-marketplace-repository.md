# M8 Marketplace + Repository 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 skill marketplace（git/nacos）浏览与安装到 workspace 的能力。用户在 UI 上管理 marketplace 实体（M2 已有 CRUD），浏览市场里的 skill 列表和详情，一键安装到 agent workspace。

**Architecture:** 三层分离：`BuilderMarketplace` SPI 是各市场类型的统一接口；`UserMarketplaceRegistry` 管理器将 DB 实体（`SkillMarketplaceEntity`）映射为运行时实例并管理生命周期；`SKillMarketplaceController` + `AgentSkillsController` 暴露浏览和安装 REST 端点。

**Tech Stack:** agentscope-core 2.0.0-RC2 (AgentSkill/GitSkillRepository)、反射（Nacos SDK）、`WorkspaceService` (NIO 文件写)、Jackson 3

**基线测试数:** 136 PASS (M7 末)

**预计累计测试数:** 136 + 3 (Task 2) + 4 (Task 3) + 2 (Task 4) + 5 (Task 5) = **150 PASS**

---

## 文件结构总览

```
resource/marketplace/
├── SkillMarketplaceController.java    [MODIFY] 追加 GET /{id}/skills + GET /{id}/skills/{name}
├── SkillMarketplaceService.java       [MODIFY] 追加 listSkills/getSkill (委托到 registry)

resource/marketplace/dto/
├── SkillMarketplaceVO.java            [MODIFY] 加 displayLocation 字段? 需确认

marketplace/                            [NEW directory under factory/]
├── BuilderMarketplace.java            [NEW] SPI 接口
├── MarketSkillSummary.java            [NEW] 浏览列表 DTO record
├── MarketSkillContent.java            [NEW] skill 详情 DTO record
├── UserMarketplaceRegistry.java       [NEW] 运行时管理器 (Spring @Component)
├── impl/
│   ├── GitBuilderMarketplace.java     [NEW] git 市场实现 (反射 GitSkillRepository)
│   └── NacosBuilderMarketplace.java   [NEW] nacos 市场实现 (反射 Nacos SDK)

skill/                                 [NEW directory]
├── AgentSkillsController.java         [NEW] /api/agents/{id}/skills/**
├── AgentSkillService.java             [NEW] 安装逻辑
├── dto/
│   ├── InstallFromRepoReq.java        [NEW] 仓库安装请求 record
│   ├── MarketplaceInstallReq.java     [NEW] 市场安装请求 record
│   └── WorkspaceSkillVO.java          [NEW] workspace skill 展示 VO

test/
├── factory/marketplace/
│   ├── GitBuilderMarketplaceTest.java   [NEW] 3 tests
│   └── NacosBuilderMarketplaceTest.java [NEW] 3 tests
├── factory/marketplace/
│   └── UserMarketplaceRegistryTest.java [NEW] 4 tests
├── resource/marketplace/
│   └── SkillMarketplaceFlowTest.java    [MODIFY] 增 2 browsing 断言
└── skill/
    └── AgentSkillsFlowTest.java         [NEW] 5 tests
```

---

### Task 1: BuilderMarketplace SPI + GitBuilderMarketplace (反射)

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/BuilderMarketplace.java`
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/MarketSkillSummary.java`
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/MarketSkillContent.java`
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/impl/GitBuilderMarketplace.java`
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/impl/NacosBuilderMarketplace.java`

**目标**：定义 marketplace SPI + git 和 nacos 两种实现。git 用反射加载 `GitSkillRepository`，nacos 用反射加载 Nacos maintainer client SDK。

- [ ] **Step 1: 写 BuilderMarketplace 接口**

`src/main/java/io/agentscope/builder/saton/marketplace/BuilderMarketplace.java`：

```java
package io.agentscope.builder.saton.marketplace;

import java.util.List;

public interface BuilderMarketplace extends AutoCloseable {

    String id();
    String type();
    String displayLocation();
    default boolean writable() { return false; }
    List<MarketSkillSummary> list();
    MarketSkillContent fetch(String name);
    @Override void close();
}
```

- [ ] **Step 2: 写 MarketSkillSummary + MarketSkillContent record**

`src/main/java/io/agentscope/builder/saton/marketplace/MarketSkillSummary.java`：

```java
package io.agentscope.builder.saton.marketplace;

public record MarketSkillSummary(String name, String description, String version) {}
```

`src/main/java/io/agentscope/builder/saton/marketplace/MarketSkillContent.java`：

```java
package io.agentscope.builder.saton.marketplace;

import java.util.Map;

public record MarketSkillContent(
        String name, String description, String markdown, Map<String, String> resources) {}
```

- [ ] **Step 3: 写 GitBuilderMarketplace（反射 GitSkillRepository）**

`src/main/java/io/agentscope/builder/saton/marketplace/impl/GitBuilderMarketplace.java`：

依赖 `io.agentscope.core.skill.repository.GitSkillRepository` 反射加载。构造器：接受 `id`, `remoteUrl`, `branch`, `localPath`, `skillsRoot`。list() 返回 `repo.getAllSkills()` 转换；fetch(name) 返回单个 skill。

关注点：
- 构造器验证 id + remoteUrl 非空 → IAE
- `Class.forName("io.agentscope.core.skill.repository.GitSkillRepository")` 反射
- `ClassNotFoundException` → `IllegalStateException("git skill repository support not installed (add agentscope-extensions-skill-git-repository dependency)")`
- 构造后调用 `repo.getAllSkills()` 做 probe（验证仓库可达）；probe 失败也 ISE
- 实现参考原始 `GitBuilderMarketplace.java`（~130 行），但适配我们自己的 record (MarketSkillSummary/MarketSkillContent)

- [ ] **Step 4: 写 NacosBuilderMarketplace（反射 Nacos SDK）**

`src/main/java/io/agentscope/builder/saton/marketplace/impl/NacosBuilderMarketplace.java`：

依赖 `com.alibaba.nacos.api.ai.model.skills.*` + `com.alibaba.nacos.maintainer.client.ai.*` 反射加载。

关注点：
- 所有 Nacos class 名写为字符串常量，整段通过 `Class.forName` + `Method.invoke` 调用
- `ClassNotFoundException` → `IllegalStateException("nacos skill repository support not installed (add nacos-client dependency)")`
- 列出支持的 prop keys: `serverAddr`, `namespace`, `username`, `password`, `accessKey`
- `list()` 走分页；`fetch(name)` 走 `getSkill(name, "LATEST")`
- 原始实现参考 `NacosBuilderMarketplace.java`（~205 行）

因为反射代码量较大且涉及分页逻辑，可以直接照搬原始 builder 的 NacosBuilderMarketplace 代码，但把所有的 Nacos import 改为 `Class.forName(...)` + `constructor.newInstance(...)` + `method.invoke(...)` 模式。

- [ ] **Step 5: 跑这两个市场的单元测试（Task 1-2 合写的问题**见下**，这里先不跑，Task 2 一起写测试后跑）

注：Task 1 只写 main 源码，测试在 Task 2 合写。两个实现写完后直接 `mvn compile` 验证编译通过即可。

编译命令：
```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% compile -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: BUILD SUCCESS（有 deprecation warning 正常）

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/marketplace/
git commit -m "feat(m8): BuilderMarketplace SPI + GitBuilderMarketplace/NacosBuilderMarketplace (反射)"
```

---

### Task 2: UserMarketplaceRegistry + 市场实现测试

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/marketplace/UserMarketplaceRegistry.java`
- Create: `src/test/java/io/agentscope/builder/saton/marketplace/impl/GitBuilderMarketplaceTest.java`
- Create: `src/test/java/io/agentscope/builder/saton/marketplace/impl/NacosBuilderMarketplaceTest.java`
- Create: `src/test/java/io/agentscope/builder/saton/marketplace/UserMarketplaceRegistryTest.java`

**目标**：UserMarketplaceRegistry 将 `SkillMarketplaceEntity` DB 记录转换为运行时 `BuilderMarketplace` 实例；写 Task 1 实现的两个市场的单元测试。市场测试验证构造/classpath缺失/基础契约。

- [ ] **Step 1: 写 GitBuilderMarketplaceTest（3 tests）**

`src/test/java/io/agentscope/builder/saton/marketplace/impl/GitBuilderMarketplaceTest.java`：

```java
class GitBuilderMarketplaceTest {
    @Test void nullIdThrows() — assertThrows(IAE)
    @Test void nullRemoteUrlThrows() — assertThrows(IAE)
    @Test void missingClasspathThrowsIseWithHint() — assertThrows(ISE), message contains "git-repository"
}
```

- [ ] **Step 2: 写 NacosBuilderMarketplaceTest（3 tests）**

`src/test/java/io/agentscope/builder/saton/marketplace/impl/NacosBuilderMarketplaceTest.java`：

```java
class NacosBuilderMarketplaceTest {
    @Test void nullIdThrows() — assertThrows(IAE)
    @Test void nullServerAddrThrows() — （通过 props 检查，至少 serverAddr 必填）
    @Test void missingClasspathThrowsIseWithHint() — assertThrows(ISE), message contains "nacos"
}
```

- [ ] **Step 3: 写 UserMarketplaceRegistry**

`src/main/java/io/agentscope/builder/saton/marketplace/UserMarketplaceRegistry.java`：

Spring `@Component`，注入 `SkillMarketplaceRepository`。

数据结构：`Map<String, Map<String, BuilderMarketplace>>` 即 `Map<userId, Map<marketplaceId, BuilderMarketplace>>`。

方法：
- `list(String userId)` → `List<BuilderMarketplace>` — 从缓存返回；缓存 miss 则加载 DB 记录并构造实例
- `find(String userId, String marketplaceId)` → `Optional<BuilderMarketplace>` — 单实例查找
- `invalidate(String userId)` — 清空该用户缓存（关闭旧实例），在 CRUD 外部调用
- `invalidateAll()` — 全部清空
- `@PreDestroy void destroy()` — 关闭所有实例

构造实例逻辑：
```java
private BuilderMarketplace createInstance(SkillMarketplaceEntity entity) {
    String type = entity.getType();
    String propsJson = entity.getPropsJson();
    Map<String, Object> props = propsJson != null
            ? JsonUtil.mapper().readValue(propsJson, new TypeReference<Map<String, Object>>() {})
            : Map.of();
    return switch (type) {
        case "git" -> new GitBuilderMarketplace(
                entity.getMarketplaceId(),
                stringProp(props, "remoteUrl"),
                stringProp(props, "branch"),
                null, // localPath — 让 GitSkillRepository 自动创建 temp dir
                stringProp(props, "skillsRoot"));
        case "nacos" -> new NacosBuilderMarketplace(
                entity.getMarketplaceId(),
                stringProp(props, "serverAddr"),
                stringProp(props, "namespace"),
                stringProp(props, "username"),
                stringProp(props, "password"),
                stringProp(props, "accessKey"));
        default -> throw new IllegalArgumentException("unsupported marketplace type: " + type);
    };
}
```

注意：`JsonUtil.mapper()` 用 Jackson 3 (`tools.jackson.databind`)，`TypeReference` 用 `tools.jackson.core.type.TypeReference` —— 与 spec §12.3 一致。

- [ ] **Step 4: 写 UserMarketplaceRegistryTest（4 tests）**

`src/test/java/io/agentscope/builder/saton/marketplace/UserMarketplaceRegistryTest.java`：

`@SpringBootTest`，注入 registry。

测试：
1. `listWhenNoEntitiesReturnsEmpty()` — 空用户 → `list("nobody")` 返回 `List.of()`
2. `listWithGitEntityReturnsMarketplace()` — seed 一个 git 类型 entity → `list("admin")` 返回 1 项
3. `findExistingReturnsPresent()` — seed → `find("admin", mkId)` → `Optional.isPresent()`
4. `findNonExistingReturnsEmpty()` — `find("admin", "nonexistent")` → `Optional.isEmpty()`

（GitBuilderMarketplace 构造需要反射 GitSkillRepository class，classpath 没有所以 seed git 类型会 ISE。这个得 mock 或构建 stub）
→ 用 `@MockBean SkillMarketplaceRepository` 来 mock 掉 DB，构造时 registry 调用 `try { createInstance(...) } catch (ISE) { log.warn }` 让缺失 classpath 的实体被跳过（同 orchestrator skill/hook 处理 pattern）。

**备选方案**：注册表 `createInstance(...)` 抛 ISE 时 `log.warn` 并 `continue`（不 throw），给 `find()`/`list()` 返回时静默忽略。测试里 seed 的实体如果 classpath missing 只会被跳过，返回空列表。这跟 M7-4 orchestrator 一致的"resource-not-available 不阻塞"哲学。

- [ ] **Step 5: 跑全部 marketplace 测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test "-Dtest=GitBuilderMarketplaceTest,NacosBuilderMarketplaceTest,UserMarketplaceRegistryTest" -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 3 + 3 + 4 = 10 tests PASS

- [ ] **Step 6: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 146 PASS（136 + 10）

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/marketplace/UserMarketplaceRegistry.java `
        src/test/java/io/agentscope/builder/saton/marketplace/
git commit -m "feat(m8): UserMarketplaceRegistry + Git/NacosBuilderMarketplace tests"
```

---

### Task 3: Marketplace 浏览端点

**Files:**
- Modify: `src/main/java/io/agentscope/builder/saton/resource/marketplace/SkillMarketplaceController.java`
- Modify: `src/main/java/io/agentscope/builder/saton/resource/marketplace/SkillMarketplaceService.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/marketplace/dto/MarketSkillVO.java`
- Modify: `src/test/java/io/agentscope/builder/saton/resource/marketplace/SkillMarketplaceFlowTest.java`

**目标**：暴露 `GET /api/skill-marketplaces/{id}/skills` (列表) + `GET /api/skill-marketplaces/{id}/skills/{name}` (详情)，让前端能浏览市场内容。

- [ ] **Step 1: 读当前 SkillMarketplaceController + Service + FlowTest**

确认：
- Controller 的 `inSaContext` helper 签名
- Service 的 `get(Long id)` 方法已返回 VO
- FlowTest 的 `setUp()` 中 login 和 seed entity 的写法

- [ ] **Step 2: 创建 MarketSkillVO**

`src/main/java/io/agentscope/builder/saton/resource/marketplace/dto/MarketSkillVO.java`：

```java
package io.agentscope.builder.saton.resource.marketplace.dto;

import io.agentscope.builder.saton.marketplace.MarketSkillContent;
import io.agentscope.builder.saton.marketplace.MarketSkillSummary;

import java.util.Map;

public record MarketSkillVO(String name, String description, String markdown, Map<String, String> resources) {
    public static MarketSkillVO from(MarketSkillContent c) {
        return new MarketSkillVO(c.name(), c.description(), c.markdown(), c.resources());
    }
}
```

以及一个轻量的 list VO（不含 markdown 等大字段）：

```java
// 在同一个文件中追加（或多个文件？简化为同一个文件即可）
public record MarketSkillSummaryVO(String name, String description, String version) {
    public static MarketSkillSummaryVO from(MarketSkillSummary s) {
        return new MarketSkillSummaryVO(s.name(), s.description(), s.version);
    }
}
```

（或者放两个单独文件，取决于 controller 风格。此处为了少创建文件，合在同一个文件里。）

- [ ] **Step 3: SkillMarketplaceService 加 listSkills / getSkill**

在 `SkillMarketplaceService.java` 中追加：

```java
    private final UserMarketplaceRegistry marketplaceRegistry;

    // 在现有一个参数的 ctor 上增加新参数 —— 注意 SB4 双 ctor 规则（§12.16）
    // 如果用一个 ctor：直接在原有 ctor 加参数
    // 如果原有 ctor 只有一个：改它

    public List<MarketSkillSummaryVO> listSkills(Long marketplaceId) {
        String me = StpUtil.getLoginIdAsString();
        SkillMarketplaceEntity entity = loadMine(marketplaceId, me);
        BuilderMarketplace mp = marketplaceRegistry.find(me, entity.getMarketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not available: " + marketplaceId));
        return mp.list().stream().map(MarketSkillSummaryVO::from).toList();
    }

    public MarketSkillVO getSkill(Long marketplaceId, String skillName) {
        String me = StpUtil.getLoginIdAsString();
        SkillMarketplaceEntity entity = loadMine(marketplaceId, me);
        BuilderMarketplace mp = marketplaceRegistry.find(me, entity.getMarketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not available: " + marketplaceId));
        MarketSkillContent content = mp.fetch(skillName);
        if (content == null) throw new NotFoundException("skill not found: " + skillName);
        return MarketSkillVO.from(content);
    }
```

**注意：引入 `UserMarketplaceRegistry` 依赖** —— 在 Service 里注入它。
**注意§12.16** —— 如果原 Service 已经有 public 单 ctor，直接改；如果是双 ctor 必须加 @Autowired（但目前看是单 ctor，safe）。

- [ ] **Step 4: SkillMarketplaceController 加 2 个端点**

在 `SkillMarketplaceController.java` 中追加：

```java
    @GetMapping("/{id}/skills")
    public Mono<List<MarketSkillSummaryVO>> listSkills(
            @PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.listSkills(id));
    }

    @GetMapping("/{id}/skills/{name}")
    public Mono<MarketSkillVO> getSkill(
            @PathVariable("id") Long id,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.getSkill(id, name));
    }
```

注意：需要在文件顶部 import：
```java
import io.agentscope.builder.saton.resource.marketplace.dto.MarketSkillSummaryVO;
import io.agentscope.builder.saton.resource.marketplace.dto.MarketSkillVO;
```

- [ ] **Step 5: SkillMarketplaceFlowTest 加 2 个测试**

追加：
1. `listSkillsReturnsOk()` — seed 一个 marketplace entity → `GET /{id}/skills` → 200 + body is list
2. `getSkillNonExistentReturns404()` — `GET /{id}/skills/missing-skill` → 404

因为 classpath 上没有 git/nacos 依赖，listSkills 实际返回空列表（registry 里的 createInstance 捕获 ISE 跳过）。所以第一个测试只验证 status+body 类型，不验证内容。
getSkill 则因为 marketplace 不可用而走到 marketplace 不可用的 404。

- [ ] **Step 6: 跑 SkillMarketplaceFlowTest**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=SkillMarketplaceFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 3 个既有 + 2 个新的 = 5 tests PASS

- [ ] **Step 7: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 148 PASS（146 + 2）

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/resource/marketplace/ `
        src/test/java/io/agentscope/builder/saton/resource/marketplace/
git commit -m "feat(m8): marketplace browsing endpoints (GET skills + GET skill/{name})"
```

---

### Task 4: AgentSkillsController — workspace skill install

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/skill/AgentSkillsController.java`
- Create: `src/main/java/io/agentscope/builder/saton/skill/AgentSkillService.java`
- Create: `src/main/java/io/agentscope/builder/saton/skill/dto/InstallFromRepoReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/skill/dto/MarketplaceInstallReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/skill/dto/WorkspaceSkillVO.java`
- Test: `src/test/java/io/agentscope/builder/saton/skill/AgentSkillsFlowTest.java`

**目标**：实现 `POST /api/agents/{id}/skills/workspace/install`（从已配置的 skill repository 安装）和 `POST /api/agents/{id}/skills/workspace/marketplace-install`（从 marketplace 安装），以及 workspace skill 的 GET（列表）/DELETE 等基本操作。

注意：本 task 服务层安装逻辑用 `WorkspaceService.write()` 写 `skills/{name}/SKILL.md` + resources。校验和冲突检测参照原 builder。

- [ ] **Step 0: 确定 WorkspaceService.write() 能否直接用于写 skill markdown**

`WorkspaceService.write(ownerId, agentDefId, relPath, content)` 的 `relPath` 通过 `WorkspacePathResolver.resolve()` 做越界校验。写 `skills/{name}/SKILL.md` 传 `"skills/{name}/SKILL.md"` 即可。

确认文件结构：
- WorkspaceService 的 `agentRoot = <root>/<ownerId>/<agentDefId>/files`
- Skill markdown 将被写到 `<root>/<ownerId>/<agentDefId>/files/skills/{name}/SKILL.md`
- 与原 builder 的 `workspace/skills/{name}/SKILL.md` 一致

- [ ] **Step 1: 读 AgentBuildOrchestrator / AgentRuntimeResolver 以便 SkillRepoSpec 解析**

确认：AgentBuildOrchestrator 的 `parseSkillRepoSpecs(def.getSkillRepositoriesJson())` 返回 `List<SkillRepoSpec>`。AgentDefinitionRepository 可按 agentId + ownerId 查到 entity。

安装流程：
1. 通过 AgentAccessGuard 等拿到 agent 的 owner + def
2. 解析 skill_repositories_json，按 index 拿到目标 SkillRepoSpec
3. 调 SkillFactory.instantiate(type, props, workspace) → AgentSkillRepository
4. repo.getSkill(name) → AgentSkill 对象
5. WorkspaceService.write() 写 SKILL.md + resources
6. 写入 _install.meta.json （记录来源、版本、时间）

但注意：AgentAccessGuard 当前实现了没？读一下。

先假设 controller 层只需要校验 agent 存在且属于当前用户（Owner 或 RUN tier）。

- [ ] **Step 2: 写 VO 和 DTO**

`src/main/java/io/agentscope/builder/saton/skill/dto/WorkspaceSkillVO.java`:

```java
package io.agentscope.builder.saton.skill.dto;

public record WorkspaceSkillVO(String name, String description, String source, long installTime) {}
```

`src/main/java/io/agentscope/builder/saton/skill/dto/InstallFromRepoReq.java`:

```java
package io.agentscope.builder.saton.skill.dto;

public record InstallFromRepoReq(int repoIndex, String skillName, String targetName, Boolean overwrite) {}
```

`src/main/java/io/agentscope/builder/saton/skill/dto/MarketplaceInstallReq.java`:

```java
package io.agentscope.builder.saton.skill.dto;

public record MarketplaceInstallReq(String marketplaceId, String skillName, String targetName, Boolean overwrite) {}
```

- [ ] **Step 3: 写 AgentSkillService**

`src/main/java/io/agentscope/builder/saton/skill/AgentSkillService.java`：

```java
package io.agentscope.builder.saton.skill;

import io.agentscope.builder.saton.agent.AgentDefinitionEntity;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.agent.SkillRepoSpec;
import io.agentscope.builder.saton.agent.runtime.AgentBuildOrchestrator;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.skill.SkillFactory;
import io.agentscope.builder.saton.marketplace.UserMarketplaceRegistry;
import io.agentscope.builder.saton.skill.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.dto.WorkspaceSkillVO;
import io.agentscope.builder.saton.workspace.WorkspacePathResolver;
import io.agentscope.builder.saton.workspace.WorkspaceService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
public class AgentSkillService {

    private static final Logger log = LoggerFactory.getLogger(AgentSkillService.class);
    private static final String SKILLS_DIR = "skills";

    private final AgentDefinitionRepository agentRepo;
    private final SkillFactory skillFactory;
    private final WorkspaceService workspaceService;
    private final WorkspacePathResolver workspaceResolver;
    private final UserMarketplaceRegistry marketplaceRegistry;
    // We need the orchestrator for parseSkillRepoSpecs — or duplicate that logic.
    // Better: extract parseSkillRepoSpecs to a shared utility, or just duplicate it here
    // since it's 5 lines of Jackson parsing.

    public AgentSkillService(AgentDefinitionRepository agentRepo,
                             SkillFactory skillFactory,
                             WorkspaceService workspaceService,
                             WorkspacePathResolver workspaceResolver,
                             UserMarketplaceRegistry marketplaceRegistry) {
        this.agentRepo = agentRepo;
        this.skillFactory = skillFactory;
        this.workspaceService = workspaceService;
        this.workspaceResolver = workspaceResolver;
        this.marketplaceRegistry = marketplaceRegistry;
    }

    /** List skills installed in the agent's workspace skills/ directory. */
    public List<WorkspaceSkillVO> listWorkspaceSkills(String ownerId, Long agentDefId) {
        // 用 workspaceService 的 list() 方法遍历 skills/ 子目录
        var nodes = workspaceService.list(ownerId, agentDefId);
        List<WorkspaceSkillVO> result = new ArrayList<>();
        for (var node : nodes) {
            if (!"dir".equals(node.type())) continue;
            if (!node.relPath().equals(SKILLS_DIR)) continue;
            // 读 SKILL.md 第一行拿 description；太复杂就返回空
        }
        // 如果 workspaceService.list 只返回顶层，则直接遍历 <root>/skills/* 子目录
        Path skillsDir = workspaceResolver.agentRoot(ownerId, agentDefId).resolve(SKILLS_DIR);
        // ...
        return result;
    }

    /** Install a skill from the agent's configured skill repository by index. */
    public WorkspaceSkillVO installFromRepository(String ownerId, Long agentDefId, InstallFromRepoReq req) {
        // 1. Lookup agent def
        AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, ownerId)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
        
        // 2. Parse skill repo specs
        List<SkillRepoSpec> repos = parseSkillRepoSpecs(def.getSkillRepositoriesJson());
        if (req.repoIndex() < 0 || req.repoIndex() >= repos.size()) {
            throw new IllegalArgumentException("repoIndex out of range: " + req.repoIndex());
        }
        SkillRepoSpec spec = repos.get(req.repoIndex());
        
        // 3. Instantiate the repo
        Path workspace = workspaceResolver.agentRoot(ownerId, agentDefId);
        AgentSkillRepository repo = skillFactory.instantiate(spec.type(), spec.props(), workspace);
        
        // 4. Fetch the skill
        AgentSkill skill = repo.getSkill(req.skillName());
        if (skill == null) {
            throw new NotFoundException("skill not found in repository: " + req.skillName());
        }
        
        // 5. Determine target name
        String targetName = (req.targetName() != null && !req.targetName().isBlank())
                ? req.targetName() : skill.getName();
        validateSkillName(targetName);
        
        // 6. Check overwrite
        String skillDir = SKILLS_DIR + "/" + targetName;
        Path skillMarkdown = workspaceResolver.resolve(ownerId, agentDefId, skillDir + "/SKILL.md");
        boolean exists = java.nio.file.Files.exists(skillMarkdown);
        if (exists && !Boolean.TRUE.equals(req.overwrite())) {
            throw new IllegalArgumentException("workspace skill already exists: " + targetName
                    + " (set overwrite=true to replace)");
        }
        
        // 7. Write SKILL.md
        String markdown = skill.getSkillContent();
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalStateException("repository returned empty SKILL.md for: " + req.skillName());
        }
        workspaceService.write(ownerId, agentDefId, skillDir + "/SKILL.md", markdown);
        
        // 8. Write resources
        Map<String, String> resources = skill.getResources();
        if (resources != null) {
            for (Map.Entry<String, String> entry : resources.entrySet()) {
                workspaceService.write(ownerId, agentDefId, skillDir + "/" + entry.getKey(), entry.getValue());
            }
        }
        
        // 9. Write install meta
        writeInstallMeta(ownerId, agentDefId, skillDir, "repository", spec.type(), skill.getName());
        
        return new WorkspaceSkillVO(targetName, skill.getDescription(), "repository", System.currentTimeMillis());
    }

    /** Install a skill from a user-configured marketplace. */
    public WorkspaceSkillVO installFromMarketplace(String ownerId, Long agentDefId, MarketplaceInstallReq req) {
        var mp = marketplaceRegistry.find(ownerId, req.marketplaceId())
                .orElseThrow(() -> new NotFoundException("marketplace not found: " + req.marketplaceId()));
        
        var content = mp.fetch(req.skillName());
        if (content == null) {
            throw new NotFoundException("skill not found in marketplace: " + req.skillName());
        }
        
        String targetName = (req.targetName() != null && !req.targetName().isBlank())
                ? req.targetName() : content.name();
        validateSkillName(targetName);
        
        String skillDir = SKILLS_DIR + "/" + targetName;
        Path skillMarkdown = workspaceResolver.resolve(ownerId, agentDefId, skillDir + "/SKILL.md");
        boolean exists = java.nio.file.Files.exists(skillMarkdown);
        if (exists && !Boolean.TRUE.equals(req.overwrite())) {
            throw new IllegalArgumentException("workspace skill already exists: " + targetName);
        }
        
        workspaceService.write(ownerId, agentDefId, skillDir + "/SKILL.md", content.markdown());
        if (content.resources() != null) {
            for (Map.Entry<String, String> entry : content.resources().entrySet()) {
                workspaceService.write(ownerId, agentDefId, skillDir + "/" + entry.getKey(), entry.getValue());
            }
        }
        
        writeInstallMeta(ownerId, agentDefId, skillDir, "marketplace", mp.type(), content.name());
        return new WorkspaceSkillVO(targetName, content.description(), "marketplace", System.currentTimeMillis());
    }

    /** Delete a workspace skill. */
    public void deleteWorkspaceSkill(String ownerId, Long agentDefId, String name) {
        validateSkillName(name);
        String skillDir = SKILLS_DIR + "/" + name;
        workspaceService.delete(ownerId, agentDefId, skillDir + "/SKILL.md");
        workspaceService.delete(ownerId, agentDefId, skillDir + "/_install.meta.json");
    }

    private void writeInstallMeta(String ownerId, Long agentDefId, String skillDir,
                                   String source, String sourceType, String originalName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", source);
        meta.put("sourceType", sourceType);
        meta.put("originalName", originalName);
        meta.put("installedAt", Instant.now().toString());
        try {
            String json = JsonUtil.mapper().writeValueAsString(meta);
            workspaceService.write(ownerId, agentDefId, skillDir + "/_install.meta.json", json);
        } catch (Exception e) {
            log.warn("failed to write install meta for {}", skillDir, e);
        }
    }

    private void validateSkillName(String name) {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("invalid skill name: " + name);
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
}
```

- [ ] **Step 4: 写 AgentSkillsController**

`src/main/java/io/agentscope/builder/saton/skill/AgentSkillsController.java`：

WebFlux + sa-token reactor 风格，沿用已有的 `inSaContext` 模式。

```java
package io.agentscope.builder.saton.skill;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.agent.AgentDefinitionRepository;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.skill.dto.InstallFromRepoReq;
import io.agentscope.builder.saton.skill.dto.MarketplaceInstallReq;
import io.agentscope.builder.saton.skill.dto.WorkspaceSkillVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{agentId}/skills")
public class AgentSkillsController {

    private final AgentSkillService skillService;
    private final AgentDefinitionRepository agentRepo;

    public AgentSkillsController(AgentSkillService skillService,
                                  AgentDefinitionRepository agentRepo) {
        this.skillService = skillService;
        this.agentRepo = agentRepo;
    }

    @GetMapping("/workspace")
    public Mono<List<WorkspaceSkillVO>> listWorkspaceSkills(
            @PathVariable("agentId") Long agentDefId, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.listWorkspaceSkills(me, agentDefId);
        });
    }

    @DeleteMapping("/workspace/{name}")
    public Mono<Void> deleteWorkspaceSkill(
            @PathVariable("agentId") Long agentDefId,
            @PathVariable("name") String name,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            skillService.deleteWorkspaceSkill(me, agentDefId, name);
            return null;
        }).then(Mono.empty());
    }

    @PostMapping("/workspace/install")
    public Mono<WorkspaceSkillVO> installFromRepository(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody InstallFromRepoReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.installFromRepository(me, agentDefId, req);
        });
    }

    @PostMapping("/workspace/marketplace-install")
    public Mono<WorkspaceSkillVO> installFromMarketplace(
            @PathVariable("agentId") Long agentDefId,
            @RequestBody MarketplaceInstallReq req,
            ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            assertOwns(agentDefId, me);
            return skillService.installFromMarketplace(me, agentDefId, req);
        });
    }

    private void assertOwns(Long agentDefId, String userId) {
        if (!agentRepo.existsByIdAndOwnerId(agentDefId, userId)) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
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

注意：如果在 `AgentDefinitionRepository` 中没有 `existsByIdAndOwnerId`，加一个：
```java
boolean existsByIdAndOwnerId(Long id, String ownerId);
```

- [ ] **Step 5: 验证 AgentDefinitionRepository 方法**

Read `AgentDefinitionRepository.java` 确认 `existsByIdAndOwnerId` 和 `findByIdAndOwnerId` 存在。不存在则加。

- [ ] **Step 6: 写 AgentSkillsFlowTest（5 tests）**

`src/test/java/io/agentscope/builder/saton/skill/AgentSkillsFlowTest.java`：

`@SpringBootTest(webEnvironment = RANDOM_PORT)`，沿用 ChatFlowTest/ChatStreamFlowTest 的 setup 风格（login + seed agent）。

测试：
1. `installFromRepoWithoutRepoSpecReturns4xx()` — POST workspace/install on agent with no skill_repositories_json → 4xx (IAE→400 per §12.17)
2. `installFromMarketplaceWithoutMarketplaceReturns404()` — POST marketplace-install with nonexistent marketplaceId → 404
3. `listWorkspaceSkillsReturnsOk()` — GET workspace → 200 + body is list (空列表)
4. `deleteWorkspaceSkillNonExistentReturns4xx()` — DELETE workspace/some-skill → 4xx (WorkspaceService 抛 NotFoundException)
5. `installFromRepoWithInvalidReqReturns4xx()` — POST workspace/install with empty body → 400

这些测试在 classpath 没有 git/nacos 依赖时验证边界条件，不验证实际安装。如果需要验证实际安装，需要在 AfterEach 清理写到 workspace 的文件（用 `@TempDir` 类级别 workspace root + `WorkspaceConfig` 覆盖）。

由于 WorkspaceConfig 和路径配置可能复杂，这 5 个 flow 测试只要验证：
- 认证/权限校验（401 → 通过 header satoken）
- 输入校验（400 → 空请求体、非法参数）
- 资源不存在（404 → marketplace/skill 不存在）

- [ ] **Step 7: 跑 AgentSkillsFlowTest**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=AgentSkillsFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 5 tests PASS

- [ ] **Step 8: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 150 PASS（148 + 5 - 3？仔细算一下）

复算：
- M7 末 = 136
- Task 2: +10 个注册表/市场测试 → 146
- Task 3: +2 个浏览端点测试 → 148
- Task 4: +5 个 skill flow 测试 → 153

等等，我前面说预计 150，现在看是 136+10+2+5 = 153。准确数字以实施时实际结果为准。

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/skill/ `
        src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java `
        src/test/java/io/agentscope/builder/saton/skill/
git commit -m "feat(m8): AgentSkillsController + workspace install from repo/marketplace"
```

---

### Task 5: Tag + spec §12 更新

**Files:**
- Modify: `docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`（outer repo）

**目标**：打 tag 并更新 spec 的 §12 实施踩坑笔记。

- [ ] **Step 1: 给 spec 加 §12.21-12.22**

Read 当前 outer spec，确认 §12.20 位置和 §13 之间的空白区域。追加：

```markdown
### 12.21 [M8] GitBuilderMarketplace / NacosBuilderMarketplace 依赖反射

两个市场实现都通过反射加载底层 SDK（`GitSkillRepository` / Nacos maintainer client），
构造阶段捕获 `ClassNotFoundException` → `IllegalStateException`。
`UserMarketplaceRegistry` 在创建实例时吞掉 ISE（`log.warn` 后跳过），
不阻塞 marketplace 的浏览/列表 —— 跟 M7-4 orchestrator 的 skill/hook try/catch 模式一致。

### 12.22 [M8] AgentSkillsController 的安全边界

目前 `AgentSkillsController` 用 `agentRepo.existsByIdAndOwnerId` 校验 owner 身份，
尚未接入 spec §6.3 的 `AgentAccessGuard` 三层 ACL（EDIT/RUN/CLONE）。
M9 引入了 `AgentAccessGuard` 后需要替换这里的校验（见 M9 plan）。
届时还需同步更新 `WorkspaceService` 的 owner 校验 —— 共享 agent 的 RUN/EDIT 用户
通过不同的 ownerId 透传可能导致 workspace path 错位。
```

- [ ] **Step 2: 提交 spec 更新到 outer repo**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java
git add docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md
git commit -m "docs(spec): §12.21-12.22 — M8 反射市场 + AgentSkillsController 安全边界"
```

- [ ] **Step 3: 给 inner repo 打 tag**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
git tag m8-marketplace-repository-done
```

- [ ] **Step 4: 验证 tag**

```powershell
git tag -l "m8*"
```

Expected: `m8-marketplace-repository-done`

---

## 自检（Self-Review）

1. **Spec coverage** — M8 = "git/nacos marketplace + skill 安装到 workspace" / "UI 上从市场装 skill"
   - SPI + Git/Naocs 实现（Task 1）→ 市场可接入 ✅
   - UserMarketplaceRegistry（Task 2）→ DB ↔ 运行时实例联动 ✅
   - Marketplace browsing endpoints（Task 3）→ 前端能浏览市场内容 ✅
   - Skill install to workspace（Task 4）→ 从仓库/市场装到 agent workspace ✅

2. **类型一致性**
   - `SkillMarketplaceController` 复用已有的 `inSaContext` helper ✅
   - `AgentSkillsController` 沿用同样 `inSaContext` 模式 ✅
   - `WorkspaceService.write(ownerId, agentDefId, relPath, content)` 签名一致 ✅
   - `MarketSkillContent` 的 record shape 匹配原始 builder 的 `MarketSkillContent` ✅

3. **关键风险**
   - Nacos SDK 不公开可用 → 反射构建 + 明确的 ISE + hint message ✅
   - GitSkillRepository 不在 classpath → 反射 + ISE + hint（同 GitSkillRepoType） ✅
   - UserMarketplaceRegistry 构造时抛 ISE 不应阻塞整个 app → log.warn 跳过 ✅
   - AgentSkillsController 当前只做 owner 校验（ACL 到 M9 统一加）✅
   - WorkspaceService 依赖 resolver 做越界防御 ✅
   - 预计 150-153 测试（基准 136 + task 实施实际增量）✅
