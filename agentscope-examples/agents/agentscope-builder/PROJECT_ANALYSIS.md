# AgentScope Builder 项目源码分析

> 本文档基于源码逐文件分析。  
> 适合：第一次接手项目的开发、做二次开发或定位 Bug 的工程师。

---

## 1. 项目定位

`agentscope-builder` 是 **AgentScope Java** 框架下的一个示例/参考实现，本质上是把
[`agentscope-claw`](../agentscope-claw/)（单租户自进化 agent）"装进了多租户 SaaS 平台"，
即：

- 后端：Spring Boot 3 (**WebFlux 响应式**) + Spring Security + Spring Data JPA
- 前端：React SPA（产物打包进 `src/main/resources/static/`）
- 运行时核心：**HarnessAgent**（来自 `agentscope-harness` 模块），通过自定义
  `BuilderBootstrap` + `HarnessGateway` 包装成多租户/多渠道的网关
- 持久化：H2（默认）/ MySQL / PostgreSQL（同一份 Schema）
- 多渠道：钉钉 / 企微 / 飞书 / GitHub / GitLab / Web ChatUI

应用入口：`io.agentscope.builder.BuilderApp#main`，服务端口 `8080`。

---

## 2. 顶层目录与包结构

```
agentscope-builder/
├── pom.xml                                    # Maven，依赖 harness + IM 渠道扩展 + JPA
├── frontend/                                  # React 源码（npm run build → static/）
├── src/main/java/io/agentscope/builder/
│   ├── BuilderApp.java                        # Spring Boot 启动入口
│   ├── runtime/                               # HarnessAgent + Gateway + Session 运行时
│   │   ├── BuilderBootstrap.java              # ★ agent/通道装配核心，对应 claw 的 Bootstrap
│   │   ├── config/                            # agentscope.json 数据模型 (DTO)
│   │   ├── gateway/HarnessGateway.java        # ★ 多 agent 路由 + 会话生命周期 + 子 agent 调度
│   │   ├── marketplace/                       # 技能市场（Git / Nacos）
│   │   ├── outbound/                          # Agent 主动外发消息（HTTP & Tool）
│   │   └── session/                           # 会话状态管理（线程/锁/调度通道）
│   └── web/                                   # Web API 层（控制器 + 持久化）
│       ├── ai/                                # AI 辅助起草 agent 配置
│       ├── api/                               # ★ REST/SSE 控制器集合
│       ├── audit/                             # 每个 agent 的活动日志
│       ├── auth/                              # 登录/JWT/用户管理
│       ├── catalog/                           # Agent 目录服务（全局 + 用户自定义）
│       ├── config/                            # Spring 配置（Security / Bootstrap / SPA）
│       ├── identity/                          # 用户在外部渠道的身份映射
│       ├── persistence/jpa/                   # JPA 实体 + Repository
│       ├── scaffold/                          # 工作区脚手架（默认模板）
│       ├── session/                           # Session 视图/解析/已读状态
│       ├── share/                             # 分享/ACL/权限分级
│       ├── template/                          # 起步模板（research-assistant 等）
│       ├── toolbus/                           # Tool 事件总线（用于 SSE 推送 tool_call）
│       ├── usage/                             # 使用统计
│       ├── util/WorkspaceCopier.java          # 跨 agent 工作区复制（用于 Clone）
│       └── workspace/                         # 共享工作区路径 / 命名空间视图
└── src/main/resources/
    ├── application.yml / application-jdbc.yml # Spring 配置（默认 H2，jdbc profile=MySQL/PG）
    ├── agentscope.json.example                # 配置样例
    ├── catalog/mcp-servers.json               # MCP 服务器模板
    ├── prompts/agent-draft.md                 # AI 起草用 system prompt
    ├── scaffold/default/                      # 默认 workspace 脚手架文件
    ├── templates/                             # 三个内置起步模板
    └── static/                                # 前端构建产物
```

---

## 3. 整体请求流程总览

### 3.1 入口路径分类

Builder 对外暴露的"入口"分四类：

| 入口类型 | 路径前缀 | 处理者 | 后端调用链 |
|---------|---------|--------|----------|
| **HTTP REST** | `/api/auth`, `/api/agents`, `/api/channels`, `/api/marketplaces`, `/api/templates`, `/api/outbound`, `/api/admin`, `/api/user` | 各 `@RestController` | `Controller → Service → BuilderBootstrap/Gateway → HarnessAgent` |
| **SSE 实时流** | `POST /api/agents/{id}/chat/stream` | `ChatController` | `Flux<ServerSentEvent>` → SSE 推送 `tool_call`/`token`/`done` |
| **静态资源 / SPA** | `/`, `/assets/**`, 其余无扩展名 | `WebConfig.spaFallback` | 落 `classpath:/static/index.html` |
| **IM 渠道回调** | 由各 `Channel` 自行注册（如钉钉 webhook） | `ChannelManager` 启动的内嵌服务 | `Channel → Gateway.run() → HarnessAgent.call()` |

### 3.2 出口路径分类

