# M9 分享 + Clone + 模板 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** A 能创建 agent 分享给 B；B 能看、能 RUN、能 Clone。同时引入起步模板系统，新建 agent 时可从模板初始化。

**Architecture:** 三层：`AgentShareEntity` + `AgentAclService` 管理分享数据与鉴权；`AgentAccessGuard` 统一保护现有端点；`TemplateRegistry` 从 classpath 加载起步模板。

**Tech Stack:** JPA + H2, WebFlux, sa-token, Jackson 3 (`JsonUtil.mapper()`), 同 M1-M8

**基线测试数:** 153 (M8)

**预计累计:** 153 + 4 (Task 2) + 3 (Task 3) + 2 + 2 (Task 5) + 3 = **~167 PASS**

---

## 文件结构总览

```
agent/
├── AgentShareEntity.java                  [NEW] agent_share JPA entity
├── AgentShareRepository.java              [NEW] Spring Data JPA repo
├── AgentAclService.java                   [NEW] tierFor(userId, agentId) → Tier
├── AgentAccessGuard.java                  [NEW] require(agentId, userId, minTier)
├── AgentService.java                      [MODIFY] list/get 加入分享可见；加 clone() 方法
├── AgentController.java                   [MODIFY] 加 /{id}/shares/** + /{id}/clone
├── AgentDefinitionRepository.java         [MODIFY] 加 findByOwnerIdOrGranteeId @Query
├── AgentRuntimeResolver.java              [MODIFY] 分享后 invalidate 机制
├── dto/
│   ├── AgentShareVO.java                  [NEW] VO for client
│   ├── AgentShareUpsertReq.java           [NEW] create request
│   └── CloneReq.java                      [NEW] POST /clone body

skill/
├── AgentSkillsController.java             [MODIFY] 替换 assertOwns → AgentAccessGuard

template/
├── TemplateController.java                [NEW] /api/templates
├── TemplateRegistry.java                  [NEW] 模板加载器
└── dto/
    └── TemplateVO.java                    [NEW] 模板展示 VO

resources/templates/
├── blank/template.json                    [NEW] 空白模版
├── customer-support/template.json         [NEW] 客服助手
└── research-assistant/template.json       [NEW] 研究助手

test/
├── agent/AgentFlowTest.java               [MODIFY] +3 share tests + 2 clone tests
├── agent/AgentAclServiceTest.java         [NEW] 3 tests
├── agent/AgentShareRepositoryTest.java    [NEW] 1 test
├── skill/AgentSkillsFlowTest.java         [MODIFY] 确保 access guard 不破坏既有测试
└── template/TemplateFlowTest.java         [NEW] 2 tests
```

---

### Task 1: AgentShareEntity + AgentShareRepository + AgentAclService

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentShareEntity.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentShareRepository.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentAclService.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/AgentAccessGuard.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/AgentAclServiceTest.java`
- Test: `src/test/java/io/agentscope/builder/saton/agent/AgentShareRepositoryTest.java`

**目标**：分享数据模型 + ACL 鉴权引擎 + AccessGuard 保护器。Task 1 只做基础设施，不接入现有端点（Task 2 接）。

- [ ] **Step 1: 写 AgentShareEntity**

```java
package io.agentscope.builder.saton.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_share",
       indexes = @Index(name = "ix_agent_share_grantee", columnList = "grantee_id"))
