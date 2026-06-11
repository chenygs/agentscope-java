# AgentScope Builder (sa-token 版) 设计文档

- **代号**: `agentscope-builder-saton`
- **作者**: chenygs
- **日期**: 2026-06-11
- **状态**: Draft（待 review）
- **参考**: 原项目 `agentscope-examples/agents/agentscope-builder` 及其 `PROJECT_ANALYSIS.md`

---

## 1. 项目定位

`agentscope-builder-saton` 是原 `agentscope-builder` 的"重做"版本。重做范围如下：

- **保留**：1:1 复刻原 builder 的能力 —— Agent 动态创建/编辑、Web Chat (SSE)、Workspace 文件、多轮 Session、子 Agent、动态 Tool/Skill、Skill Marketplace、Agent 分享/ACL、AI 起草、模板、审计日志。
- **删除**：所有 IM 渠道接入（钉钉/企微/飞书/GitHub/GitLab）、`runtime/outbound`、`IdentityLinkStore` 以及 `/dock_*` slash 命令、`agentscope.json` 静态配置文件持久化。
- **替换**：Spring Security + JWT → **sa-token**；Spring Boot 3 + WebFlux → **Spring Boot 4 + WebFlux**。
- **新增**：5 大工厂（Agent / Model / Tool / Skill / Hook）+ 前端可见的"工厂目录" REST；模型/MCP/技能市场/技能仓库等"可复用资源"独立持久化（Hermes / LobeChat 模式）。

**核心心智模型转变**：从原 builder 的"agent 创建时一次性硬编码装配"转向"**资源 + 引用**"模型 —— 模型实例、MCP Server、Skill 仓库都是用户在"管理页"独立维护的持久化资源，agent 只引用它们的 id；聊天时能临时切换模型而无需重建 agent。

---

## 2. 技术栈

| 维度 | 选型 |
|---|---|
| JDK | 21 |
| 构建 | Maven 3.8+ |
| Web 层 | Spring Boot 4.0.x + Spring WebFlux + Spring Framework 7（Jakarta EE 11） |
| 鉴权 | `sa-token-reactor-spring-boot4-starter:1.45.0`（Maven Central 已验证可用，无需排除冲突依赖） |
| 持久化 | Spring Data JPA + Hibernate 7 |
| 数据库 | 默认 H2（file），profile `jdbc` 切 MySQL 8 / PostgreSQL |
| Agent 运行时 | `agentscope-harness`（无修改复用） |
| 配置中心 | 数据库为主，**不再使用 `agentscope.json`** |

依赖示例：

```xml
<properties>
    <spring-boot.version>4.0.0</spring-boot.version>
    <sa-token.version>1.45.0</sa-token.version>
</properties>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-reactor-spring-boot4-starter</artifactId>
    <version>${sa-token.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-core</artifactId>
    <version>${revision}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${revision}</version>
</dependency>
```

---

## 3. 整体架构

### 3.1 一次"用户创建并使用一个 Agent"的完整流程

```
┌─────────────────────────────────────────────────────────────────┐
│ 阶段 A：先添加资源                                              │
│   前端「模型管理」→ POST /api/models { dashscope, apiKey, ... } │
│   前端「MCP 管理」 → POST /api/mcp-servers                       │
│   前端「技能市场」→ POST /api/skill-marketplaces                │
│   后端：ModelFactory.validate(...) → 加密 → INSERT              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 阶段 B：创建 Agent（仅入库，不实例化）                          │
│   POST /api/agents {                                            │
│     name, sysPrompt,                                            │
│     defaultModelProviderId: 7,                                  │
│     toolSpecs: [...], skillRefs: [...], hookSpecs: [...]        │
│   }                                                              │
│   → INSERT INTO agent_definition                                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 阶段 C：聊天时按需实例化 HarnessAgent                          │
│   POST /api/agents/{id}/chat/stream                             │
│     body: { message, overrideModelProviderId? }   ← 可临时切模型│
│                                                                  │
│   AgentRuntimeResolver:                                          │
│     1. 查 agent_definition                                       │
│     2. effectiveModelId = overrideModelProviderId               │
│                          ?? defaultModelProviderId               │
│     3. 用 (agentId, effectiveModelId) 查缓存                    │
│     4. 不命中 → 调 AgentBuildOrchestrator 装配：                 │
│          Model  = ModelFactory.instantiate(modelRow)            │
│          Tools  = ToolFactory.instantiateAll(def.toolSpecs)     │
│          Skills = SkillFactory.loadAll(def.skillRefs)           │
│          Hooks  = HookFactory.createAll(def.hookSpecs)          │
│          HarnessAgent = AgentFactory.create(def.agentType, ...) │
│     5. 缓存                                                      │
│     6. ha.call(message) → SSE 流回前端                           │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 与原 builder 的关键差异

| 原 builder | 新项目 | 说明 |
|---|---|---|
| `agentscope.json` 静态配置 | ❌ 全数据库驱动 | UI 上配 → 立即生效 |
| Channel 扩展（钉钉/飞书/...） | ❌ 全删 | 仅保留 ChatUI |
| `OutboundController` + `OutboundTool` | ❌ 删 | 没有 IM 不需要 |
| `IdentityLinkStore` + `/dock_*` 命令 | ❌ 删 | 同上 |
| Spring Security + JWT | ❌ 换 sa-token |  |
| HarnessGateway 路由表 | ✅ 简化保留 | 只有 chatui 一种渠道 |
| gateKey/sessionKey 双键 | ⚠️ 简化为单 sessionKey | 接口可调，去掉"渠道身份映射"层 |
| BuilderBootstrap 硬编码装配 | ✅ 替换为 5 大工厂 + AgentBuildOrchestrator | 架构核心改进 |
| Model 是 Spring Bean 单例 | ✅ ModelProvider 资源表 | Hermes 模式 |
| MCP / Skill 没有独立资源 | ✅ 新增独立表 | Hermes 模式 |
| AdminUserController | ❌ 删 | 单人视角，无 admin |

---

## 4. 五大工厂（架构核心）

### 4.1 设计原则

5 个工厂走**完全统一的模板**，便于一次掌握、五处复用。每个工厂由 4 部分构成：

```java
// 1. SPI 接口（每种 type 一个实现 bean）
public interface XxxProvider {                    // X = Agent/Model/Tool/Skill/Hook
    String type();                                 // 唯一标识，如 "dashscope"
    XxxMeta meta();                                // 给前端列表用：displayName、描述、参数 JSON Schema
    XxxProduct create(XxxConfig config);           // 真正生产对象
}