| 出口类型 | 触发者 | 实现 |
|---------|--------|------|
| **HTTP 同步回包** | 大部分 REST 控制器 | `Mono<XxxResponse>` |
| **SSE 推 token + tool 事件** | `ChatController.stream` | `Flux<ServerSentEvent>`，由 `ToolEventBus` + 模型回包合流 |
| **主动外发到 IM 渠道** | Agent 工具 `outbound_send` / `POST /api/outbound/send` | `OutboundService → ChannelManager.deliver()` |
| **子 agent 完成回灌** | `SessionAgentManager` 触发 `AnnounceDispatcher` | `HarnessGateway.tryDispatchAnnounce` → 启动新 `HarnessAgent.call` → 通过原渠道下发 |

### 3.3 一次"用户发消息→Agent 回答"完整时序

```
浏览器
  │ POST /api/agents/{agentId}/chat/stream   (Bearer JWT)
  ▼
SecurityConfig.JwtAuthFilter           ← 解析 JWT，把 userId 塞进 Reactive Security Context
  │
  ▼
ChatController.stream(agentId, req, auth)
  │
  ├─ AgentAccessGuard.require(userId, agentId, RUN)     ← 鉴权：必须 ≥ RUN 级
  ├─ handleSlashCommand(...)                            ← 拦截 /new /reset /identity /dock_*
  ├─ ToolEventBus.subscribe(sessionKey) ──► toolEvents  ← 订阅 tool_call/result（用于 SSE 旁路）
  └─ executeChat(userId, agentId, message)              ← 真正调度
        │
        ▼
ChatUiChannel.dispatch(InboundMessage)
        │  （把 (userId, agentId) 包成 InboundMessage，channelId=chatui）
        ▼
HarnessGateway.run(MsgContext, msgs, outboundAddress)
        │
        ├─ resolveOrCreateMainSession(gateKey, ha, userId)
        │     │
        │     └─► SessionAgentManager.registerMainSession(agentId, label, gateKey, userId)
        │           （写 SessionStore → sessions.json，分配 sessionKey/sessionId）
        │
        ├─ resolveAgent(agentId) → HarnessAgent
        │     （对用户自定义 agent 是 `uca-<ownerId>-<agentId>` 的网关 ID）
        │
        ├─ fsUserIdResolver(callerUserId, agentId) → fsUserId
        │     （SCOPE_USER agent 用 ownerId；GLOBAL/未注册 用 callerUserId）
        │
        ├─ SessionTurnGate.acquire(gateKey)             ← 同一 (user,agent) 串行化
        │
        └─ HarnessAgent.call(messages, RuntimeContext)  ← 进入 harness 真正的 ReAct 循环
              │
              │ ReAct: Reasoning → Acting (tool call) → ... 循环
              │   - ToolNotificationMiddleware 在每次 Acting 前 publish 到 ToolEventBus
              │   - 工具中可能包含 outbound_send / sessions_spawn 等
              │
              ▼ 返回 Mono<Msg>
        │
        ▼ Mono<Msg>（最终 reply）
ChatController.stream() 把它转成 SSE：
   event=token data={type:token, data:reply.textContent}
   event=done  data={type:done,  sessionKey:<resolved>}
        │
        ▼
浏览器 EventSource 收到流，渲染对话气泡
```

> 关键点：**`sessionKey`** 是 SessionStore 内的真实主键；
> **`gateKey`** 是 `MsgContext.canonicalKey()` 的派生路由键，由 `(channelId,userId,extra.agentId)` 决定。
> Chat 路径上前端拿到的就是真实的 `sessionKey`，但内部路由始终用 `gateKey`。

---

## 4. 逐类详细说明

### 4.1 包 `io.agentscope.builder`（入口）

#### `BuilderApp.java`
- **职责**：Spring Boot 启动类，`@SpringBootApplication`。
- **入口方法**：`main(String[] args)`。
- 启动后注册的端点详见类 Javadoc：`/api/auth/**`, `/api/user/**`, `/api/agents/**`,
  `/api/channels`, `/api/templates/**`, 其余路径走 SPA fallback。

---

### 4.2 包 `io.agentscope.builder.runtime`（运行时核心）

#### `BuilderBootstrap.java` ★
- **职责**：构造、装配并暴露 HarnessAgent 集群 + Gateway + ChannelManager 的"主厂"。
- **核心常量**：
  - `DEFAULT_WORKSPACE_ROOT` = `~/.agentscope/builder/workspace`
  - `DEFAULT_CONFIG_PATH`     = `~/.agentscope/builder/agentscope.json`
- **构造流程**（`Builder.build()`）：
  1. 加载 `agentscope.json`，并入 programmatic 注册的 agent 列表 → 得到 `ids` 集合 + `mainId`。
  2. **Phase 1** —— 用 main agent 的 builder 提取 `SubagentEntry` 列表，建立共享 `WorkspaceManager`、
     `DefaultAgentManager`、`SessionStore`、`SessionAgentManager`、`ChannelManager`、
     `HarnessGateway`、`SessionsTool`、`OutboundTool`。
  3. **Phase 2** —— 对每个 agent 走 `applyFileEntry` + 外部 `Toolkit`（含 outbound_send）+
     externalSubagentTool 注入 → `b.build()`。
  4. **Phase 3** —— 全部 `built` agent 注册进 gateway，并绑定 main。