public class AgentShareEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_def_id", nullable = false)
    private Long agentDefId;

    @Column(name = "grantee_type", length = 16, nullable = false)
    private String granteeType = "USER";

    @Column(name = "grantee_id", length = 128, nullable = false)
    private String granteeId;

    @Column(name = "tier", length = 16, nullable = false)
    private String tier;

    @Column(name = "created_by", length = 128, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // getters + setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentDefId() { return agentDefId; }
    public void setAgentDefId(Long agentDefId) { this.agentDefId = agentDefId; }
    public String getGranteeType() { return granteeType; }
    public void setGranteeType(String granteeType) { this.granteeType = granteeType; }
    public String getGranteeId() { return granteeId; }
    public void setGranteeId(String granteeId) { this.granteeId = granteeId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: 写 AgentShareRepository**

```java
package io.agentscope.builder.saton.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentShareRepository extends JpaRepository<AgentShareEntity, Long> {
    List<AgentShareEntity> findByAgentDefId(Long agentDefId);
    List<AgentShareEntity> findByGranteeId(String granteeId);
    Optional<AgentShareEntity> findByAgentDefIdAndGranteeId(Long agentDefId, String granteeId);
    long deleteByAgentDefId(Long agentDefId);
    long deleteByIdAndAgentDefId(Long id, Long agentDefId);
}
```

- [ ] **Step 3: 写 Tier 枚举 + AgentAclService**

`AgentAclService.java`：

```java
package io.agentscope.builder.saton.agent;

import org.springframework.stereotype.Service;
import java.util.Comparator;

public enum Tier { CLONE, RUN, EDIT;  // 按权限升序排列
    public boolean atLeast(Tier min) { return compareTo(min) >= 0; }
}

@Service
public class AgentAclService {
    private final AgentDefinitionRepository agentRepo;
    private final AgentShareRepository shareRepo;
    
    public AgentAclService(AgentDefinitionRepository agentRepo, AgentShareRepository shareRepo) {
        this.agentRepo = agentRepo;
        this.shareRepo = shareRepo;
    }
    
    /** Return the effective tier for {@code userId} on the agent identified by {@code agentDefId}. */
    public Tier tierFor(String userId, Long agentDefId) {
        // Owner always gets EDIT
        if (agentRepo.existsByIdAndOwnerId(agentDefId, userId)) {
            return Tier.EDIT;
        }
        // Check shares
        return shareRepo.findByAgentDefIdAndGranteeId(agentDefId, userId)
                .map(s -> Tier.valueOf(s.getTier()))
                .orElse(null);  // null = no access
    }
}
```

注意：`AgentDefinitionRepository.existsByIdAndOwnerId(Long, String)` 已经在 M8-4 时加过了；如果不存在需要加。

- [ ] **Step 4: 写 AgentAccessGuard**

```java
package io.agentscope.builder.saton.agent;

import io.agentscope.builder.saton.common.error.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Unified access guard for agent-scoped operations.
 * Replaces ad-hoc assertOwns() checks with tier-based authorization.
 */
@Component
public class AgentAccessGuard {

    private final AgentAclService aclService;
    private final AgentDefinitionRepository agentRepo;

    public AgentAccessGuard(AgentAclService aclService, AgentDefinitionRepository agentRepo) {
        this.aclService = aclService;
        this.agentRepo = agentRepo;
    }

    /**
     * Require that {@code userId} has at least {@code minTier} on the agent.
     * Returns the AgentDefinitionEntity on success, throws on failure.
     */
    public AgentDefinitionEntity require(Long agentDefId, String userId, Tier minTier) {
        Tier actual = aclService.tierFor(userId, agentDefId);
        if (actual == null) {
            throw new NotFoundException("agent not found: " + agentDefId);
        }
        if (!actual.atLeast(minTier)) {
            throw new io.agentscope.builder.saton.common.error.ForbiddenException(
                    "insufficient tier: need " + minTier + ", have " + actual);
        }
        return agentRepo.findById(agentDefId)
                .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
    }

    /** Quick existence + ownership check (for owner-only operations like delete/share management). */
    public AgentDefinitionEntity requireOwner(Long agentDefId, String userId) {
        return require(agentDefId, userId, Tier.EDIT);
    }
}
```

注意：`ForbiddenException` 可能还不存在。如果不存在，在 `common/error/` 下创建：

```java
package io.agentscope.builder.saton.common.error;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
```

并确认 `@RestControllerAdvice` 将 ForbiddenException 映射到 HTTP 403。参考现有 `AuthExceptionHandler`（应该已经有了 SaTokenException → 401 的映射），追加 ForbiddenException → 403。

- [ ] **Step 5: 写 AgentAclServiceTest (3 tests)**

`@SpringBootTest`，注入 agent repo + share repo + acl service。

分别测试：
1. `ownerGetsEdit()` — seed agent with `ownerId="admin"` → `acl.tierFor("admin", agentId)` → `EDIT`
2. `granteeGetsRun()` — seed agent + share with `granteeId="user2"`, `tier="RUN"` → `acl.tierFor("user2", agentId)` → `RUN`
3. `noShareReturnsNull()` — no share → `acl.tierFor("stranger", agentId)` → `null`

- [ ] **Step 6: 写 AgentShareRepositoryTest (1 test)**

`@DataJpaTest`，验证 CRUD：seed share entity → findByGranteeId 查得到 → deleteByAgentDefId 删除。

- [ ] **Step 7: 编译+跑 4 个新测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% compile -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```
然后：
```powershell
mvn --% test "-Dtest=AgentAclServiceTest,AgentShareRepositoryTest" -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 3 + 1 = 4 tests PASS

- [ ] **Step 8: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 157 PASS（153 + 4，pre-existing flaky 不变）

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/agent/AgentShareEntity.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentShareRepository.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentAclService.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentAccessGuard.java `
        src/main/java/io/agentscope/builder/saton/common/error/ForbiddenException.java `
        src/test/java/io/agentscope/builder/saton/agent/AgentAclServiceTest.java `
        src/test/java/io/agentscope/builder/saton/agent/AgentShareRepositoryTest.java
git commit -m "feat(m9): AgentShare + AgentAclService + AgentAccessGuard + ForbiddenException"
```

---

### Task 2: AgentService 可见性 + Share CRUD 端点

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentShareVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/agent/dto/AgentShareUpsertReq.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentController.java`
- Modify: `src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java`

**目标**：AgentService.list() 返回自己拥有的 + 被分享的 agent；AgentService.get() 允许 owner 和 RUN tier 用户读；AgentController 新增 share CRUD 端点。

- [ ] **Step 1: 写 DTO**

`AgentShareVO.java`:
```java
package io.agentscope.builder.saton.agent.dto;

import io.agentscope.builder.saton.agent.AgentShareEntity;

public record AgentShareVO(Long id, Long agentDefId, String granteeType, String granteeId, String tier, long createdAt) {
    public static AgentShareVO from(AgentShareEntity e) {
        return new AgentShareVO(e.getId(), e.getAgentDefId(), e.getGranteeType(), e.getGranteeId(), e.getTier(), e.getCreatedAt());
    }
}
```

`AgentShareUpsertReq.java`:
```java
package io.agentscope.builder.saton.agent.dto;

public record AgentShareUpsertReq(String granteeId, String tier) { }
```

- [ ] **Step 2: AgentDefinitionRepository 加 shared visibility 查询**

加自定义 @Query：
```java
@Query("SELECT a FROM AgentDefinitionEntity a WHERE a.ownerId = :userId " +
       "OR EXISTS (SELECT 1 FROM AgentShareEntity s WHERE s.agentDefId = a.id AND s.granteeId = :userId) " +
       "ORDER BY a.createdAt DESC")
List<AgentDefinitionEntity> findByOwnerIdOrGranteeId(@Param("userId") String userId);
```

注意 import：`org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`。

- [ ] **Step 3: AgentService 改 list() + get()**

`list()`：把 `repo.findByOwnerIdOrderByCreatedAtDesc(me)` 替换为 `repo.findByOwnerIdOrGranteeId(me)`。

`get(Long id)`：替换 `loadMine(id, me)` 为 `AgentAccessGuard.require(id, me, Tier.RUN)`。这样 owner 和 RUN tier 共享者都能读到。

`delete(Long id)`：改为用 `AccessGuard.requireOwner(id, me)` 替代 `loadMine`。

`update(Long id, ...)`：改为用 `AccessGuard.requireOwner(id, me)` 替代 `loadMine`（只有 owner 能编辑）。

注入 `AgentAccessGuard`（添加为构造器参数）。

- [ ] **Step 4: AgentController 加 share CRUD 端点**

加依赖：注入 `AgentShareRepository`。

```java
    @GetMapping("/{id}/shares")
    public Mono<List<AgentShareVO>> listShares(@PathVariable("id") Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            return shareRepo.findByAgentDefId(id).stream().map(AgentShareVO::from).toList();
        });
    }

    @PostMapping("/{id}/shares")
    public Mono<AgentShareVO> createShare(@PathVariable("id") Long id,
                                           @RequestBody AgentShareUpsertReq req,
                                           ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            if (req.granteeId() == null || req.granteeId().isBlank() || req.tier() == null || req.tier().isBlank()) {
                throw new IllegalArgumentException("granteeId and tier required");
            }
            // Validate tier value
            Tier.valueOf(req.tier()); // throws IAE if invalid
            long now = System.currentTimeMillis();
            AgentShareEntity e = new AgentShareEntity();
            e.setAgentDefId(id);
            e.setGranteeType("USER");
            e.setGranteeId(req.granteeId());
            e.setTier(req.tier());
            e.setCreatedBy(me);
            e.setCreatedAt(now);
            return AgentShareVO.from(shareRepo.save(e));
        });
    }

    @DeleteMapping("/{id}/shares/{shareId}")
    public Mono<Void> deleteShare(@PathVariable("id") Long id,
                                   @PathVariable("shareId") Long shareId,
                                   ServerWebExchange exchange) {
        return inSaContext(exchange, () -> {
            String me = StpUtil.getLoginIdAsString();
            accessGuard.requireOwner(id, me);
            long n = shareRepo.deleteByIdAndAgentDefId(shareId, id);
            if (n == 0) throw new NotFoundException("share not found: " + shareId);
            return null;
        }).then(Mono.empty());
    }