// 2. 注册表（Spring 启动时收集所有 XxxProvider bean 按 type 注册）
@Component
public class XxxRegistry {
    private final Map<String, XxxProvider> providers;
    public XxxRegistry(List<XxxProvider> beans) {
        this.providers = beans.stream().collect(toMap(XxxProvider::type, identity()));
    }
    public XxxProvider get(String type);
    public List<XxxMeta> listMetas();              // 前端 GET /api/factories/xxx-types
}

// 3. 工厂门面
@Service
public class XxxFactory {
    public XxxProduct create(String type, Map<String,Object> config) {
        var provider = registry.get(type);
        JsonSchema.validate(provider.meta().schema(), config);
        return provider.create(config);
    }
}

// 4. 内置 Provider 实现（每种 type 一个类，@Component）
```

**模式归类**：
- **工厂模式** = `XxxFactory.create(type, config)` 屏蔽 new 细节
- **策略模式** = 每个 type → 一个 Provider 实现，运行时按 type 选

### 4.2 各工厂的内置 Provider

| 工厂 | 内置 Provider 类型 | 前端使用方式 |
|---|---|---|
| **ModelFactory** | `dashscope` / `openai` / `anthropic` / `gemini` / `ollama` | 下拉框选 → 渲染 apiKey/baseUrl/modelName 表单 |
| **ToolFactory** | `shell-cmd` / `read-file` / `write-file` / `plan-notebook` / `sub-agent` / `mcp-bridge` | 多选框勾选 → 渲染各工具参数表单 |
| **SkillFactory** | `local` / `git` / `nacos`（对应 `SkillRepoType`） | 装/卸 skill 仓库 |
| **HookFactory** | `logging` / `tool-notification` / `audit-jsonl` / `tracing-otel` | 多选勾选 |
| **AgentFactory** | `harness`（默认） / `react` | 高级用户可选 |

> AgentFactory 是真正的"**编排器**"：它接收前 4 个工厂的产物，把它们拼装成 `HarnessAgent`。

### 4.3 给前端用的"工厂目录" REST

```
GET  /api/factories/model-types      → 列出 dashscope/openai/... + JSON Schema
GET  /api/factories/tool-types       → 列出 shell-cmd/... + JSON Schema
GET  /api/factories/skill-repo-types → 列出 local/git/nacos + JSON Schema
GET  /api/factories/hook-types       → 列出 logging/... + JSON Schema
GET  /api/factories/agent-types      → 列出 harness/react + JSON Schema
```

前端可用 [react-jsonschema-form](https://github.com/rjsf-team/react-jsonschema-form) 之类的库自动渲染参数表单 —— **新增一种 Provider = 后端加一个 @Component，前端零改动**。

### 4.4 不做的事情

- **不做**运行时 jar 热加载 / 动态 ClassLoader 隔离（复杂度高、安全难度大）。
- **不做**用户自定义工具（前端写代码当工具 = RCE 风险）。

---

## 5. 数据模型

### 5.1 表清单总览（7 张）

```
─── 用户身份 ───
sys_user                    用户表（sa-token 用）

─── 独立资源（per-owner，UI 上独立管理）───
model_provider              "我的通义千问"、"我的 GPT-4"
mcp_server                  MCP Server 实例
skill_marketplace           技能市场（git/nacos，浏览+安装到 workspace）
skill_repository            技能仓库（git/filesystem，挂为 overlay）