- **关键方法**：
  - `chatUiChannel()` —— 程序化获取 ChatUiChannel（嵌入/CLI/测试使用）。
  - `start(channels...)` —— 用 `ChannelManager` 初始化并启动通道。
  - `resolveWorkspace(agentId)` —— 计算某 agent 的 workspace 物理路径。

#### 子包 `runtime.config` —— `agentscope.json` 数据模型
| 类 | 作用 |
|---|---|
| `AgentscopeConfig` | 根：`main` + `agents` + `channels` + `session` |
| `AgentConfigEntry` | 单个 agent：name/sysPrompt/maxIters/model/tools.allow/identity/groupChat/skills/skillRepositories |
| `ChannelConfigEntry` | 单个通道：type/properties/defaultAgentId/dmScope/disabled/bindings |
| `BindingConfigEntry` | 一条路由绑定，对应 `ChannelBinding`：peer / parentPeer / guild+roles / team / account / channel 七档优先级 |
| `MarketplaceConfigEntry` | type + 自由属性（git/nacos） |
| `SessionLifecycleConfig` | `reset.dailyAt`/`idleMinutes` + `maintenance.mode/pruneAfter/maxEntries` |
| `SkillRepositoryConfigEntry` | 单个技能仓库（filesystem / git） |
| `ChannelTypeRegistry` | `ChannelFactory` 注册表，内置 chatui/dingtalk/wecom/feishu/github/gitlab |
| `SkillRepositorySupport` | 把 `SkillRepositoryConfigEntry` 物化成 `AgentSkillRepository`（含 GitRepo 反射加载） |

#### `runtime.gateway.HarnessGateway` ★
- **职责**：实现 `Gateway` 接口，负责把渠道入站消息路由到正确的 HarnessAgent，并：
  - 维护 `gateKey ↔ sessionKey ↔ agentId ↔ outboundAddress` 双向映射；
  - 通过 `SessionTurnGate` 保证同一 sessionKey 同时只跑一次；
  - 通过 `subscribeOn(Schedulers.boundedElastic())` 把阻塞操作放到弹性线程；
  - 安装 `fsUserIdResolver`（由 `AgentCatalogService` 注入），把"调用者用户"映射成
    "拥有者用户"以便共享 agent 读到同一份文件系统命名空间；
  - 作为 `AnnounceDispatcher` —— 子 agent 完成时把 announce 包成 USER 消息回灌父 agent，
    再把结果通过 `ChannelManager.deliver()` 推回原渠道。
- **重要方法**：
  - `run(ctx, msgs)` / `run(ctx, msgs, outboundAddress)` —— 主入口。
  - `bindMainAgent(agent)` / `registerAgent(id, agent)` —— 注册。
  - `findAgent(id)` —— 反查（控制器用）。
  - `tryDispatchAnnounce(completion)` —— 子 agent 回灌。
  - `restorePersistedMainSessions()` —— 重启后从 `SessionStore` 恢复路由表。

#### 子包 `runtime.session`
| 类 | 作用 |
|---|---|
| `SessionAgentManager` ★ | 会话+并发的总管：sessionsByKey/labelToSessionKey/childrenByParent 三大注册表；`subagentLane`/`nestedLane` 两个全局信号量；per-sessionKey ReentrantLock；agent 实例缓存；`registerMainSession` / `registerSession` / `execute` / `resetSession` / `runMaintenance` / `drainPendingCompletions` |
| `SessionStore` | `sessions.json` JSON 持久化（读写锁 + 原子 rename） |
| `SessionEntry` | 单条 session 元数据（含 gateKey、userId 等） |
| `SessionView` | 对外暴露 record（去掉可变内部细节） |
| `SessionKind` | `MAIN` / `SUBAGENT` |
| `SpawnResult` | 注册 session 的结果 |
| `SendResult` | 一次 prompt 执行的结果 |
| `HistoryResult` | 读取 session 文件后的结果 |
| `PendingCompletion` | 子 agent 完成的"通告"载荷 |
| `CommandLane` | `MAIN / SUBAGENT / NESTED` —— 决定走哪个 lane semaphore |
| `CleanupPolicy` | `KEEP / DELETE` —— 子 agent 完成后是否清理 |
| `AgentManagerConfig` | SessionAgentManager 的并发/announce/maintenance 调优参数 |
| `SessionResetPolicy` | `NEVER / DAILY / IDLE / BOTH` |
| `SessionMaintenanceConfig` | `enabled / pruneAfterMs / maxEntries` |
| `SessionFreshness` / `SessionFreshnessEvaluator` | "这个 session 还新鲜吗"评估器 |
| `SubagentRunRegistry` | 子 agent run 的内存登记（observability） |
| `SessionConstants` | `MAX_SPAWN_DEPTH = 3`、`ROOT_REQUESTER_SESSION_KEY` |
| `tool/SessionsTool` ★ | 暴露给 agent 的"会话工具"：`sessions_spawn` / `sessions_send` / `sessions_list` / `sessions_history` / `sessions_pending_completions` |