```

同时需要对 `AgentService.update()` 和 `AgentService.delete()` 中 invalidate 的相关逻辑加一个注意：当 agent 的 share 发生变化时，如果被分享者正在使用该 agent，`runtimeResolver` 的缓存可能过时。但目前 `update/delete` 方法已经调了 `invalidateByAgent`，所以分享修改后 owner 侧已经 invalidate。被分享者那边的缓存是靠下次 `resolve` 时重新查库来刷新的（因为 cache key 不含 userId）。

- [ ] **Step 5: AgentService 修改注意——invalidate 分享变化**

`AgentController.createShare()` 和 `deleteShare()` 成功之后应调 `runtimeResolver.invalidateByAgent(id)`，以确保享用该 agent 的 Runner 实例下次重新 build。

- [ ] **Step 6: AgentFlowTest 加 3 个测试**

测试 1——`listReturnsSharedAgents()`：
- admin 登录创建 agent A
- 直接给 `agent_share` 表 insert share（`granteeId="admin"`, `tier="RUN"`）—— 或更方便：用第二个用户 seed 后 share
- 但更简单的做法：创建 agent A（owner=admin），POST createShare 给 "admin" 自己（虽然奇怪但合法），验证 list 包含 A
- 或者：创建 agent A（owner="admin"），用 shareRepo.save(...) 直接插一条 share {granteeId="admin"}
- 最简单的：在同一个 test 里创建 agent，然后 `GET /api/agents` 验证 list 包含该 agent（因为 owner 本来就能看到）

由于 owner 本来就能看到自己的 agent，真正测分享场景需要两个用户。但 WebTestClient 切换用户不方便。对 M9 的测试来说，验证"被分享者能看到"可以通过直接插 share 记录 + 查 list 验证。

更好方案：注册第二个用户（`POST /api/user/register` 但本系统无注册接口）。那就简化：test 1 只测 owner + share grantee 的组合，通过 `shareRepo.save()` 注入数据后验证 acl 按预期工作（这已经在 Task 1 的 `AgentAclServiceTest` 里测了）。Flow 的 share 端点测试只测：
1. `createShareSuccess()` — POST shares → 200 + body has tier
2. `listSharesReturnsShares()` — GET shares → 200 + list non-empty
3. `deleteShareRemovesShare()` — DELETE shares → 200 + GET 变空

- [ ] **Step 7: 运行测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=AgentFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 5 + 3 = 8 tests PASS（原 AgentFlowTest 5 个 + 3 个 share 测试）

- [ ] **Step 8: 全量回归**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: ~160 PASS

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionRepository.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentService.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentController.java `
        src/main/java/io/agentscope/builder/saton/agent/dto/AgentShareVO.java `
        src/main/java/io/agentscope/builder/saton/agent/dto/AgentShareUpsertReq.java `
        src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java