─── Agent ───
agent_definition            agent 配置（子配置全部 JSON 列）
agent_share                 ACL 关联表（唯一需要反向查询的拆表）
```

### 5.2 字段详情

#### `sys_user`

| 列 | 类型 | 说明 |
|---|---|---|
| user_id | VARCHAR(128) PK | sa-token loginId |
| username | VARCHAR(64) UNIQUE | 登录名 |
| password_hash | VARCHAR(128) | BCrypt |
| created_at | BIGINT | |

> 不引入 `roles` 字段，单人视角无角色概念。

#### `model_provider`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| owner_id | VARCHAR(128) | = user_id |
| name | VARCHAR(200) | 显示名："我的千问" |
| type | VARCHAR(32) | "dashscope"/"openai"/... |
| props_json | LOB | 加密存储敏感字段（apiKey/token/password） |
| created_at, updated_at | BIGINT | |

唯一约束 `(owner_id, name)`。

#### `mcp_server`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| owner_id | VARCHAR(128) | |
| name | VARCHAR(200) | |
| transport | VARCHAR(16) | stdio/sse/http |
| props_json | LOB | 命令、URL、headers 等 |
| created_at, updated_at | BIGINT | |

#### `skill_marketplace`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| owner_id | VARCHAR(128) | |
| marketplace_id | VARCHAR(128) | 用户起的业务名 |
| type | VARCHAR(32) | "git"/"nacos" |
| props_json | LOB | 连接参数（url/branch/token） |
| created_at, updated_at | BIGINT | |

唯一约束 `(owner_id, marketplace_id)`。

#### `skill_repository`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| owner_id | VARCHAR(128) | |
| name | VARCHAR(200) | |
| type | VARCHAR(32) | "filesystem"/"git" |
| props_json | LOB | 路径或 git url |
| created_at, updated_at | BIGINT | |

#### `agent_definition`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | row_id |
| owner_id | VARCHAR(128) | |
| agent_id | VARCHAR(128) | 业务唯一标识（同原 builder） |
| name | VARCHAR(200) | |
| description | LOB | |
| sys_prompt | LOB | |
| agent_type | VARCHAR(50) | "harness"（默认）/"react" |
| default_model_provider_id | BIGINT | FK → model_provider.id |
| max_iters | INT | |
| workspace_path | VARCHAR(1024) | |
| tool_specs_json | LOB | `[{type:"shell-cmd",props:{...}}, ...]` |
| skill_refs_json | LOB | `[{repoId:7,name:"git-flow"}, ...]` |
| hook_specs_json | LOB | `[{type:"audit-jsonl",props:{...}}, ...]` |
| subagent_refs_json | LOB | `["agent_002", "agent_005", ...]` |
| skill_repositories_json | LOB | agent 启动时挂载的 skill_repository.id 列表 + 配置 |
| sandbox_mode | VARCHAR(16) | local/sandbox |
| sandbox_scope | VARCHAR(16) | SESSION/USER/AGENT/GLOBAL |
| run_as | VARCHAR(20) | INVOKER/OWNER |
| fork_of | VARCHAR(128) | Clone 来源 |
| created_at, updated_at | BIGINT | |

唯一约束 `(owner_id, agent_id)`；索引 `(owner_id)`、`(agent_id)`。

#### `agent_share`

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| agent_def_id | BIGINT | FK → agent_definition.id（CascadeType.ALL + orphanRemoval）|
| grantee_type | VARCHAR(16) | USER（暂不实现 WORKSPACE）|
| grantee_id | VARCHAR(128) | 被分享给的 user_id |
| tier | VARCHAR(16) | EDIT/RUN/CLONE |
| created_by | VARCHAR(128) | |
| created_at | BIGINT | |

### 5.3 子配置为何用 JSON 列

`tool_specs / skill_refs / hook_specs / subagent_refs` 这 4 个字段都是：

1. **列表**：每个 agent 勾的子项数量不固定
2. **schemaless**：每种 type 的 props 字段不一样（如 shell-cmd 要 allowedCommands、mcp-bridge 要 mcpServerId）
3. **无反向查询需求**：不需要问"哪些 agent 用了 read-file"

**结论**：用 `@Lob` JSON 字符串列存储 —— 跟原 `AgentEntity` 风格一致（参考 `tools_allow_json`、`skill_repositories_json`），保留"加新 type 无需改表结构"的灵活性。

唯一拆表的是 `agent_share`，因为它有反向多对多查询（"我能看到哪些 agent"）。

### 5.4 不持久化的数据（同原 builder）

| 数据 | 存放位置 |
|---|---|
| Session / Transcript | `<workspace>/agents/<id>/sessions/*.log.jsonl` |
| UsageStore | 进程内存，重启清空（同原） |
| AgentActivity | `<workspace>/activity/activity.jsonl` |
| Session 已读状态 | `~/.agentscope/session-read-state.json` |

---

## 6. 鉴权与数据隔离（sa-token）

### 6.1 全局过滤器

```java
@Configuration
public class SaTokenConfig {
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude("/api/auth/login",
                        "/actuator/health",
                        "/", "/assets/**", "/index.html")
            .setAuth(obj -> SaRouter.match("/**").check(r -> StpUtil.checkLogin()));
    }
}
```

### 6.2 数据隔离规则（应用层过滤）

| 表 | 规则 |
|---|---|
| `model_provider` / `mcp_server` / `skill_marketplace` / `skill_repository` | per owner：所有读写 `WHERE owner_id = StpUtil.getLoginIdAsString()` |
| `agent_definition` | per owner + 通过 `agent_share` 分享给他人；可见 = `owner_id = me OR EXISTS(share WHERE grantee_id = me)` |
| `agent_share` | 仅 agent owner 可写；被分享者只能读 |
| `sys_user` | 仅本人能改自己的密码 |

**Repository 标准模板**：

```java
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    List<ModelProvider> findByOwnerId(String ownerId);
    Optional<ModelProvider> findByIdAndOwnerId(Long id, String ownerId);
    long deleteByIdAndOwnerId(Long id, String ownerId);
}
```

**Agent 可见性查询**：

```sql
SELECT a.* FROM agent_definition a
WHERE a.owner_id = :me
   OR EXISTS (SELECT 1 FROM agent_share s
              WHERE s.agent_def_id = a.id AND s.grantee_id = :me)
```

### 6.3 Agent 分享 ACL（三级 tier）

沿用原 builder 的 `EDIT > RUN > CLONE`：

```java
public enum Tier { EDIT, RUN, CLONE }

@Service
public class AgentAccessGuard {
    public Mono<AgentDefinition> require(String agentId, Tier minTier) {
        String me = StpUtil.getLoginIdAsString();
        return aclService.tierFor(me, agentId)
            .flatMap(actual -> actual.compareTo(minTier) >= 0
                ? agentRepo.findByAgentId(agentId)
                : Mono.error(new NotPermittedException()))
            .switchIfEmpty(Mono.error(new AgentNotFoundException()));
    }
}
```

### 6.4 敏感字段加密

- 启动时从环境变量 `AGENTSCOPE_BUILDER_SECRET_KEY`（32 字节 base64）读出 AES-256 主密钥
- JPA `AttributeConverter<String, String>` 在 `props_json` 写入时**字段级**加密 `apiKey` / `token` / `password` 等 key，读取时解密
- 列表接口返回时再做 mask（`sk-xxxx****`），编辑时前端传特殊占位符表示"不修改"
- 思路同原 builder `ChannelDirectoryController` 的凭证 mask

### 6.5 子 Agent / 调用者 / 文件系统 owner 三件套

沿用原 builder 设计：SCOPE_USER（即 `run_as = OWNER`）的 agent 被其他用户调用时，调用者的**会话独立**，但**文件系统命名空间锁定到 owner**：

```java
String fsUserId = agentDef.runAs() == RunAs.OWNER
    ? agentDef.ownerId()
    : callerLoginId;
```

### 6.6 默认账号

启动时 `@PostConstruct` 若 `sys_user` 表空，自动种 `admin/admin`（控制台打印强提示首次登录改密）。同原 builder。

### 6.7 不做的事情

- **不做 admin 视角** —— 不存在 `/api/admin/**` 接口、不存在"看全部数据"的入口
- **不做用户注册接口** —— 启动种子账号自己用
- **不做角色字段** —— sys_user 不存 roles

---

## 7. REST 接口总览

```
─── 认证 ───
POST   /api/auth/login                         sa-token 登录
POST   /api/auth/logout
GET    /api/auth/me
POST   /api/user/change-password

─── 资源管理（per-owner CRUD）───
GET/POST/PUT/DELETE  /api/models                     ← model_provider
GET/POST/PUT/DELETE  /api/mcp-servers                ← mcp_server
GET/POST/PUT/DELETE  /api/skill-marketplaces         ← skill_marketplace
GET/POST/PUT/DELETE  /api/skill-repositories         ← skill_repository

GET    /api/skill-marketplaces/{id}/skills           列出市场里的 skill
GET    /api/skill-marketplaces/{id}/skills/{name}    skill 详情

─── 工厂目录（给前端动态填表单用）───
GET    /api/factories/model-types
GET    /api/factories/tool-types
GET    /api/factories/skill-repo-types
GET    /api/factories/hook-types
GET    /api/factories/agent-types

─── Agent ───
GET/POST/PUT/DELETE  /api/agents[/{id}]
POST   /api/agents/{id}/clone
POST   /api/agents/draft                              AI 起草

GET/POST/DELETE      /api/agents/{id}/shares[/...]
GET    /api/agents/{id}/activity

─── Workspace ───
GET    /api/agents/{id}/workspace                     摘要
POST   /api/agents/{id}/workspace/scaffold
GET/PUT/DELETE       /api/agents/{id}/workspace/file
POST   /api/agents/{id}/workspace/file/move
POST   /api/agents/{id}/workspace/upload              multipart
GET    /api/agents/{id}/workspace/memory
GET    /api/agents/{id}/workspace/files

GET/PUT              /api/agents/{id}/subagents[/{name}]
POST   /api/agents/{id}/subagents/from-agent
DELETE /api/agents/{id}/subagents/{name}

─── Skill 安装到 workspace ───
GET/PUT/DELETE       /api/agents/{id}/skills/workspace[/{name}]
GET    /api/agents/{id}/skills/repositories[/{index}/skills[/{name}]]
POST   /api/agents/{id}/skills/workspace/install
POST   /api/agents/{id}/skills/workspace/marketplace-install

─── Tool ───
GET    /api/agents/{id}/tools/active
GET/PUT              /api/agents/{id}/tools/config
GET    /api/agents/{id}/tools/catalog/builtins
GET    /api/agents/{id}/tools/catalog/mcp-servers

─── Session ───
GET    /api/agents/{id}/sessions/inbox
GET    /api/agents/{id}/sessions/{key}
POST   /api/agents/{id}/sessions/{key}/reset
PATCH  /api/agents/{id}/sessions/{key}/read
DELETE /api/agents/{id}/sessions/{key}

─── Chat（核心）───
GET    /api/agents/{id}/chat/session                  当前 sessionKey
POST   /api/agents/{id}/chat/send                     同步
POST   /api/agents/{id}/chat/stream                   SSE
       body: { message, overrideModelProviderId? }    ← 可临时切模型

─── 模板 ───
GET    /api/templates[/{id}]

─── SPA fallback ───
GET    /, /assets/**, 其余无扩展名                    → /static/index.html
```

**SSE 事件类型**（同原）：`token` / `tool_call` / `tool_result` / `done` / `error`

---

## 8. 项目目录结构

单 Maven module，目录：

```
agentscope-builder-saton/
├── pom.xml
└── src/main/java/io/agentscope/builder/saton/
    ├── BuilderApp.java                      # @SpringBootApplication
    │
    ├── auth/                                # sa-token 集成
    │   ├── SaTokenConfig.java               # SaReactorFilter
    │   ├── AuthController.java              # /api/auth/**
    │   └── UserService.java
    │
    ├── factory/                             # ★ 五大工厂层（架构核心）
    │   ├── core/
    │   │   ├── ProviderRegistry.java
    │   │   ├── FactoryBase.java
    │   │   └── TypeMeta.java                # 给前端的 type+schema 元信息
    │   ├── model/
    │   │   ├── ModelProviderType.java       # SPI 接口
    │   │   ├── ModelFactory.java
    │   │   └── impl/
    │   │       ├── DashScopeModelProviderType.java
    │   │       ├── OpenAIModelProviderType.java
    │   │       ├── AnthropicModelProviderType.java
    │   │       ├── GeminiModelProviderType.java
    │   │       └── OllamaModelProviderType.java
    │   ├── tool/
    │   │   ├── ToolType.java
    │   │   ├── ToolFactory.java
    │   │   └── impl/{ShellCmd,ReadFile,WriteFile,PlanNotebook,SubAgent,McpBridge}ToolType.java
    │   ├── skill/
    │   │   ├── SkillRepoType.java
    │   │   ├── SkillFactory.java
    │   │   └── impl/{Local,Git,Nacos}SkillRepoType.java
    │   ├── hook/
    │   │   ├── HookType.java
    │   │   ├── HookFactory.java
    │   │   └── impl/{Logging,ToolNotification,AuditJsonl,TracingOtel}HookType.java
    │   ├── agent/
    │   │   ├── AgentType.java
    │   │   ├── AgentFactory.java            # ★ 编排器
    │   │   └── impl/{Harness,ReAct}AgentType.java
    │   └── api/
    │       └── FactoriesController.java     # /api/factories/*-types
    │
    ├── resource/                            # 4 类独立资源 CRUD
    │   ├── model/        (Controller + Service + Entity + Repo)
    │   ├── mcp/
    │   ├── marketplace/
    │   └── repository/
    │
    ├── agent/                               # Agent CRUD + 装配
    │   ├── AgentController.java             # /api/agents/**
    │   ├── AgentService.java
    │   ├── AgentDefinitionEntity.java + Repo
    │   ├── AgentShareEntity.java + Repo
    │   ├── AgentAclService.java
    │   ├── AgentAccessGuard.java
    │   └── AgentBuildOrchestrator.java      # ★ 调五大工厂装配 HarnessAgent
    │
    ├── runtime/                             # HarnessAgent 运行时
    │   ├── AgentRuntimeResolver.java        # (agentId, modelId) → HarnessAgent 缓存
    │   ├── AgentInstanceCache.java
    │   ├── HarnessGatewayConfig.java
    │   └── session/
    │       ├── SessionService.java
    │       ├── SessionController.java       # /api/agents/{id}/sessions/**
    │       └── SessionReadStateStore.java
    │
    ├── chat/                                # SSE 聊天
    │   ├── ChatController.java              # /api/agents/{id}/chat/**
    │   ├── ChatService.java
    │   ├── ToolEventBus.java
    │   └── ToolNotificationMiddleware.java
    │
    ├── workspace/
    │   ├── WorkspaceController.java         # /api/agents/{id}/workspace/**
    │   ├── WorkspaceService.java
    │   └── WorkspaceScaffolder.java
    │
    ├── skill/
    │   └── AgentSkillsController.java       # /api/agents/{id}/skills/**
    │
    ├── tool/
    │   └── AgentToolsController.java        # /api/agents/{id}/tools/**
    │
    ├── template/
    │   ├── TemplateRegistry.java
    │   └── TemplateController.java          # /api/templates
    │
    ├── ai/                                  # AI 起草
    │   ├── AgentDraftController.java
    │   └── AgentDraftService.java
    │
    ├── audit/
    │   └── AgentActivityStore.java          # JSONL
    │
    ├── common/
    │   ├── EncryptedJsonConverter.java      # 字段级 AES-256
    │   ├── SecretFields.java
    │   ├── ApiException.java + GlobalErrorHandler.java
    │   └── JsonUtil.java
    │
    └── persistence/
        └── JpaConfig.java                   # @EnableJpaRepositories
```

```
src/main/resources/
├── application.yml                         # 默认 H2 (file)
├── application-jdbc.yml                    # MySQL/PG 切换 profile
├── scaffold/default/                       # workspace 脚手架（同原）
├── templates/                              # 起步模板（同原）
├── prompts/agent-draft.md                  # AI 起草 system prompt
└── catalog/mcp-servers.json                # MCP 静态目录（给前端的"建议列表"）
```

**为何不拆子模块**：原 builder 单 module；类规模 80-120 个可控；过早拆分增加依赖管理负担。若后期某层稳定，按 `factory / runtime / web` 三段拆分。

---

## 9. 落地里程碑

每个里程碑结束都能跑通一个 demo。

| 里程碑 | 内容 | 完成标志 |
|---|---|---|
| **M1 基础设施** | pom、SB4 启动、sa-token 拦截器、JPA + H2、sys_user 表 + 登录 | `admin/admin` 登录拿到 token，`/api/auth/me` 返回用户 |
| **M2 资源管理** | model_provider / mcp_server / skill_marketplace / skill_repository 4 表 + 4 套 CRUD + 加密 Converter | 前端能加一个 DashScope，DB 中 api_key 已加密 |
| **M3 工厂骨架** | factory/core + ModelFactory 完整 + FactoriesController | `GET /api/factories/model-types` 返回 dashscope 等 + schema |
| **M4 第一个能聊的 Agent** | AgentDefinition CRUD + AgentBuildOrchestrator（仅接 ModelFactory）+ ChatController 非流式 + HarnessAgent 集成 | 创建 agent → POST 消息 → 拿到回复 |
| **M5 流式 + 工具** | SSE 推 token + ToolFactory + 6 个内置 ToolType + ToolEventBus + ToolNotificationMiddleware | 前端流式 token + 实时 tool_call 事件 |
| **M6 Workspace + Session** | workspace CRUD + SessionService + 多轮对话 + transcript 回读 | 刷新页面继续上次对话 |
| **M7 Skill + Hook + Subagent** | SkillFactory + HookFactory + 3 类 hook + SubAgentTool + sessions_spawn | agent 用 skill 文件、调子 agent |
| **M8 Marketplace + Repository** | git/nacos marketplace + skill 安装到 workspace | UI 上从市场装 skill |
| **M9 分享 + Clone + 模板** | agent_share + AccessGuard + AgentClone + 模板初始化 | A 创建 agent 分享给 B；B 能用、能 Clone |
| **M10 AI 起草 + 审计 + 收尾** | AgentDraftController + AgentActivityStore + polish | 功能对齐原 builder（除 IM） |

---

## 10. 重要约定与坑（沿用原 builder 经验）

1. **`gateKey ↔ sessionKey` 简化为单 sessionKey**：因为去掉了多渠道路由，前端可调，无需保留两层映射。
2. **每次 EDIT agent 必须 `runtimeResolver.invalidate(agentId)`** —— 丢弃缓存的 HarnessAgent 实例，下次 chat 按新配置重建。
3. **删除/改密 model_provider 必须 `runtimeResolver.invalidateByModelId(modelId)`** —— 所有引用该模型的 HarnessAgent 全部 invalidate。
4. **共享 agent 的 `run_as = OWNER` 时**，文件系统命名空间锁定 owner，会话仍按 caller 隔离。
5. **`activity/` 目录走共享存储** —— 多副本部署时审计日志统一。
6. **chat 接口 `overrideModelProviderId` 必须校验权限** —— 调用者必须 own 这个 model_provider（不能借别人的 API Key 跑）。
7. **agent_share 删除/改 tier 触发 `runtimeResolver.invalidate(agentId)`** —— 防止已被踢权限的 caller 仍在用缓存实例。

---

## 11. Out of Scope（明确不做）

- 钉钉/企微/飞书/GitHub/GitLab 等所有 IM 渠道
- A2A / AGUI / Agent Protocol 等通信协议扩展
- Outbound 主动外发消息（OutboundController / OutboundTool）
- IdentityLinkStore 与 `/dock_*` slash 命令
- AdminUserController 与"看全部数据"的视角
- 用户注册接口
- 用户自定义工具（前端写 Java/JavaScript 代码作工具）
- 运行时 jar 热加载 / 动态 ClassLoader 隔离
- Hibernate `@TenantId` 多租户（仅做应用层 owner 过滤）
- 模型/MCP/Skill 资源的跨用户分享（仅 agent 支持分享）

---

## 12. 实施踩坑笔记（M1 实测沉淀，后续 milestone 套用）

这一节记录在 M1（基础设施）实施过程中真实踩到的 Spring Boot 4 + sa-token 1.45 + Jackson 3 集成事实，后续 milestone 直接照搬，不要再花时间踩。

### 12.1 Maven 默认 surefire 不识别 JUnit 5

**事实**：Maven 3.8.4 的 super-pom 默认 `maven-surefire-plugin:2.12.4`，**静默跳过**所有 JUnit 5 测试 —— 报 BUILD SUCCESS 但 Tests run: 0。

**对策**：所有 milestone 的 `pom.xml` 必须 pin surefire 3.2.5+：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

### 12.2 Spring Boot 4 拆出多个 test slice 依赖

Spring Boot 4 把若干 test slice 从 `spring-boot-starter-test` 默认传递依赖里拆走。Starter 不再传递这些，必须在 pom.xml 显式声明 test-scope。

**已知拆走的**：

| Slice 注解 | SB4 独立 artifact |
|---|---|
| `@DataJpaTest` | `spring-boot-data-jpa-test` |
| `@AutoConfigureWebTestClient` | `spring-boot-webtestclient`（或用 `@LocalServerPort` 手动 `WebTestClient.bindToServer()`，M1 走的就是手动路线，无需加 dep） |

**对策**：用到 `@DataJpaTest` 必须加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-data-jpa-test</artifactId>
    <version>${spring.boot.version}</version>
    <scope>test</scope>
</dependency>
```

**集成测试不依赖 `@AutoConfigureWebTestClient`**，用：

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class XxxTest {
    @LocalServerPort int port;
    WebTestClient client;

    @BeforeEach void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }
}
```

### 12.3 Spring Boot 4 默认 Jackson 3

**事实**：SB4 把默认 ObjectMapper 升到 Jackson **3.x**，包名从 `com.fasterxml.jackson.databind` 改为 `tools.jackson.databind`。`com.fasterxml.jackson.core:jackson-annotations:2.20` 还在 classpath，但 `ObjectMapper` 本体在新包。

**对策**：自己写的代码引入 ObjectMapper 时：

```java
import tools.jackson.databind.ObjectMapper;   // NOT com.fasterxml.jackson.databind.ObjectMapper
```

### 12.4 sa-token reactor 在 WebFlux 里调 `StpUtil.login()` 的正确姿势

**事实**：sa-token-reactor 不写 ThreadLocal 也不写 Reactor Context，它把 `ServerWebExchange` 塞进 `SaReactorSyncHolder`（一个 ThreadLocal binding），但仅在 filter 链上有效。`Mono.fromCallable` 的 lambda 跑在的线程上**没有这个 binding**，直接调 `StpUtil.login()` 抛 `SaTokenContextException: SaTokenContext 上下文尚未初始化`。

**对策**：controller 方法签名加上 `ServerWebExchange exchange`，在调用 `StpUtil` 的同步代码块外手动 set/clear：

```java
@PostMapping("/login")
public Mono<LoginResponse> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
    return Mono.fromCallable(() -> {
        SaReactorSyncHolder.setContext(exchange);
        try {
            return userService.login(req);   // 这里面调 StpUtil.login()
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    });
}
```

**也不要再 `.subscribeOn(Schedulers.boundedElastic())`**：boundedElastic 线程上同样没有 binding，再加 subscribeOn 会让事情更糟。M1 的 in-memory JPA 在 Netty 线程跑足够快；真要切池子，M-？ 再讨论统一方案（含 binding 跨线程传递）。

**纯单元测试（无 HTTP 请求）调用 service 时同理失败** —— 这就是为什么 M1 的 `UserServiceTest` 只测 `BadCredentialsException`，把"login 拿 token"的 happy path 放到 `AuthFlowTest`（有真实 HTTP 上下文）。

### 12.5 WebFlux filter 抛的异常不走 `@RestControllerAdvice`

**事实**：`@RestControllerAdvice` 的 `@ExceptionHandler` 只接 controller 方法抛出的异常。`SaReactorFilter` 是 WebFilter，跑在 controller 之前，它抛的 `NotLoginException` 不会被 advice 接到，最终走 Spring 的默认 500 处理器。

**对策**：实现 `WebExceptionHandler` bean，`@Order(-2)` 让它在默认处理器之前：

```java
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        NotLoginException notLogin = findNotLogin(ex);
        if (notLogin != null) {
            return writeJson(exchange, HttpStatus.UNAUTHORIZED,
                ApiError.of(401, "not logged in: " + notLogin.getType()));
        }
        return Mono.error(ex);   // 让 @RestControllerAdvice 接 controller 异常
    }
    // ...
}
```

### 12.6 sa-token 把 NotLoginException wrap 在 SaTokenException 里

**事实**：`SaReactorFilter` 在 line 98 左右把 `NotLoginException` 包进一个外层 `SaTokenException` 再抛。直接 `if (ex instanceof NotLoginException)` 永远进不去。

**对策**：递归走 cause chain（深度限制防自循环）：

```java
private static NotLoginException findNotLogin(Throwable ex) {
    Throwable cur = ex;
    for (int i = 0; cur != null && i < 8; i++) {
        if (cur instanceof NotLoginException nle) return nle;
        cur = cur.getCause();
    }
    return null;
}
```

### 12.7 集成测试用 in-memory H2，与 dev 文件 db 隔离

**事实**：`spring.datasource.url=jdbc:h2:file:./data/builderdb` 在 `@SpringBootTest` 里也会被加载，跟一个本地正在运行的 dev 实例抢同一文件，H2 抛 `Database may be already in use`。

**对策**：`src/test/resources/application.yml` 显式覆盖为 in-memory，create-drop：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
sa-token:
  is-log: false
```

### 12.8 Logback 在 Windows 默认非 UTF-8

**事实**：Windows 控制台默认 GBK，logback 不显式声明 charset 会乱码（特别是 sa-token 自带中文异常文案 + 我们项目本身中文日志）。

**对策**：encoder 块加 `<charset>UTF-8</charset>`。

```xml
<encoder>
    <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
    <charset>UTF-8</charset>
</encoder>
```

### 12.9 spring-security-crypto 不要显式 pin 版本

**事实**：M1 早期手贱 pin 了 `spring-security-crypto:6.4.1`，比 SB4 BOM 管理的版本（7.0.2）旧两个大版本，硬降级。

**对策**：所有 Spring 生态的 artifact **不写 version**，让 `spring-boot-dependencies` BOM 管理。仅 `spring.boot.version` 和 `sa-token.version` 在 properties 里。

### 12.10 JPA AttributeConverter 要被 Spring 注入必须加 @Component

**事实**（M2 发现）：一个实现 `AttributeConverter<X,Y>` 的类如果有 Spring bean 依赖（比如本项目的 `EncryptedJsonConverter` 依赖 `AesGcmCipher`），仅靠 `@Converter` 注解 + 构造器注入是不够的。JPA 通过 SPI 用 no-arg 构造器实例化，但 Spring 这边因为没有 stereotype 注解（`@Component` / `@Service`）也不会注册成 bean，autowire 测试就会拿不到。

**对策**：同时加 `@Converter` 和 `@Component`，构造器参数加 `@Autowired @Lazy`：

```java
@Converter
@Component
public class EncryptedJsonConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    @Autowired
    public EncryptedJsonConverter(@Lazy AesGcmCipher cipher) {
        this.cipher = cipher;
    }
    // ...
}
```

### 12.11 pom 没开 -parameters → @PathVariable / @RequestParam 必须写显式名字

**事实**（M2 发现）：默认 javac 不保留参数名到 .class 文件，Spring 拿不到 `@PathVariable Long id` 中的 `id` 名字，请求 `/api/models/7` 会因为 path variable 缺失返回 500。

**对策**（两选一，本项目走 A）：

**A. controller 里始终显式写名字**（最小变更，零 pom 改动）：
```java
@GetMapping("/{id}")
public Mono<Foo> get(@PathVariable("id") Long id, ServerWebExchange exchange) { ... }
```

**B. pom 开 `-parameters` 编译选项**（一次配，永久受益；后续可考虑）：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

### 12.12 @PathVariable 风格小结（参考）

对 `@PathVariable` / `@RequestParam` / `@MatrixVariable`，全项目沿用方案 A 显式名字写法，直到任何一处需要更复杂参数处理时再考虑切换 B。

### 12.13 ResourceCommon.normalizePropsJson 解 NPE

**事实**：JPA 通过 `setPropsJson(null)` 写入是合法的，但 `JsonUtil.mapper().readTree(null)` 会抛 NPE。VO mask 路径必须 null-guard。

**对策**：`ResourceCommon.normalizePropsJson(s)` 把 `null/blank` 都规范化成 `"{}"` 再传给 Jackson。

### 12.14 SaReactorSyncHolder 在 Flux.defer 嵌套链中会丢

**事实**（M5 发现）：在 controller 层 `Flux.defer(() -> { setContext(exchange); try { return service.stream(...); } finally { clearContext(); } })`，service.stream 内部又是 `Flux.defer(() -> StpUtil.getLoginIdAsString())`。两个 defer 看起来同步链上，但 reactor 实际执行顺序是：

1. 外层 defer lambda 执行 → `setContext` → 调 service.stream → 拿到内部 Flux → `clearContext`（finally 立即执行） → 外层返回内部 Flux
2. **reactor 再去 subscribe** 内部 Flux → 内部 defer lambda 执行 → 此时 ThreadLocal **已经清空** → `StpUtil` 抛 `SaTokenContextException`

**对策**：service 层在 `Flux.defer` **之前**捕获 loginId 等需要 sa-token 的数据：

```java
// ChatService.stream 错误写法（context lost）
return Flux.defer(() -> {
    String me = StpUtil.getLoginIdAsString();   // ← context 已被清空，抛异常
    ...
});

// 正确写法：在 defer 外捕获
public Flux<AgentEvent> stream(Long agentDefId, ChatSendReq req) {
    String me = StpUtil.getLoginIdAsString();   // ← 此时 controller 的 setContext 还有效
    return Flux.defer(() -> {
        // 只用捕获到的 me，不再调 sa-token
        var def = agentRepo.findByIdAndOwnerId(agentDefId, me).orElseThrow(...);
        ...
    });
}
```

### 12.15 AgentEvent 是 Jackson 2 注解、项目用 Jackson 3 mapper —— SSE data JSON 没 type 字段

**事实**（M5 发现）：`io.agentscope.core.event.AgentEvent` 的 `@JsonTypeInfo + @JsonSubTypes` 来自 `com.fasterxml.jackson.annotation`（Jackson 2），但项目的 `JsonUtil.mapper()` 是 Jackson 3（`tools.jackson.databind`）。Jackson 3 **不识别** Jackson 2 注解，所以 `mapper.writeValueAsString(event)` 输出的 JSON **没有** `"type": "TEXT_BLOCK_DELTA"` 这种鉴别字段。

**当前影响**：
- SSE 的 `event:` name 字段独立来自 `event.getType().name().toLowerCase()`，前端按 event name 路由是 OK 的
- 但前端如果想从 `data:` 字符串解析回 Java/JS 对象，没有 type 字段就不知道用哪个子类

**对策**（M5 暂未处理，留给 M6+ 或前端集成时再决定）：
- 选项 A：toSse 方法手动注入 type 字段（如把 `data` 用 ObjectNode 包一层 + put("type", ...)）—— 简单但耦合
- 选项 B：项目内提供 Jackson 2 兼容 mapper（仅给 SSE 序列化用）—— pom 加 `com.fasterxml.jackson.core:jackson-databind` runtime dep
- 选项 C：等 agentscope-core 升级到 Jackson 3 注解（理想，等不到）

短期对策记录在 SSE 客户端文档里："靠 event: name 路由，不要从 data: 字段读 type"。

### 12.16 SB4 不会在多个 public 构造器之间自动挑选 → 必须显式 @Autowired

**事实**（M6-3 发现）：一个 `@Service` / `@Component` 有两个 public 构造器（典型场景：一个走 `@Value` 注入用于 Spring，一个走原始 `Path` 用于单元测试 `@TempDir`），Spring Boot 4 **不会**自动挑选其中一个，会在另一个 `@SpringBootTest` 加载时报：

```
No default constructor found; nested exception is java.lang.NoSuchMethodException
```

而且这个错只在**其它**测试类加载 ApplicationContext 时炸（带 `@SpringBootTest` 的 flow test），单跑 `SessionServiceTest` 自己（不需要 Spring）反而绿。诊断有迷惑性。

**对策**（M6-3 已用）：
- Spring 用的 ctor 显式标 `@Autowired`，让 SB 明确选用
- 测试 ctor 改 `package-private`（同包测试可见，但 Spring 看不到）

```java
@Service
public class SessionService {
    private final Path root;
    private final AgentStateStore stateStore;

    @Autowired
    public SessionService(@Value("${app.session.root:./data/sessions}") String rootConfig,
                          AgentStateStore stateStore) {
        this(Paths.get(rootConfig).toAbsolutePath().normalize(), stateStore);
    }

    /** package-private — 仅给同包测试用 */
    SessionService(Path root, AgentStateStore stateStore) {
        this.root = root;
        this.stateStore = stateStore;
    }
}
```

也可以走单 ctor + Spring 自动注入 + 测试用 `@SpringBean` / `@MockBean` 替换 —— 但这两个 ctor 的写法更轻。

### 12.17 WebFlux Controller 兜底异常处理：IllegalArgumentException → 400 要主动加

**事实**（M6-7 发现）：M1 写的 `AuthExceptionHandler` 通过 `@RestControllerAdvice + @ExceptionHandler` 处理 `NotFoundException → 404` / `ConflictException → 409` / `BadCredentialsException → 401`，但**没有** `IllegalArgumentException → 400`。Workspace 模块的 `WorkspacePathResolver` 抛 `IllegalArgumentException` 表达"路径越界"，前端用户 POST `path=../etc/passwd` 时本应返回 400，实际拿到 **500 internal server error**。

**对策**：在 `AuthExceptionHandler` 直接加一个 handler（沿用同步 `ResponseEntity<ApiError>` 风格）：

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(400, ex.getMessage()));
}
```