#### 子包 `runtime.outbound`
| 类 | 作用 |
|---|---|
| `OutboundController` | `POST /api/outbound/send` —— Agent/外部主动外发的 HTTP 入口（需 ≥ RUN，且 agentId 与 channel 路由一致） |
| `OutboundRequest` | record：channelId/peerKind/peerId/accountId/threadId/text/markdown/agentId |
| `OutboundService` | 把请求转成 `OutboundAddress + Msg`，调 `ChannelManager.deliver`；并通过 ChannelRouter 探针校验路由合规 |
| `OutboundTool` | `@Tool("outbound_send")` —— 注册到每个 HarnessAgent 的 Toolkit，可让 agent 主动推消息到 IM |

#### 子包 `runtime.marketplace`
| 类 | 作用 |
|---|---|
| `BuilderMarketplace` | 接口：id / type / displayLocation / list / fetch / close |
| `GitBuilderMarketplace` | 委托 `GitSkillRepository` 做 clone/pull/列表 |
| `NacosBuilderMarketplace` | 用 nacos `AiMaintainerService.skill()` 分页拉 SKILL.md |
| `UserMarketplaceRegistry` | per-user 的活跃 marketplace 实例池（懒加载、`@PreDestroy` 关闭） |
| `UserMarketplacePersistence` | 数据库读写 + 与 Registry 联动（reload / unregister） |
| `MarketSkillSummary` | 列表项 record |
| `MarketSkillContent` | 完整 skill 内容（markdown + 资源 map） |

---

### 4.3 包 `io.agentscope.builder.web.config`

#### `BuilderConfig`
- 三个核心 `@Bean`：
  - `dashscopeModel()` —— 如果配了 key 且没人提供 Model bean，自动构造 DashScope。
  - `baseStore(DataSource)` —— 用 Spring DataSource 构造 `JdbcStore`。
  - `builderBootstrap(...)` ★ —— 调用 `BuilderBootstrap.builder()`，**给每个 agent 自动注入**
    `ToolNotificationMiddleware` + 共享 `AgentStateStore` + `RemoteFilesystemSpec`（IsolationScope.USER，
    `activity/` 走共享前缀）。最后启动 ChatUiChannel。
- 还提供 `IdentityLinkStore` 与 `ChatUiChannel` 两个 bean。

#### `SecurityConfig`
- `@EnableWebFluxSecurity`。
- 规则：
  - `POST /api/auth/login` 公开
  - `/actuator/health` `/actuator/info` 公开
  - `/api/**` 必须认证
  - 其余公开（SPA 资源）
- 内嵌 `JwtAuthFilter`：从 `Authorization: Bearer ...` 解析 JWT → 写入
  `ReactiveSecurityContextHolder`（principal = userId，authorities = `ROLE_*`）。

#### `WebConfig`
- 一个 `RouterFunction` —— 任何 `不以 /api 开头、不含点、不以 /actuator 开头` 的 GET
  转给 `classpath:/static/index.html`（SPA fallback）。

---

### 4.4 包 `io.agentscope.builder.web.auth`

| 类 | 端点 / 作用 |
|---|---|
| `AuthController` | `POST /api/auth/login`（→ `LoginResponse{token,userId,username,roles}`）；`GET /api/auth/me`（→ `MeResponse`） |
| `UserController` | `GET /api/user/profile`、`POST /api/user/change-password` |
| `JwtService` | HS256 JWT 签发/解析（7 天 TTL，secret 自动补 32 字节） |
| `UserStore` | 接口：findById/findByUsername/listAll/createUser/verifyPassword/updatePassword/updateRoles/deleteUser；`UserRecord{userId,username,passwordHash,roles}` |

---

### 4.5 包 `io.agentscope.builder.web.api`（控制器集合）

> 几乎所有控制器都遵循"`Mono.fromCallable` + `guard.require` + 业务方法"模板，统一 404/403/409。

#### `ChatController` ★
| 端点 | 说明 |
|---|---|
| `POST /api/agents/{agentId}/chat/stream` | **SSE 流**：`tool_call` / `tool_result` / `token` / `done` / `error`。订阅 `ToolEventBus.subscribe(sessionKey)` 拿工具事件，并把 `executeChat(...)` 返回的 `Mono<Msg>` 转成 token+done 帧合流。 |
| `GET  /api/agents/{agentId}/chat/session` | 返回当前 (userId, agentId) 的 sessionKey（用于刷新时回填历史） |
| `POST /api/agents/{agentId}/chat/send` | 同步版本（不走 SSE） |

slash 命令拦截：`/new`、`/reset` 会调用 `sessionAgentManager.resetSession(sessionKey)`；
`/dock_<channel> <externalId>` 写入 `IdentityLinkStore`。

#### `SessionController`
- 路径 `/api/agents/{agentId}/sessions/*`
- `GET /inbox`、`GET /{key}`、`POST /{key}/reset`、`PATCH /{key}/read`、`DELETE /{key}`
- 所有读写都校验 `entry.userId() == 调用者` 且 `entry.gateKey()` 匹配预期 gateKey，
  防止跨用户/跨 agent 访问。
- 读 transcript 的实际路径是 `agents/<innerAgentId>/sessions/<sessionId>.log.jsonl`，
  通过 `WorkspaceManager.readManagedWorkspaceFileUtf8` 走 composite/remote 文件系统。