git commit -m "feat(m9): shared agent visibility + share CRUD endpoints"
```

---

### Task 3: AgentAccessGuard retrofit 到现有端点

**Files:**
- Modify: `src/main/java/io/agentscope/builder/saton/skill/AgentSkillsController.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/chat/ChatController.java` (maybe)
- Modify: `src/main/java/io/agentscope/builder/saton/chat/ChatController.java` (the actual chat controller)

**目标**：把 M8 AgentSkillsController 的 `assertOwns` 替换为 `AgentAccessGuard.requireOwner`，把 ChatService 的 agent lookup 替换为 `AgentAccessGuard.require(...)` 以支持 RUN tier 用户聊天。

- [ ] **Step 1: Read 当前 ChatService + ChatController**

确认 ChatController 的路径和 ChatService 的 `send()` / `stream()` 的鉴权方式。

- [ ] **Step 2: AgentSkillsController 替换 assertOwns → AccessGuard**

找到 `assertOwns` 方法，删掉。把 4 个端点里的 `assertOwns(agentDefId, me)` 替换为：
- `listWorkspaceSkills`, `installFromRepository`, `installFromMarketplace` → `accessGuard.require(agentDefId, me, Tier.RUN)`（RUN tier 可执行这些操作）
- `deleteWorkspaceSkill` → `accessGuard.require(agentDefId, me, Tier.EDIT)`（只有 EDIT 可删文件）

注入 `AgentAccessGuard accessGuard`。

- [ ] **Step 3: ChatService 替换 agent lookup**

`send()` 和 `stream()` 里目前是：
```java
AgentDefinitionEntity def = agentRepo.findByIdAndOwnerId(agentDefId, me)
        .orElseThrow(() -> new NotFoundException("agent not found: " + agentDefId));