`GlobalErrorWebExceptionHandler` 是 `WebExceptionHandler`（接 WebFilter 抛的 sa-token NotLoginException），跟 controller 抛的异常各管一摊（spec §12.5 已说明）；新增 controller 异常类型一律加到 `@RestControllerAdvice` 文件里，别动 `WebExceptionHandler`。

### 12.18 HarnessAgent 默认 middleware 链在 WebFlux + sa-token-reactor 上 `Mono.block()` 死锁

**事实**（M7-1 发现）：`agentscope-core` 2.0.0-RC2 的 `ReActAgent.applySystemPromptMiddlewares` 在任何 middleware override `onSystemPrompt` 时执行 `Mono.block()`（ReActAgent.java:569）。`HarnessAgent` 的默认 builder 链会自动安装：

- `HarnessSkillMiddleware` —— `.skillRepositories(...)` 非空或默认 Layer-4 workspace skills 启用时
- `WorkspaceContextMiddleware` —— `.workspace(Path)` 调用即启用
- `PlanModeMiddleware` —— planMode 启用时（本项目不开）

WebFlux Netty 事件循环禁止 `Mono.block()`，SSE / 任何 streaming chat 都会抛 `IllegalStateException: block()/blockFirst()/blockLast() are blocking`。原 `agentscope-builder` 用 Spring MVC (servlet)，没踩到；本项目用 WebFlux + sa-token-reactor 必然踩。