#### `AgentBindingController`（per-agent 通道绑定）
- `GET/POST/PUT/DELETE /api/agents/{agentId}/bindings`
- 写操作走 `BindingPersistence.mutate(...)`：加锁 → 改 `agentscope.json` → 原子 rename → 热加载到
  对应 `Channel.applyRoutingConfig(...)`。

#### `BindingPersistence`
- 不是控制器，而是被多个控制器复用的"配置变更服务"。

#### `ChannelDirectoryController`
- `GET /api/channels` —— 合并 live + 持久化的通道列表
- `GET /api/channels/types` —— `ChannelTypeRegistry` 已注册类型
- `GET/POST/PUT/DELETE /api/channels/{id}` —— **仅 admin**，凭证字段自动 mask
- `POST /api/channels/{id}/enable|disable`
- `POST /api/agents/{agentId}/channels/{channelId}/default`

#### `MarketplacesController`
- 个人私有 marketplace：`GET/POST/PUT/DELETE /api/marketplaces[/{id}]`
- 联通性测试：`POST /api/marketplaces/test`、`POST /api/marketplaces/{id}/test`
- 技能列表/详情：`GET /api/marketplaces/{id}/skills[/{name}]`

#### `AgentSkillsController`
- workspace 内 skills：`GET/PUT/DELETE /api/agents/{agentId}/skills/workspace[/{name}]`
- agent 已挂的仓库：`GET /api/agents/{agentId}/skills/repositories[/{index}/skills[/{name}]]`
- 从仓库/市场安装：`POST .../workspace/install`、`POST .../workspace/marketplace-install`
- 每次写后调用 `catalogService.invalidateUca(...)` 让下次会话重建 agent。

#### `AgentToolsController`
- `GET /api/agents/{agentId}/tools/active` —— 现存可用 Tool（含 MCP）
- `GET/PUT /api/agents/{agentId}/tools/config` —— 读写 workspace 内 `tools.json`
- `GET .../tools/catalog/builtins`、`GET .../tools/catalog/mcp-servers` —— 静态目录

#### `AgentWorkspaceController`
- 工作区文件 CRUD：
  - `GET    /api/agents/{agentId}/workspace`（摘要）
  - `POST   .../scaffold`（写 AGENTS.md）
  - `GET    .../memory`、`/files`、`/file?path=`
  - `PUT/POST/DELETE .../file`
  - `POST   .../file/move`
  - `POST   .../upload`（multipart）
- subagent CRUD：`GET .../subagents`、`PUT .../subagents/{name}`、
  `POST .../subagents/from-agent`、`DELETE .../subagents/{name}`
- 所有 IO 经 `HarnessAgent.workspaceFor(ctxUser,null)`，并对路径做 `validatePath` 防穿越。

#### `AgentShareController`
- `GET/POST /api/agents/{id}/shares`、`DELETE /api/agents/{id}/shares/{type}/{granteeId}`
- 仅作用于 SCOPE_USER agent，全局 agent 报 409。

#### `AgentCloneController`
- `POST /api/agents/{id}/clone` —— 复制 entry + 用 `WorkspaceCopier` 复制文件
- 全局 agent 拒绝克隆。

#### `AgentActivityController`
- `GET /api/agents/{id}/activity?since=&limit=` —— 读 `AgentActivityStore`
- 非 EDIT 调用者，对 GRANT/REVOKE_SHARE 和 BIND_*/UNBIND_*/EDIT_BINDING 事件做脱敏。

#### `AdminUserController` （仅 admin）
- `GET /api/admin/users`、`POST /api/admin/users`、`PATCH .../password`、
  `PATCH .../roles`、`DELETE .../users/{userId}`
- 删除用户时级联撤销所有 `(USER, deletedId)` 类型的 grant。

---

### 4.6 包 `io.agentscope.builder.web.catalog`

#### `AgentCatalogService` ★
- 业务中心：合并"全局 agent（来自 `agentscope.json`）"+"用户自定义 agent（来自 JPA）"+
  "被分享给我的 agent"。
- 关键方法：
  - `listVisible(userId)` / `findVisible(userId, id)` / `findOwnerOf(id)` / `isGlobal(id)`
  - `createUserAgent` / `updateUserAgent` / `deleteUserAgent` / `prepareClone`
  - `getRunningAgent` / `getOrInstantiateRunningAgent` —— 用户自定义 agent 第一次被访问时，
    用 `buildAndRegisterUca` 把它构造成 HarnessAgent，注册进 gateway，缓存 gatewayId。
  - `resolveGatewayAgentId` / `peekGatewayAgentId` —— 把 `agentId` 映射到 gateway 里的
    实际 ID（全局保持原 id；用户 agent 是 `uca-<ownerId>-<agentId>`）。
  - `resolveFilesystemUserId` —— **注册给 gateway 作为 `fsUserIdResolver`**：让
    SCOPE_USER agent 的所有调用者都读 owner 的 workspace。
  - `invalidateUca` —— 设置变更后丢弃缓存，下次会话重建。

#### `AgentCatalogController`
- `GET /api/agents`、`GET /api/agents/{id}`、`POST /api/agents`、`PUT /api/agents/{id}`、
  `DELETE /api/agents/{id}`