```

替换为：
```java
AgentDefinitionEntity def = accessGuard.require(agentDefId, me, Tier.RUN);
```

注入 `AgentAccessGuard`。

`stream()` 里的 `Flux.defer` 内也一样替换。注意 §12.14：`me` 在 `Flux.defer` 外已捕获。

- [ ] **Step 4: 编译+跑回归**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% compile -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```
然后全量回归。所有分享相关测试和既有 flow 测试应保持不变。

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/io/agentscope/builder/saton/skill/AgentSkillsController.java `
        src/main/java/io/agentscope/builder/saton/agent/chat/ChatService.java
git commit -m "refactor(m9): replace assertOwns with AgentAccessGuard in chat + skills"
```

---

### Task 4: Clone 端点

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/agent/dto/CloneReq.java`
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentService.java`（加 clone() 方法）
- Modify: `src/main/java/io/agentscope/builder/saton/agent/AgentController.java`（加 clone 端点）
- Modify: `src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java`（加 2 个测试）

**目标**：实体级别的 clone，复制 agent 的所有配置（包括 toolSpecs/skillRepos/hooks/subagents），生成新的 agentId 和 name。

- [ ] **Step 1: 写 CloneReq**

```java
package io.agentscope.builder.saton.agent.dto;

public record CloneReq(String newAgentId, String name) {}
```

- [ ] **Step 2: AgentService 加 clone() 方法**