**对策**（M7-1 已用 —— 留到 M7-4 引入 skill 时再处理）：
- 在 `AgentBuildOrchestrator` 的 builder 链上加 `.disableDynamicSkills().disableWorkspaceContext()`
- 仍调 `.workspace(path)`（路径已 mkdir，给 future skill_manage tool 用），但不开 context middleware
- HarnessAgent 行为退化为 ReActAgent 等同 —— 现有 SSE 测试全绿

**M7-4 注意事项**：当 skill_repositories_json 真正传入并要启用 dynamic skill 时，需要二选一：
- 选项 A: 在 `ChatService.stream` 的 streamEvents 调用上加 `.subscribeOn(Schedulers.boundedElastic())`，把 sa-token loginId 提前 capture（已有），让整个 reactive 链跑在 boundedElastic 上（一次性 thread switch，但不影响响应式语义）。需新 spec §12.19 跟踪
- 选项 B: 在 agentscope-core 上游修复（让 applySystemPromptMiddlewares 全程返回 `Mono<String>`，不 block）—— 跨 repo，目前不可行

短期保留 `.disableDynamicSkills().disableWorkspaceContext()` —— M7-2/M7-3/M7-4 仍按 plan 把工厂建好，但 M7-4 启用时把 disable 去掉的同时必须配合 subscribeOn 改造。

---

## 13. 开放问题（实施期再决定）

- `application.yml` 端口默认 8080 还是另选
- JSON Schema 校验库选 [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator) 还是 [erosb/json-sKema](https://github.com/erosb/json-sKema)
- sa-token session 存储默认进程内还是 Redis（多副本部署时）
- 模型 invalidate 触发后，正在进行的会话是否优雅过渡（让当前 turn 跑完再切换）

---

**End of design** —— 待 review 后进入 writing-plans 阶段。