- 返回的每个 `AgentDefinition` 都会调用 `aclService.tierFor(...)` 写入 `tierForCurrentUser`
  字段，便于前端按 tier 显示/禁用。

#### `AgentDefinition`（record）
- 完整对外字段：tools.allow/deny / identity / groupChat / skills / shares / runAs /
  forkOf / workspacePath / sandboxMode / sandboxScope / **tierForCurrentUser**
- 常量：`SCOPE_GLOBAL / SCOPE_USER`、`RUN_AS_INVOKER / RUN_AS_OWNER`

#### `UserAgentDefinitionStore` + `StoredEntry`
- 抽象层；实现是 `JpaUserAgentDefinitionStore`。
- `StoredEntry.toDefinition(ownerId)` 把存储记录转成 API record；
  `toConfigEntry()` 转成 `AgentConfigEntry` 供 HarnessAgent 装配使用。

---

### 4.7 包 `io.agentscope.builder.web.share`

| 类 | 作用 |
|---|---|
| `AgentAclService` | 单一 ACL 计算入口：tier 排序 `EDIT > RUN > CLONE`；全局 agent 给登录者 EDIT；owner 自己 EDIT；按 grants 解析最高 |
| `AgentAccessGuard` | 控制器统一的 `require(userId, agentId, Tier)`、`load(...)` 简便方法 |
| `AgentShareGrant` | record：`granteeType(USER/WORKSPACE) / granteeId / tier / createdAt / createdBy` |

---

### 4.8 包 `io.agentscope.builder.web.persistence.jpa`

| 类 | 作用 |
|---|---|
| `JpaPersistenceConfig` | `@EnableJpaRepositories` + 注册 `UserStore` `UserAgentDefinitionStore` 两个 bean |
| `UserEntity` / `UserEntityRepository` | `builder_user` 表（roles 用 CSV） |
| `JpaUserStore` | 实现 `UserStore`，BCrypt 加密；`@PostConstruct` 自动种 `admin/admin` |
| `AgentEntity` / `AgentEntityRepository` | `builder_agent` 表（list 类字段 JSON 序列化、shares 走 OneToMany） |
| `AgentShareEntity` | `builder_agent_share` 表 |
| `JpaUserAgentDefinitionStore` | 实现 `UserAgentDefinitionStore` |
| `UserMarketplaceEntity` / `UserMarketplaceRepository` | `builder_user_marketplace` 表 |

---

### 4.9 包 `io.agentscope.builder.web.audit`

| 类 | 作用 |
|---|---|
| `ActivityEvent` | record + `Action` 字符串常量集合（CREATE/EDIT_FILE/CLONE_FROM/.../RUN_SESSION） |
| `AgentActivityStore` | append-only JSONL，写到 `<agent-workspace>/activity/activity.jsonl`；超 1MiB 自动 rotate；per-(owner,agent) 锁；丢失/损坏行容错；公司级共享 BaseStore 上可见（因为 BuilderConfig 把 `activity/` 设为共享前缀） |

---

### 4.10 包 `io.agentscope.builder.web.toolbus`

| 类 | 作用 |
|---|---|
| `ToolEventBus` | Reactor `Sinks.Many` 多播总线，按 sessionKey 过滤；`publish(ToolEvent)` |
| `ToolNotificationMiddleware` | `MiddlewareBase.onActing` 钩子，每次 ReAct 的 Acting 前为每个 tool call 发一条 `TOOL_CALL` 事件给 bus → SSE 路径透传到前端 |

---

### 4.11 包 `io.agentscope.builder.web.session`

| 类 | 作用 |
|---|---|
| `SessionLifecycleScheduler` | `@PostConstruct` 注册 3 个定时器：每分钟跑 idle reset、每天 dailyAt 跑 daily reset、每 5 分钟跑 maintenance |
| `SessionReadStateStore` | per-(user,session) 的"最后已读时间"，持久化到 `.agentscope/session-read-state.json` |
| `SessionTurnParser` | 把 HarnessAgent 写的 JSONL transcript 解析成 `TurnEntry` 列表（消息/工具调用/结果） |

---

### 4.12 包 `io.agentscope.builder.web.template`

| 类 | 作用 |
|---|---|
| `TemplateRegistry` | 启动扫描 classpath `templates/<id>/template.json`；运行期再扫 `${cwd}/.agentscope/templates/*`；提供 `list`/`get`/`instantiate(id, workspaceDir)` |
| `TemplateController` | `GET /api/templates`、`GET /api/templates/{id}` |

---

### 4.13 包 `io.agentscope.builder.web.scaffold`

#### `WorkspaceScaffolder`
- 静态工具类：从 `classpath:scaffold/default/*` 复制 `AGENTS.md`（含占位符替换）、
  `tools.json`、`skills/example-skill/SKILL.md`、`subagents/README.md`、空 `memory/.gitkeep`。
- 写时跳过已存在文件。

---

### 4.14 包 `io.agentscope.builder.web.workspace`

| 类 | 作用 |
|---|---|
| `SharedWorkspacePaths` | 把 user 自定义的 workspacePath 解析成绝对路径（绝对原样；相对落 `${cwd}/.agentscope/`） |
| `BuilderWorkspaceConfig` | 唯一作用：暴露 `SharedWorkspacePaths` bean |
| `NamespacedFilesystemView` | 通用的"`NamespaceFactory` + `AbstractFilesystem` 包装器"，给每个调用透明加 `[users, userId, agents, agentId]` 前缀；目前主要给后续多租户化用 |