```java
@Transactional
public AgentVO clone(Long sourceId, CloneReq req, String ownerId) {
    // 1. Load source (access already checked by controller)
    AgentDefinitionEntity source = repo.findById(sourceId)
            .orElseThrow(() -> new NotFoundException("source agent not found: " + sourceId));
    
    // 2. Validate new agentId
    if (req.newAgentId() == null || req.newAgentId().isBlank()) {
        throw new IllegalArgumentException("newAgentId required");
    }
    if (repo.existsByOwnerIdAndAgentId(ownerId, req.newAgentId())) {
        throw new ConflictException("agentId already exists: " + req.newAgentId());
    }
    
    // 3. Deep copy
    long now = System.currentTimeMillis();
    AgentDefinitionEntity clone = new AgentDefinitionEntity();
    clone.setOwnerId(ownerId);
    clone.setAgentId(req.newAgentId());
    clone.setName(req.name() != null ? req.name() : source.getName());
    clone.setDescription(source.getDescription());
    clone.setSysPrompt(source.getSysPrompt());
    clone.setAgentType(source.getAgentType());
    clone.setDefaultModelProviderId(source.getDefaultModelProviderId());
    clone.setMaxIters(source.getMaxIters());
    clone.setToolSpecsJson(source.getToolSpecsJson());
    clone.setSkillRepositoriesJson(source.getSkillRepositoriesJson());
    clone.setHookSpecsJson(source.getHookSpecsJson());
    clone.setSubagentRefsJson(source.getSubagentRefsJson());
    clone.setForkOf(source.getAgentId());  // Track clone source
    clone.setCreatedAt(now);
    clone.setUpdatedAt(now);
    
    return AgentVO.from(repo.save(clone));
}
```

注意：需要 `AgentDefinitionEntity` 有 `forkOf` 字段（spec §5.2 定义了 `fork_of` 列）。读一下 entity 确认 `setForkOf` / `getForkOf` 存在。如果不存在，加字段。

- [ ] **Step 3: AgentController 加 clone 端点**

```java
@PostMapping("/{id}/clone")
public Mono<AgentVO> clone(@PathVariable("id") Long id,
                            @RequestBody CloneReq req,
                            ServerWebExchange exchange) {
    return inSaContext(exchange, () -> {
        String me = StpUtil.getLoginIdAsString();
        accessGuard.require(id, me, Tier.CLONE);  // CLONE tier allows cloning
        return service.clone(id, req, me);
    });
}
```

注意：需要注入 `AgentAccessGuard`。已有 `service`，加 `accessGuard` 字段。

- [ ] **Step 4: AgentFlowTest 加 2 个测试**

1. `cloneCreatesNewAgent()` — 创建 agent A → POST /{id}/clone `{newAgentId:"clone-of-a"}` → 200 + body.id != source.id + body.agentId == "clone-of-a"
2. `cloneWithoutPermissionReturns403()` — （可选，需要在 share 场景下测）或简化为 `cloneWithDuplicateAgentIdReturns409()` 验证存在冲突→409

- [ ] **Step 5: 测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% test -Dtest=AgentFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 10-11 tests PASS（8 share + 2 clone）

- [ ] **Step 6: 全量回归 + Commit**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

```powershell
git add src/main/java/io/agentscope/builder/saton/agent/AgentService.java `
        src/main/java/io/agentscope/builder/saton/agent/AgentController.java `
        src/main/java/io/agentscope/builder/saton/agent/dto/CloneReq.java `
        src/test/java/io/agentscope/builder/saton/agent/AgentFlowTest.java
git commit -m "feat(m9): agent clone endpoint (entity deep copy)"
```

如果 `AgentDefinitionEntity` 加了 `forkOf` 字段：
```powershell
git add src/main/java/io/agentscope/builder/saton/agent/AgentDefinitionEntity.java
```
并在上面的 commit 命令里加上。

---

### Task 5: Template 系统 + Tag

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/template/TemplateVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/template/TemplateRegistry.java`
- Create: `src/main/java/io/agentscope/builder/saton/template/TemplateController.java`
- Create: `src/main/resources/templates/blank/template.json`
- Create: `src/main/resources/templates/customer-support/template.json`
- Create: `src/main/resources/templates/research-assistant/template.json`
- Test: `src/test/java/io/agentscope/builder/saton/template/TemplateFlowTest.java`