---

### 4.15 包 `io.agentscope.builder.web.util`

#### `WorkspaceCopier`
- 静态工具：`glob("**/*")` + `read` + `uploadFiles`，把一个 agent 的 workspace 完整复制到另一个；
- 跳过 `activity/*.jsonl` 文件以确保 Clone 的新 agent 有干净审计；
- 由 `AgentCloneController` 调用。

---

### 4.16 包 `io.agentscope.builder.web.usage`

#### `UsageStore`
- 进程内内存事件队列（`CopyOnWriteArrayList`，上限 50 000），不持久化。
- 提供 `record / recentEvents / hourlyTurns / dailyTurns / summary / topUsers / topAgents`，
  per-user 版同样支持。
- 当前没有独立控制器暴露，被 `ChatController.executeChat` 在每次 reply 后调用 `record(...)`。

---

### 4.17 包 `io.agentscope.builder.web.identity`

#### `IdentityLinkStore`
- 存 `.agentscope/identity-links.json`，结构 `userId → channelId → externalId`。
- 提供 `link/unlink/snapshot/linksFor/externalIdFor/userIdByExternal`。
- 由 `ChatController` 的 `/dock_<channel>` slash 命令调用。

---

### 4.18 包 `io.agentscope.builder.web.ai`

| 类 | 作用 |
|---|---|
| `AgentDraftController` | `POST /api/agents/draft` —— 输入 `description`，输出 `AgentDraft`（建议 name/sysPrompt/工具/skill/subagent 文件） |
| `AgentDraftService` | 加载 `classpath:prompts/agent-draft.md`，调用 `Model.stream(...)` 一次，宽容解析返回的 JSON（剥 code fence、提取最外层 `{...}`） |

---

## 5. 关键时序补充

### 5.1 用户自定义 Agent 首次被使用

```
浏览器 POST /api/agents/my-agent/chat/stream
  └─ ChatController.executeChat
       └─ catalogService.resolveGatewayAgentId(userId, "my-agent")
            ├─ findVisible → 看到 SCOPE_USER 的条目
            ├─ findOwnerOf → 得到 ownerId
            └─ registeredUcaIds.computeIfAbsent(ownerId+"/my-agent", buildAndRegisterUca)
                  └─ HarnessAgent.builder().agentId("uca-<owner>-my-agent")...
                        ↓
                  builderBootstrap.gateway().registerAgent("uca-<owner>-my-agent", agent)
                  gatewayIdToOwner.put(...)
       └─ ChatUiChannel.dispatch(InboundMessage.dmFor(chatui, userId, "uca-<owner>-my-agent", msgs))
            └─ HarnessGateway.run(...)
                  ├─ fsUserIdResolver("调用者", "uca-<owner>-my-agent") = ownerId
                  │   （所以 RuntimeContext.userId = ownerId → 文件系统命名空间锁定到 owner）
                  └─ ha.call(...)
```

### 5.2 Agent 调子 Agent（OpenClaw 风格）

```
HarnessAgent.call(...)
  └─ ReAct 循环触发 tool call: sessions_spawn(agent_id="researcher", task="...")
        └─ SessionsTool.sessionsSpawn
              └─ sessionAgentManager.registerSession(...)
                    └─ spawnInterceptor.onSpawn(...)（HarnessGateway 记 gateKey/agentId/route）
              └─ sessionAgentManager.execute(sessionKey, prompt, timeout, announceOnComplete=true, lane=SUBAGENT)
                    ├─ subagentLane.acquire()
                    ├─ 真正调 invokeAgent
                    └─ finishRun → maybeEnqueueAnnounce
                          └─ announceDispatcher.dispatch(PendingCompletion)
                                ↳ HarnessGateway.tryDispatchAnnounce(...)
                                      ├─ 构造 USER 消息 "AgentScope runtime context (internal): ..."
                                      ├─ withGatedTurn → ha.call(announceMsg)
                                      └─ 把 reply 用 channelManager.deliver(...) 推回原渠道
```

### 5.3 持久化层启动顺序

1. `application.yml` → `DataSource`（H2 file 默认）
2. Hibernate `ddl-auto=update` 建表（`builder_user` / `builder_agent` / `builder_agent_share` / `builder_user_marketplace`）
3. `data-h2.sql`（只在 H2 profile 跑）插入 bob / alice 演示账号（MERGE INTO）
4. `JpaUserStore.@PostConstruct.seedDefaultAdmin()` 若没 admin 则插 `admin/admin`
5. `BuilderConfig.builderBootstrap(...)` 启动 HarnessAgent + Gateway + ChatUiChannel
6. `SessionLifecycleScheduler.@PostConstruct.start()` 调度定时任务

---

## 6. 入口/出口速查表

### 6.1 HTTP 入口（一览）

| Method | Path | Controller | 鉴权 |
|---|---|---|---|
| POST | `/api/auth/login` | AuthController | 公开 |
| GET | `/api/auth/me` | AuthController | JWT |
| GET/POST | `/api/user/profile`, `/change-password` | UserController | JWT |
| GET/POST/PUT/DELETE | `/api/agents[/{id}]` | AgentCatalogController | JWT |
| GET | `/api/agents/{id}/activity` | AgentActivityController | RUN |
| POST | `/api/agents/{id}/clone` | AgentCloneController | CLONE |
| GET/POST/DELETE | `/api/agents/{id}/shares[/...]` | AgentShareController | RUN/EDIT |
| `/api/agents/{id}/workspace/**` | AgentWorkspaceController | RUN/EDIT |
| `/api/agents/{id}/skills/**` | AgentSkillsController | RUN/EDIT |
| `/api/agents/{id}/tools/**` | AgentToolsController | RUN/EDIT |
| `/api/agents/{id}/bindings/**` | AgentBindingController | RUN/EDIT |
| `/api/agents/{id}/sessions/**` | SessionController | JWT + 用户/通道匹配 |
| POST stream/send + GET session | `/api/agents/{id}/chat/**` | ChatController | RUN |
| GET/POST/PUT/DELETE | `/api/channels[/...]` | ChannelDirectoryController | JWT (admin 用于变更) |
| GET/POST/PUT/DELETE | `/api/marketplaces[/...]` | MarketplacesController | JWT |
| GET | `/api/templates[/{id}]` | TemplateController | JWT |
| POST | `/api/agents/draft` | AgentDraftController | JWT |
| POST | `/api/outbound/send` | OutboundController | JWT (+ RUN 检查) |
| `/api/admin/users/**` | AdminUserController | admin |
| `/api/agents/{agentId}/channels/{channelId}/default` | ChannelDirectoryController | JWT |
| any GET 静态/SPA | `WebConfig.spaFallback` | 公开 |

### 6.2 出口（一览）

| 出口 | 触发 |
|---|---|
| Web 同步 JSON 响应 | 绝大部分 `Mono<XxxResponse>` |
| SSE 事件（token/tool_call/tool_result/done/error） | `ChatController.stream` |
| IM 出站消息 | `OutboundController.send` → `OutboundService.send` → `ChannelManager.deliver` |
| Agent 主动出站 | `OutboundTool.send`（同上）|
| 子 agent 完成回灌 + 通过原渠道下发 | `HarnessGateway.tryDispatchAnnounce` → `channelManager.deliver` |
| 写文件（workspace 任何修改） | 都通过 `WorkspaceManager` → `AbstractFilesystem` → `BaseStore`（远程共享） |
| 数据库写 | JPA `Repository.save/delete`（事务化） |
| 配置文件写 | `BindingPersistence.writeAtomic` 原子 rename `agentscope.json` |

---

## 7. 重要约定与坑

1. **`gateKey` ≠ `sessionKey`**：网关用 `MsgContext.canonicalKey()` 做路由键，
   `SessionAgentManager` 内部主键是 `sessionKey`，二者由 `contextKeyToSessionKey` 双向映射。
2. **用户自定义 agent 在 gateway 的真 ID 是 `uca-<ownerId>-<agentId>`**，目录展示用
   `agentId`，控制器需用 `AgentCatalogService.resolveGatewayAgentId` 转换。
3. **SCOPE_USER agent 的所有调用者读同一个 owner workspace**，由
   `HarnessGateway.fsUserIdResolver`（来自 `AgentCatalogService.resolveFilesystemUserId`）
   实现。会话路由仍按调用者隔离 —— 每个 caller 一条独立对话线程。
4. **每次 EDIT 后必须调 `catalogService.invalidateUca(...)`**，下次会话才会用最新设置
   重建 HarnessAgent，否则缓存里的旧实例还在跑老 prompt/tools。
5. **`activity/` 必须落共享存储**（BuilderConfig 加了 `addSharedPrefix("activity/")`），
   否则多副本部署时审计日志会分散在不同 pod。
6. **agentscope.json 是平台共享**，不是 per-user 的，每次写都要走 `BindingPersistence`
   的锁 + 原子 rename，避免多请求竞争。
7. **`OutboundController` 的 agentId 校验**：要求路由到的目标 agent 与 caller 自报一致，
   防止 Agent A 借 Channel B 的 binding 偷发消息。

---

## 8. 一些可改进点（来自 builder.md 与本次阅读所见）

- `web/workspace/NamespacedFilesystemView` 当前**没有任何 bean 使用**（早期多租户拆分残留），
  可移除；同包 `BuilderWorkspaceConfig` 也只剩一个简单 bean，可合并。
- `runtime/session/CleanupPolicy` 枚举存在但当前没人引用，可以删除或在
  `SessionsTool` 里实装。
- `UsageStore` 是内存的，重启会丢；如果做生产化运营建议改为 JPA + 异步落库。
- `SessionsTool` 内嵌的子 agent run 系列功能可以独立成 service 与 controller，
  目前只能通过 agent 工具触发，没有直接的 REST API。
- `MarketplacesController` 在错误码上把"跨用户读他人 marketplace"统一返回 404 而非 403，
  避免 enumeration —— 这是规范，但前端要小心区分"真不存在"与"不可见"。

---

**End of analysis** —— 本文档随源码演进，欢迎在新增模块时同步追加。