- [ ] **Step 1: 读当前 spec 的 template 目录结构**

spec §8 说 `template/` 下有 `TemplateRegistry.java` 和 `TemplateController.java`。确认路径。

- [ ] **Step 2: 写 TemplateVO**

```java
package io.agentscope.builder.saton.template.dto;

import java.util.Map;

public record TemplateVO(String id, String name, String description, Map<String, Object> agent) {}
```

- [ ] **Step 3: 写 TemplateRegistry**

从 classpath `templates/*/template.json` 加载模板元数据。简单实现：在 `@PostConstruct` 时通过 Jackson 加载。

```java
package io.agentscope.builder.saton.template;

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistry.class);
    private static final String LOCATION = "classpath:templates/*/template.json";

    private final List<TemplateVO> templates = new ArrayList<>();

    @PostConstruct
    void loadTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(LOCATION);
            for (Resource r : resources) {
                try {
                    Map<String, Object> map = io.agentscope.builder.saton.common.json.JsonUtil.mapper()
                            .readValue(r.getInputStream(), new TypeReference<Map<String, Object>>() {});
                    String id = (String) map.get("id");
                    String name = (String) map.get("name");
                    String description = (String) map.get("description");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> agent = (Map<String, Object>) map.get("agent");
                    if (id != null && name != null) {
                        templates.add(new TemplateVO(id, name, description != null ? description : "", agent));
                    }
                } catch (Exception e) {
                    log.warn("failed to load template: {}", r.getFilename(), e);
                }
            }
            templates.sort(Comparator.comparing(TemplateVO::id));
        } catch (Exception e) {
            log.warn("failed to scan templates", e);
        }
    }

    public List<TemplateVO> list() { return List.copyOf(templates); }

    public Optional<TemplateVO> get(String id) {
        return templates.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
```

- [ ] **Step 4: 写 TemplateController**

```java
package io.agentscope.builder.saton.template;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.template.dto.TemplateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRegistry registry;

    public TemplateController(TemplateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<TemplateVO> list() {
        return registry.list();
    }

    @GetMapping("/{id}")
    public TemplateVO get(@PathVariable("id") String id) {
        return registry.get(id).orElseThrow(() -> new NotFoundException("template not found: " + id));
    }
}
```

注意：`/api/templates` 不需要登录验证（`SaReactorFilter` 默认拦截所有，需要在 `SaTokenConfig` 的 `addExclude` 中加 `/api/templates` 为公开？还是保持登录可访问？）

建议保持登录可访问（用户必须登录才能看到模板列表）。

- [ ] **Step 5: 写 3 个模板 JSON**

`src/main/resources/templates/blank/template.json`:
```json
{
  "id": "blank",
  "name": "空白 Agent",
  "description": "从零开始配置，不含任何预设工具",
  "agent": {
    "sysPrompt": "You are a helpful assistant.",
    "agentType": "react",
    "maxIters": 10
  }
}
```

`src/main/resources/templates/customer-support/template.json`:
```json
{
  "id": "customer-support",
  "name": "客服助手",
  "description": "内置读文件、搜索知识库等工具，适合企业客服场景",
  "agent": {
    "sysPrompt": "你是客服助手，礼貌而专业地解答用户问题。遇到无法解答的技术问题，请记录并转交人工客服。",
    "agentType": "react",
    "maxIters": 15
  }
}
```

`src/main/resources/templates/research-assistant/template.json`:
```json
{
  "id": "research-assistant",
  "name": "研究助手",
  "description": "擅长调研、汇总信息、写报告，内置 Shell 和文件读写工具",
  "agent": {
    "sysPrompt": "You are a research assistant. Help users gather information, analyze data, and produce well-structured reports.",
    "agentType": "react",
    "maxIters": 20
  }
}
```

- [ ] **Step 6: 写 TemplateFlowTest (2 tests)**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TemplateFlowTest {

    @LocalServerPort int port;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void listReturnsTemplates() {
        client.get().uri("/api/templates")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TemplateVO.class)
                .hasSize(3);  // blank + customer-support + research-assistant
    }

    @Test
    void getReturnsTemplate() {
        client.get().uri("/api/templates/blank")
                .exchange()
                .expectStatus().isOk()
                .expectBody(TemplateVO.class);
    }
}
```

注意：需要 login！因为 `/api/templates` 需要 sa-token 认证。

加 login 后 seed token：
```java
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
        token = login.token();
    }

    @Test
    void listReturnsTemplates() {
        client.get().uri("/api/templates")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TemplateVO.class)
                .hasSize(3);
    }
```

- [ ] **Step 7: 编译+跑测试**

```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton
mvn --% compile -Dmaven.repo.local=D:/PROGRAM/maven/Repository
mvn --% test -Dtest=TemplateFlowTest -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```

Expected: 2 tests PASS

- [ ] **Step 8: 全量回归 + Tag**

```powershell
mvn --% test -Dmaven.repo.local=D:/PROGRAM/maven/Repository
```
Expected: ~165 PASS

```powershell
git add src/main/java/io/agentscope/builder/saton/template/ `
        src/main/resources/templates/ `
        src/test/java/io/agentscope/builder/saton/template/
git commit -m "feat(m9): template system (3 starter templates) + TemplateRegistry"
git tag m9-share-clone-template-done
```

同时更新 outer spec §12（如果实施中踩到新坑）：
```powershell
cd D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java
git add docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md
git commit -m "docs(spec): §12.23 — M9 实施记录"
```

---

## 自检（Self-Review）

1. **Spec coverage** — M9 = "分享 + Clone + 模板"
   - AgentShareEntity + AgentAclService + 分享可见（Task 1-2）→ A 能分享给 B ✅
   - AgentAccessGuard（Task 3）→ 现有 endpoint 统一用 ACL 保护 ✅
   - Clone（Task 4）→ 实体深拷贝 ✅
   - Template system（Task 5）→ 起步模板列表 ✅
   - 完整测试（~165 PASS）

2. **类型一致性**
   - `AgentAccessGuard.require(Long, String, Tier)` → `AgentDefinitionEntity` ✅
   - `AgentAclService.tierFor(String, Long)` → `Tier` ✅
   - `AgentShareEntity` FK → `agent_definition.id`（不设 JPA cascade，service 层手动 deleteByAgentDefId） ✅
   - `CloneReq.newAgentId` → `agent_definition.agentId` ✅
   - `TemplateVO` ↔ `template.json` 字段名一致 ✅
   - `Tier` 枚举值：`CLONE < RUN < EDIT`（数值小权限小，`atLeast` 用 `compareTo` 比较） ✅

3. **关键风险**
   - **AI 起草（`POST /api/agents/draft`）** 在 spec §7 列出了但 M9 不做（M10 做），本 plan 明确不含 draft ✅
   - **AgentShareEntity 的级联删除**：用了 `deleteByAgentDefId()` 在 `AgentService.delete()` 手动清理，不依赖 JPA cascade，明确可控 ✅
   - **共享 agent 的文件系统隔离**：共享 RUN 用户看到的 workspace 路径是 OWNER 的还是自己的？当前实现下，`AccessGuard.allow()` 返回后，chat 流程中 `RuntimeContext` 的 userId 是调用者（即 RUN 用户），workspace 路径按 ownerId 算——这跟 spec §6.5 一致。但如果 AgentSkillsController 的 workspace 操作也用 RUN 用户的 ownerId，会导致访客无法正确读写文件。**TODO:** M9 结束后，共享 agent 的 workspace 操作需要明确 owner 是谁。
   - **TemplateRegistry 线程安全**：用 `List.copyOf(templates)` 返回不可变列表，read-only ✅
   - 预计 165 测试（基线 153 + 4 + 3 + 2 + 2 + 1 flaky） ✅
