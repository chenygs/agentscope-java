# AgentScope 代码可编程智能体平台 — 设计方案

> 基于 `agentscope-builder` 扩展，面向 Java 开发者，支持在前端编写 Groovy 代码定义 Tool/Middleware/Hook，动态编译运行。
> 创建日期: 2026-06-06

---

## 目录

1. [核心理念](#一核心理念)
2. [与 builder 的关系](#二与-builder-的关系)
3. [功能全景](#三功能全景)
4. [数据库设计](#四数据库设计)
5. [代码编译引擎](#五代码编译引擎)
6. [模块结构](#六模块结构)
7. [API 设计](#七-api-设计)
8. [前端改动](#八前端改动)
9. [运行时装配流程](#九运行时装配流程)
10. [安全设计](#十安全设计)
11. [开发阶段规划](#十一开发阶段规划)
12. [关键风险与应对](#十二关键风险与应对)

---

## 一、核心理念

### 一切皆代码，代码即配置

| 分层 | 用户可编辑 | 编辑方式 | 存储位置 |
|------|-----------|---------|---------|
| 敏感配置 | ❌ 管理员维护 | application.yml / 环境变量 | 文件系统 / 密钥管理服务 |
| **系统内置工具** | ❌ 框架提供，用户选择 | — | AgentScope ToolRegistry |
| **用户自建 Tool 代码** | ✅ **在线编辑** | Monaco 编辑器 (Groovy) | `tool_definition` 表 |
| **Middleware 代码** | ✅ **在线编辑** | Monaco 编辑器 (Groovy) | `middleware_definition` 表 |
| **Agent 拼装代码** | ✅ **在线编辑** | Monaco 编辑器 (Groovy) | `agent_definition` 表 |
| 文档资产 | ✅ Markdown 编辑 | Markdown 编辑器 | workspace 文件系统 |
| 权限规则 | ✅ 在线配置 | 表格 + JSON | `permission_rule` 表 |
| 运行时记忆 | ❌ 自动 | — | Session 持久化 |

### 对比现有 builder

| 维度 | agentscope-builder | 本平台 |
|------|-------------------|--------|
| 配置方式 | JSON + workspace 文件 | **代码 (Groovy)** 为主 |
| 目标用户 | 非技术用户（写 Markdown） | **Java 开发者** |
| 工具定义 | 框架预制，用户只能 allow/deny | **系统内置 + 用户在线创建** |
| Middleware | 框架硬编码 | **用户在线创建** |
| Hook | 框架硬编码 | **用户在线创建** |
| 编译 | 无 | Groovy 动态编译 |
| CS 背景要求 | 低 | 中高 |

---

## 二、与 builder 的关系

### 复用（不改动）

| 模块 | 原因 |
|------|------|
| `BuilderBootstrap` | 完整的 Builder 模式 + config 加载 |
| `AgentscopeConfig` | JSON 配置结构定义 |
| `Channel` + `ChannelRouter` | 多 Channel 接入架构（ChatUI、钉钉、飞书等） |
| `ChatUiChannel` | SSE 对话流 |
| `HarnessGateway` | 多 Agent 路由 + Session 隔离 |
| `SessionAgentManager` | 会话生命周期管理 |
| `ChatController` | SSE 对话接口 |
| `AuthController` + `JwtService` + `UserStore` | 用户认证 |
| `AgentCatalogService` + `AgentCatalogController` | Agent CRUD + 可见性管理 |
| `AgentAclService` + `AgentAccessGuard` | 分享 / ACL 权限 |
| `AgentShareGrant` | 分享机制 |
| `WorkspaceScaffolder` | 工作区脚手架 |
| `TemplateRegistry` | 模板管理 |
| `AgentActivityStore` | 审计日志 |
| `IdentityLinkStore` | 多 Channel 身份关联 |
| `ToolEventBus` + `ToolNotificationMiddleware` | 工具事件实时推送 |
| `SharedWorkspacePaths` | 多用户的数据路径隔离 |
| `RemoteFilesystemSpec` + `BaseStore` | 文件系统持久化 |

### 扩展（新增代码）

| 模块 | 位置 |
|------|------|
| Groovy 编译引擎 | 新增 `agentscope-compiler` 模块 |
| Tool Definition 管理 API | 在 builder 的 `web/api/` 新增 |
| Middleware Definition 管理 API | 在 builder 的 `web/api/` 新增 |
| **内置工具注册表 (BuiltinToolRegistry)** | 在 builder 的 `web/service/` 新增 |
| **内置工具发现服务** | 扫描 AgentScope 的 ToolRegistry/ToolGroup |
| Agent 动态装配服务 | 在 builder 的 `web/` 新增 |
| 前端 Tool/Middleware 编辑器 | 在 builder 的 `frontend/src/` 新增 |
| 安全 AST 分析器 | 新增到 compiler 模块 |
| 在线编译缓存 | 新增到 compiler 模块 |

### 修改（需要改现有代码）

| 文件 | 改动 |
|------|------|
| `AgentDefinition` (record) | 新增 `toolIds`、`middlewareIds`、`assembledAgentCode` 字段 |
| `AgentCreateRequest` (record) | 新增对应的字段 |
| `AgentCatalogService.getOrInstantiateRunningAgent()` | 新增动态编译 + 装配逻辑 |
| `HarnessAgent.Builder` 调用方式 | 从预编译模式改为动态注入模式 |
| `UserAgentDefinitionStore.StoredEntry` | 新增 tool/middleware 引用字段 |
| `BuilderConfig` | 可选: 添加 Compiler Bean |
| 前端 AgentSettingsForm | 新增 Tool/Middleware 选择面板 |
| 前端 AgentCreatePage | 新增「拼装代码」编辑器 |

---

## 三、功能全景

### 3.1 用户故事

```
作为 Java 开发者 Alice，我想：
1. 在浏览器中写一个计算器 Tool，编译通过后保存为模板
2. 创建一个 Agent，从工具面板里看到「系统内置工具」和「我创建的工具」两类
3. 从系统内置工具中选择 read_file、write_file，从我创建的工具中选择 CalculatorTool
4. 写一个 Middleware，记录每次用户输入的字数
5. 把 Middleware 加到 Agent 的中间件链
6. 写 Agent 拼装代码，把 Model + Tool + Middleware 组装成 HarnessAgent
7. 在对话页面测试，实时看到工具调用过程
8. 如果需要新功能，直接改代码、重新编译、继续对话
```

### 3.2 功能清单

#### Agent 管理
- [x] CRUD Agent（复用 builder）
- [x] Agent 分享 / ACL（复用 builder）
- [ ] **Agent 拼装代码编辑**（新增）
- [ ] **从已编译的 Tool/Middleware 列表中选择**（新增）

#### Tool 管理（新增）
- [ ] Monaco 编辑器写 Groovy 代码
- [ ] 客户端编译（语法验证）→ 服务端编译（完整编译）
- [ ] 编译结果展示（成功/失败 + 行号）
- [ ] 已编译工具列表
- [ ] **内置工具目录** — 自动发现 AgentScope 框架中所有已注册的内置工具
- [ ] 内置工具在编辑 Agent 时可直接选择，无需编译
- [ ] 工具版本管理（可选）
- [ ] 工具依赖声明（可选）

#### Middleware 管理（新增）
- [ ] Monaco 编辑器写 Groovy 代码
- [ ] 客户端 + 服务端编译
- [ ] 编译结果展示
- [ ] Middleware 列表 + 排序

#### Hook 管理（新增，v2）
- [ ] 定义 ReAct loop 各阶段的 Hook
- [ ] 支持 PreReasoning / PostReasoning / PreActing 等钩子点

#### 会话
- [x] SSE 对话（复用 builder）
- [x] Session 管理（复用 builder）
- [x] Slash 命令（复用 builder）

#### 权限
- [x] 工具级别权限规则（复用 builder）
- [ ] 限制用户代码中可调用的 API 包名（新增安全约束）

---

## 四、数据库设计

### 4.1 工具定义表 `tool_definition`

```sql
CREATE TABLE tool_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       COMMENT '关联 agent_definition.id, null=共享工具模板',
    name            VARCHAR(100) NOT NULL COMMENT '工具名, @Tool 注解上的名称',
    description     VARCHAR(500) COMMENT '工具描述',

    -- 工具来源: builtin=系统内置, user=用户在线创建
    source          VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT 'builtin/user',

    -- 用户创建工具的代码字段 (source=user 时使用)
    code            MEDIUMTEXT   COMMENT 'Groovy 源码 (source=user 时填写)',

    -- 内置工具的类全名 (source=builtin 时使用, 如 io.agentscope.harness.agent.tool.FilesystemTool)
    class_name      VARCHAR(300) COMMENT '内置工具 Java 类全名',

    compiled_class  VARCHAR(200) COMMENT '编译后的类全名 (用于缓存加速)',
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/compiled/error / ready(builtin)',
    compile_error   MEDIUMTEXT   COMMENT '编译错误信息 (含行号)',
    shared          BOOLEAN DEFAULT FALSE COMMENT '是否共享给所有 agent',
    created_by      VARCHAR(100) COMMENT '创建用户 id',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_id (agent_id),
    KEY idx_source (source),
    KEY idx_status (status)
) COMMENT='工具定义';
```

### 4.2 中间件定义表 `middleware_definition`

```sql
CREATE TABLE middleware_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       COMMENT '关联 agent_definition.id, null=共享',
    name            VARCHAR(100) NOT NULL COMMENT '中间件名',
    description     VARCHAR(500) COMMENT '描述',
    hook_point      VARCHAR(30)  NOT NULL COMMENT 'AgentInput/ReasoningInput/ActingInput/ModelCallInput',
    code            MEDIUMTEXT   NOT NULL COMMENT 'Groovy 源码',
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/compiled/error',
    compile_error   MEDIUMTEXT   COMMENT '编译错误信息',
    shared          BOOLEAN DEFAULT FALSE,
    created_by      VARCHAR(100),
    sort_order      INT DEFAULT 0 COMMENT '排序 (当多个中间件挂在同一 hook_point 时)',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_id (agent_id),
    KEY idx_hook_point (hook_point)
) COMMENT='中间件定义';
```

### 4.3 Agent 定义表扩展

在 builder 原有结构上新增字段（builder 的 agent 定义存在 `user_agent_definition_store` 的 JSON 文件里，这里改为数据库表）：

```sql
-- 新增: Agent 与 Tool 的关联表
CREATE TABLE agent_tool_binding (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL COMMENT '关联 agent_definition.id',
    tool_id         BIGINT       NOT NULL COMMENT '关联 tool_definition.id',
    sort_order      INT DEFAULT 0,
    UNIQUE KEY uk_agent_tool (agent_id, tool_id)
) COMMENT='Agent 绑定工具';

-- 新增: Agent 与 Middleware 的关联表
CREATE TABLE agent_middleware_binding (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL COMMENT '关联 agent_definition.id',
    middleware_id   BIGINT       NOT NULL COMMENT '关联 middleware_definition.id',
    sort_order      INT DEFAULT 0 COMMENT '中间件执行顺序',
    UNIQUE KEY uk_agent_middleware (agent_id, middleware_id)
) COMMENT='Agent 绑定中间件';

-- 扩展 agent_definition 表 (在现有基础上新增字段)
-- 现有 agent_definition 已包含 name/description/status/provider_id/model_name
-- 新增:
--   assembled_code  MEDIUMTEXT  COMMENT 'Agent 拼装代码 (Groovy)'
--   compile_status  VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/compiled/error'
--   compile_error   MEDIUMTEXT  COMMENT '编译错误'
```

### 4.4 实体类

```java
@Entity
@Table(name = "tool_definition")
public class ToolDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;            // null = 共享工具
    @Column(nullable = false)
    private String name;
    private String description;

    /** builtin = 系统内置（框架自带）, user = 用户在线创建 */
    @Column(nullable = false)
    private String source = "user";

    /** source=user 时：Groovy 源码 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String code;

    /** source=builtin 时：Java 类全名（如 io.agentscope.harness.agent.tool.FilesystemTool） */
    private String className;

    private String compiledClass;    // 编译后的类名
    private String status = "draft"; // draft / compiled / error / ready
    @Column(columnDefinition = "MEDIUMTEXT")
    private String compileError;
    private Boolean shared = false;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** 是否内置工具（快捷判断） */
    public boolean isBuiltin() { return "builtin".equals(source); }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "middleware_definition")
public class MiddlewareDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;
    @Column(nullable = false)
    private String name;
    private String description;

    @Column(nullable = false)
    private String hookPoint;  // AgentInput / ReasoningInput / ActingInput / ModelCallInput

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String code;

    private String status = "draft";
    @Column(columnDefinition = "MEDIUMTEXT")
    private String compileError;

    private Boolean shared = false;
    private String createdBy;
    private Integer sortOrder = 0;
}
```

---

## 四、内置工具目录

### 4.1 什么是内置工具

AgentScope 框架自带一系列内置工具（如文件读写、Shell 执行、Memory 搜索等），这些工具在 `agentscope-harness` 和 `agentscope-builder` 中已经实现好，用户**不需要写代码**，只需要在 Agent 编辑页面勾选即可使用。

### 4.2 设计原则

> **内置工具不做数据库持久化，完全动态加载。**

系统内置的组件（工具、中间件、技能）全部在 Java 代码中定义，前端通过 API 实时获取，不写数据库。这是 builder 已有的做法——`AgentToolsController` 里有硬编码的 `BUILTIN_TOOLS` 列表。

只有**用户自建**的组件（自己写的 Groovy 代码）才存 `tool_definition` 表。

### 4.3 内置工具清单

内置工具来自三类来源：

| 来源 | 说明 | 发现方式 |
|------|------|---------|
| **HarnessAgent 标准工具** | AgentScope harness 模块自带，构建 Agent 时自动注册 | Java `List<BuiltinToolInfo>` 硬编码 |
| **Builder 业务工具** | agentscope-builder 特有的工具 | Java `List<BuiltinToolInfo>` 硬编码 |
| **运行时工具** | 已运行的 Agent 实例实际拥有的工具 | 运行时 introspect HarnessAgent 的 Toolkit |

#### 第一类：HarnessAgent 标准工具（来自 `agentscope-harness`）

这些工具在 `HarnessAgent.Builder.build()` 中自动注册，是所有 Agent 的「出厂标配」：

| 工具名 | 对应类 | 说明 |
|--------|--------|------|
| `read_file` | `FilesystemTool` | 读取文件 |
| `write_file` | `FilesystemTool` | 写入文件 |
| `edit_file` | `FilesystemTool` | 查找替换文件内容 |
| `grep_files` | `FilesystemTool` | 搜索文件内容 |
| `glob_files` | `FilesystemTool` | 按 glob 查找文件 |
| `list_files` | `FilesystemTool` | 列出目录 |
| `shell_execute` | `ShellExecuteTool` | 执行 Shell 命令 |
| `memory_search` | `MemorySearchTool` | 搜索长期记忆 |
| `memory_get` | `MemoryGetTool` | 获取记忆片段 |
| `session_search` | `SessionSearchTool` | 搜索会话历史 |
| `agent_spawn` | `AgentSpawnTool` | 创建子代理 |
| `agent_send` | `AgentSpawnTool` | 发送消息给子代理 |
| `agent_list` | `AgentSpawnTool` | 列出子代理 |
| `agent_generate` | `AgentGenerateTool` | 自动生成子代理配置 |
| `task_output` | `TaskTool` | 获取任务输出 |
| `task_cancel` | `TaskTool` | 取消任务 |
| `task_list` | `TaskTool` | 列出任务 |
| `propose_skill` | `ProposeSkillTool` | 向上司推荐技能 |
| `skill_manage` | `SkillManageTool` | 管理技能生命周期 |
| `plan_enter` | `PlanModeTools.PlanEnterTool` | 进入计划模式 |
| `plan_write` | `PlanModeTools.PlanWriteTool` | 编写计划 |
| `plan_exit` | `PlanModeTools.PlanExitTool` | 退出计划模式 |

#### 第二类：Builder 业务工具（来自 `agentscope-builder`）

这些工具只在 builder 项目中存在，由 `BuilderBootstrap` 在构建 Agent 时注册：

| 工具名 | 对应类 | 说明 |
|--------|--------|------|
| `outbound_send` | `OutboundTool` | 通过 Channel 向外发送消息（钉钉/飞书等） |
| `sessions_spawn` | `SessionsTool` | 创建子代理会话 |
| `sessions_send` | `SessionsTool` | 发送消息到子代理会话 |
| `sessions_list` | `SessionsTool` | 列出会话 |
| `sessions_history` | `SessionsTool` | 获取会话历史 |
| `sessions_pending_completions` | `SessionsTool` | 列出待完成的子代理 |

#### 第三类：运行时工具

Agent 启动后，通过 `GET /api/agents/{agentId}/tools/active` 接口可以 introspect 运行中 Agent 实际注册的工具列表（含 MCP 服务器注册的工具）。

### 4.4 内置工具的生命周期

```
┌────────────────────────────────────────────────────┐
│                编译期（Java 代码）                     │
│                                                     │
│  BuiltinToolCatalog.java                             │
│    └── 静态 BUILTIN_TOOLS 列表（同 builder 做法）     │
│        └── List.of(                                  │
│            BuiltinToolInfo("read_file", "文件读取"),   │
│            BuiltinToolInfo("write_file", "文件写入"),  │
│            ...                                        │
│        )                                              │
│                                                     │
├────────────────────────────────────────────────────┤
│                运行期（API）                           │
│                                                     │
│  GET /api/tools/catalog/builtins                     │
│    └── 返回 BuiltinToolCatalog.BUILTIN_TOOLS         │
│        → 前端展示「系统内置」标签页                       │
│                                                     │
│  GET /api/agents/{id}/tools/active                   │
│    └── introspect 运行中 HarnessAgent 的 Toolkit      │
│        → 前端展示「当前 Agent 实际使用的工具」            │
│                                                     │
├────────────────────────────────────────────────────┤
│                装配时                                  │
│                                                     │
│  用户勾选了 read_file + outbound_send                │
│    → agent_tool_binding 存:                          │
│        { tool_name: "read_file", source: "builtin" } │
│        { tool_name: "outbound_send", source: "builtin" } │
│    → 装配时按 name 查 BuiltinToolCatalog 得到类名     │
│    → 反射实例化 → 注入 HarnessAgent                   │
│    → 不需要查 tool_definition 表                      │
└────────────────────────────────────────────────────┘
```

### 4.5 BuiltinToolCatalog 核心代码

模仿 builder 的 `AgentToolsController.BUILTIN_TOOLS`：

```java
/**
 * 内置工具目录。
 *
 * <p>所有系统内置工具在此集中定义，直接暴露 API 给前端。
 * 不写数据库，完全内存加载。
 *
 * <p>和 builder 的 {@code AgentToolsController.BUILTIN_TOOLS} 等价，
 * 只是拆成独立类方便复用。
 *
 * <p>当 AgentScope 版本升级、新增或修改内置工具时，
 * 只需更新这个类，无需跑数据库迁移。
 */
public class BuiltinToolCatalog {

    /** 工具名 → 类全名映射（运行时反射实例化用） */
    private static final Map<String, String> TOOL_CLASS_MAP = new LinkedHashMap<>();

    /** 前端展示用的工具信息列表 */
    public static final List<BuiltinToolInfo> BUILTIN_TOOLS = List.of(
        // ===== HarnessAgent 标准工具 =====
        tool("read_file",       "读取文件内容",                 "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("write_file",      "写入文件内容",                 "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("edit_file",       "查找替换文件内容",             "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("grep_files",      "在文件中搜索文本",             "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("glob_files",      "按 glob 查找文件",             "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("list_files",      "列出目录内容",                 "filesystem", "io.agentscope.harness.agent.tool.FilesystemTool"),
        tool("shell_execute",   "执行 Shell 命令",              "shell",      "io.agentscope.harness.agent.tool.ShellExecuteTool"),
        tool("memory_search",   "搜索长期记忆",                 "memory",     "io.agentscope.harness.agent.tool.MemorySearchTool"),
        tool("memory_get",      "获取记忆片段",                 "memory",     "io.agentscope.harness.agent.tool.MemoryGetTool"),
        tool("session_search",  "搜索会话历史",                 "memory",     "io.agentscope.harness.agent.tool.SessionSearchTool"),
        tool("agent_spawn",     "创建子代理",                   "subagent",   "io.agentscope.harness.agent.tool.AgentSpawnTool"),
        tool("agent_send",      "发送消息给子代理",             "subagent",   "io.agentscope.harness.agent.tool.AgentSpawnTool"),
        tool("agent_list",      "列出子代理",                   "subagent",   "io.agentscope.harness.agent.tool.AgentSpawnTool"),
        tool("agent_generate",  "自动生成子代理配置",           "subagent",   "io.agentscope.harness.agent.tool.AgentGenerateTool"),
        tool("task_output",     "获取任务输出",                 "task",       "io.agentscope.harness.agent.tool.TaskTool"),
        tool("task_cancel",     "取消任务",                     "task",       "io.agentscope.harness.agent.tool.TaskTool"),
        tool("task_list",       "列出任务",                     "task",       "io.agentscope.harness.agent.tool.TaskTool"),
        tool("propose_skill",   "向上司推荐技能",               "skill",      "io.agentscope.harness.agent.tool.ProposeSkillTool"),
        tool("skill_manage",    "管理技能",                     "skill",      "io.agentscope.harness.agent.tool.SkillManageTool"),
        tool("plan_enter",      "进入计划模式",                 "plan",       "io.agentscope.harness.agent.middleware.PlanModeTools$PlanEnterTool"),
        tool("plan_write",      "编写计划",                     "plan",       "io.agentscope.harness.agent.middleware.PlanModeTools$PlanWriteTool"),
        tool("plan_exit",       "退出计划模式",                 "plan",       "io.agentscope.harness.agent.middleware.PlanModeTools$PlanExitTool"),

        // ===== Builder 业务工具 =====
        tool("outbound_send",                "通过 Channel 发送消息到外部平台", "channel", "io.agentscope.builder.runtime.outbound.OutboundTool"),
        tool("sessions_spawn",               "创建子代理会话",                "session",  "io.agentscope.builder.runtime.session.tool.SessionsTool"),
        tool("sessions_send",                "发送消息到子代理会话",          "session",  "io.agentscope.builder.runtime.session.tool.SessionsTool"),
        tool("sessions_list",                "列出所有会话",                  "session",  "io.agentscope.builder.runtime.session.tool.SessionsTool"),
        tool("sessions_history",             "获取会话历史",                  "session",  "io.agentscope.builder.runtime.session.tool.SessionsTool"),
        tool("sessions_pending_completions", "列出待完成的子代理",            "session",  "io.agentscope.builder.runtime.session.tool.SessionsTool")
    );

    static {
        for (BuiltinToolInfo t : BUILTIN_TOOLS) {
            TOOL_CLASS_MAP.put(t.name(), t.className());
        }
    }

    /** 返回所有内置工具（给前端 /catalog/builtins API） */
    public static List<BuiltinToolInfo> listAll() {
        return BUILTIN_TOOLS;
    }

    /** 按 name 查找内置工具的类名 */
    public static Optional<String> getClassName(String toolName) {
        return Optional.ofNullable(TOOL_CLASS_MAP.get(toolName));
    }

    /** 判断某个工具名是否为内置工具 */
    public static boolean isBuiltin(String toolName) {
        return TOOL_CLASS_MAP.containsKey(toolName);
    }

    /** 实例化内置工具 */
    public static Object instantiate(String toolName) {
        String className = TOOL_CLASS_MAP.get(toolName);
        if (className == null) {
            throw new IllegalArgumentException("Unknown builtin tool: " + toolName);
        }
        // FilesystemTool 有多个 @Tool 方法（read_file, write_file 等），
        // 但实例化一次即可，AgentScope 的 @Tool 方法分发是按方法名匹配的
        Class<?> clazz = Class.forName(className);
        return clazz.getDeclaredConstructor().newInstance();
    }

    /** 内置工具信息（同 builder 的 BuiltinToolInfo record） */
    public record BuiltinToolInfo(
            String name,          // 工具名
            String description,   // 描述
            String group,         // 分组（filesystem/shell/memory/subagent/task/skill/plan/channel/session）
            String className      // Java 类全名（用于反射实例化）
    ) {}

    private static BuiltinToolInfo tool(String name, String desc, String group, String cls) {
        return new BuiltinToolInfo(name, desc, group, cls);
    }
}
```

### 4.6 内置工具相关 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tools/catalog/builtins` | **返回所有系统内置工具列表**（从 BuiltinToolCatalog 内存加载，不查数据库） |
| GET | `/api/agents/{id}/tools/active` | **返回 Agent 运行时实际拥有的工具**（introspect HarnessAgent.Toolkit） |
| GET | `/api/agents/{id}/tools/catalog/builtins` | （同 builder）某个 Agent 可见的内置工具 |
| GET | `/api/agents/{id}/tools/catalog/mcp-servers` | （同 builder）MCP 服务器模板 |

### 5.1 总体架构

```
┌──────────────────────────────────────────────────────────┐
│                    前端 (Monaco 编辑器)                     │
│  用户写 Groovy 代码 → 语法高亮 / 补全 → 点击「编译」       │
└──────────────┬───────────────────────────────────────────┘
               │ POST /api/tools/:id/compile
               ▼
┌──────────────────────────────────────────────────────────┐
│               GroovyAgentCompiler (新增模块)                │
│                                                           │
│  1. 代码预处理 (CodePreprocessor)                          │
│      - 替换占位符 $apiKey$ $baseUrl$                      │
│      - 插入默认 import                                    │
│                                                           │
│  2. AST 安全分析 (SecurityAstAnalyzer)                     │
│      - 白名单包检查                                       │
│      - 黑名单 API 拦截                                    │
│      - 禁止反射调用                                       │
│                                                           │
│  3. Groovy 编译                                          │
│      - GroovyClassLoader.parseClass()                     │
│      - 捕获编译错误 (含行号)                               │
│                                                           │
│  4. 编译缓存                                              │
│      - 按 (code_md5, agent_id) 缓存 Class 对象            │
│      - 避免重复编译                                       │
└──────────────┬───────────────────────────────────────────┘
               │ 成功? → 存入数据库 status=compiled
               │ 失败? → 存入数据库 status=error + error_msg
               ▼
┌──────────────────────────────────────────────────────────┐
│               Agent 动态装配                               │
│                                                           │
│  1. 从数据库读取 agent 绑定的 tool/middleware              │
│  2. 从缓存取 Class (或重新编译)                           │
│  3. 实例化: toolInstance = clazz.newInstance()             │
│  4. 注入 HarnessAgent.Builder                             │
│      builder.toolkit(toolkit).middleware(mw)               │
│  5. 注册到 Gateway                                       │
└──────────────────────────────────────────────────────────┘
```

### 5.2 用户能写的 Tool 代码示例

```groovy
// Tool.java — 用户在前端编辑

import io.agentscope.core.tool.annotation.Tool;
import io.agentscope.core.tool.annotation.ToolParam;

class CalculatorTool {

    @Tool(name = "calculator", description = "执行数学计算")
    public String calculate(
            @ToolParam(name = "expression", description = "数学表达式, 如 1+2*3")
            String expression
    ) {
        // 注意: ScriptEngine 需要白名单放行
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        javax.script.ScriptEngine engine = manager.getEngineByName("js");
        Object result = engine.eval(expression);
        return "计算结果: " + result.toString();
    }
}
```

### 5.3 用户能写的 Middleware 代码示例

```groovy
// Middleware.java — 用户在前端编辑

import io.agentscope.core.agent.middleware.MiddlewareBase;
import io.agentscope.core.agent.middleware.AgentInput;
import reactor.core.publisher.Mono;

class AuditMiddleware extends MiddlewareBase {

    @Override
    public String hookPoint() {
        return "AgentInput"; // 在 Agent 接收输入前执行
    }

    @Override
    public Mono<AgentInput> process(AgentInput input) {
        String userMsg = input.getMessages().stream()
            .filter(m -> m.getRole() == MsgRole.USER)
            .map(m -> m.getTextContent())
            .collect(Collectors.joining(", "));

        System.out.println("[AUDIT] 用户输入: " + userMsg);
        System.out.println("[AUDIT] Token 数: " + input.getTokenCount());

        // 可以修改输入
        // input.getMessages().add(new SystemMessage("注意: 正在被审计"));

        return Mono.just(input);
    }
}
```

### 5.4 用户能写的 Agent 拼装代码

```groovy
// Agent.java — 用户在 Agent 编辑页写
// 以下变量由编译器注入:
//   model: Model 实例
//   toolkit: Toolkit 实例 (已包含用户选择的工具)
//   middlewares: List<MiddlewareBase> (已按排序装配)
//   session: Session 实例
//   filesystem: FilesystemSpec 实例
//   permCtx: PermissionContextState 实例

HarnessAgent agent = HarnessAgent.builder()
    .name("我的智能体")
    .model(model)
    .toolkit(toolkit)
    .middleware(middlewares)     // 所有用户选的中间件
    .session(session)
    .filesystem(filesystem)
    .permissionContext(permCtx)
    .maxIters(20)
    .build();

return agent;  // 编译引擎期望的返回值
```

### 5.5 编译引擎核心代码

```java
/**
 * 代码编译引擎。所有用户编写的 Groovy 代码统一走这个入口。
 *
 * <p>流程：
 * <ol>
 *   <li>预处理（占位符替换、import 注入）</li>
 *   <li>AST 安全分析（包白名单、黑名单 API）</li>
 *   <li>GroovyClassLoader 编译</li>
 *   <li>编译结果缓存</li>
 * </ol>
 */
@Component
public class GroovyAgentCompiler {

    private static final Logger log = LoggerFactory.getLogger(GroovyAgentCompiler.class);

    /** 每个 agent 独立 ClassLoader，防止类泄漏 */
    private final Map<Long, GroovyClassLoader> agentLoaders = new ConcurrentHashMap<>();
    /** 编译缓存: md5(code) → Class<?> */
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    private final CodePreprocessor preprocessor;
    private final SecurityAstAnalyzer securityAnalyzer;
    private final CompilationCache cache;

    public GroovyAgentCompiler(
            CodePreprocessor preprocessor,
            SecurityAstAnalyzer securityAnalyzer,
            CompilationCache cache) {
        this.preprocessor = preprocessor;
        this.securityAnalyzer = securityAnalyzer;
        this.cache = cache;
    }

    /**
     * 编译 Tool 代码，返回 CompiledResult。
     * 只编译不实例化——实例化在 agent 装配时才做。
     */
    public CompiledResult compileTool(Long agentId, String sourceCode) {
        return compile(agentId, sourceCode, "Tool");
    }

    /**
     * 编译 Middleware 代码
     */
    public CompiledResult compileMiddleware(Long agentId, String sourceCode) {
        return compile(agentId, sourceCode, "Middleware");
    }

    /**
     * 编译 Agent 拼装代码（需要绑定变量）
     */
    public CompiledResult compileAgentAssembly(
            Long agentId,
            String sourceCode,
            Map<String, Object> bindings) {
        return compileWithBindings(agentId, sourceCode, bindings);
    }

    /**
     * 通用编译方法
     */
    private CompiledResult compile(Long agentId, String sourceCode, String kind) {
        try {
            // 1. 安全检查
            SecurityAstAnalyzer.AnalysisResult analysis = securityAnalyzer.analyze(sourceCode);
            if (!analysis.isAllowed()) {
                return CompiledResult.error(
                    "安全分析未通过: " + String.join(", ", analysis.getViolations()));
            }

            // 2. 检查缓存
            String md5 = DigestUtils.md5Hex(sourceCode);
            Class<?> cached = cache.get(agentId, md5);
            if (cached != null) {
                return CompiledResult.success(cached, md5);
            }

            // 3. 预处理
            String processed = preprocessor.preprocess(sourceCode, kind);

            // 4. 编译
            GroovyClassLoader loader = getLoader(agentId);
            Class<?> clazz = loader.parseClass(processed);

            // 5. 写缓存
            cache.put(agentId, md5, clazz);

            log.info("Compiled {} for agent {}: {}", kind, agentId, clazz.getName());
            return CompiledResult.success(clazz, md5);

        } catch (MultipleCompilationErrorsException e) {
            // Groovy 编译错误，提取行号
            List<CompileError> errors = e.getErrorCollector().getErrors().stream()
                .map(this::toCompileError)
                .collect(Collectors.toList());
            return CompiledResult.compileErrors(errors);
        } catch (Exception e) {
            log.error("Unexpected compile error for agent {} {}: {}", agentId, kind, e.getMessage(), e);
            return CompiledResult.error("未知编译错误: " + e.getMessage());
        }
    }

    private GroovyClassLoader getLoader(Long agentId) {
        return agentLoaders.computeIfAbsent(agentId, id -> {
            // 限制: 只允许加载 AgentScope + 白名单包
            GroovyClassLoader loader = new GroovyClassLoader(
                new SandboxClassLoader(AgentScopeClassLoaderGenerator.getClassLoader()));
            return loader;
        });
    }

    private CompileError toCompileError(ErrorMessage msg) {
        return new CompileError(
            msg.getLine(),              // Groovy 行号 (从 0 开始)
            msg.getColumn(),
            msg.getMessage());
    }

    /** 编译结果 */
    public static class CompiledResult {
        private final boolean success;
        private final Class<?> clazz;
        private final String cacheKey;
        private final List<CompileError> errors;
        private final String generalError;

        // 工厂方法
        public static CompiledResult success(Class<?> clazz, String cacheKey) { ... }
        public static CompiledResult compileErrors(List<CompileError> errors) { ... }
        public static CompiledResult error(String msg) { ... }

        public boolean isSuccess() { return success; }
        public Class<?> getClazz() { return clazz; }
        public List<CompileError> getErrors() { return errors; }
    }

    public static class CompileError {
        private final int line;      // 1-based 行号
        private final int column;
        private final String message;

        // 构造函数 + getter
    }
}
```

### 5.6 安全 AST 分析器

```java
/**
 * Groovy AST 安全分析。
 *
 * <p>限制用户代码可以调用的 API，防止恶意代码执行。
 *
 * <h3>白名单包（允许使用）</h3>
 * <ul>
 *   <li>io.agentscope.* — AgentScope 框架</li>
 *   <li>java.util.* — 集合工具</li>
 *   <li>java.time.* — 时间</li>
 *   <li>java.math.* — 数学</li>
 *   <li>java.lang.* — 语言基础</li>
 *   <li>com.fasterxml.jackson.* — JSON 处理</li>
 * </ul>
 *
 * <h3>黑名单（禁止调用）</h3>
 * <ul>
 *   <li>java.lang.Runtime.exec / ProcessBuilder — 系统命令</li>
 *   <li>java.lang.ClassLoader — 类加载</li>
 *   <li>java.lang.reflect.* — 反射（可绕过白名单）</li>
 *   <li>java.io.FileOutputStream / FileInputStream — 直接文件操作</li>
 *   <li>java.net.Socket / URL.openConnection — 网络请求</li>
 *   <li>java.sql.* — 数据库操作</li>
 * </ul>
 */
public class SecurityAstAnalyzer {

    private static final Set<String> WHITELIST_PACKAGES = Set.of(
        "io.agentscope", "java.util", "java.time",
        "java.math", "java.lang", "com.fasterxml.jackson");

    private static final Set<String> BLACKLIST_CLASSES = Set.of(
        "java.lang.Runtime", "java.lang.ProcessBuilder",
        "java.lang.ClassLoader", "java.lang.reflect",
        "java.io.FileOutputStream", "java.io.FileInputStream",
        "java.net.Socket", "java.sql.DriverManager");

    /**
     * 分析 AST，返回分析结果。
     * 使用 Groovy 的 AST 遍历机制（ClassCodeVisitorSupport）。
     */
    public AnalysisResult analyze(String sourceCode) {
        List<String> violations = new ArrayList<>();

        try {
            // 将源码解析为 AST
            GroovyCodeSource source = new GroovyCodeSource(sourceCode, "analysis", "/tmp");
            CompilationUnit unit = new AstBuilder()
                .buildFromString(source.getName(), source.getScriptSource());

            // 遍历 AST 节点
            unit.visit(new GroovyClassVisitor() {
                @Override
                public void visitMethodCallExpression(MethodCallExpression call) {
                    String className = call.getReceiver().getText();
                    String methodName = call.getMethodAsString();

                    // 检查反射调用
                    if ("forName".equals(methodName) && "Class".equals(className)) {
                        violations.add("禁止使用 Class.forName() 反射加载类");
                    }
                    if ("exec".equals(methodName) && "Runtime".equals(className)) {
                        violations.add("禁止调用 Runtime.exec()");
                    }

                    // 检查方法接收器的包名
                    checkType(violations, call.getReceiver().getType());
                }

                @Override
                public void visitVariableExpression(VariableExpression expression) {
                    checkType(violations, expression.getType());
                }

                // ... 其他 visitor 方法
            });
        } catch (Exception e) {
            violations.add("AST 分析异常: " + e.getMessage());
        }

        return new AnalysisResult(violations.isEmpty(), violations);
    }

    private void checkType(List<String> violations, ClassNode type) {
        if (type == null) return;
        String name = type.getName();
        for (String black : BLACKLIST_CLASSES) {
            if (name.startsWith(black)) {
                violations.add("禁止使用黑名单类: " + name);
            }
        }
    }

    public static class AnalysisResult {
        private final boolean allowed;
        private final List<String> violations;

        public AnalysisResult(boolean allowed, List<String> violations) {
            this.allowed = allowed;
            this.violations = violations;
        }

        public boolean isAllowed() { return allowed; }
        public List<String> getViolations() { return violations; }
    }
}
```

### 5.7 代码预处理器

```java
public class CodePreprocessor {

    /**
     * 预处理用户代码：
     * 1. 注入默认 import（让用户不用手写 import）
     * 2. 替换占位符
     * 3. 包装为完整 Groovy 类
     */
    public String preprocess(String sourceCode, String kind) {
        StringBuilder sb = new StringBuilder();

        // 自动注入常用 import
        sb.append("""
            import io.agentscope.*
            import io.agentscope.core.*
            import io.agentscope.core.tool.annotation.*
            import io.agentscope.core.agent.middleware.*
            import java.util.*
            import java.util.stream.*
            import com.fasterxml.jackson.databind.*
            """);

        sb.append("\n");
        sb.append(sourceCode);

        return sb.toString();
    }

    /**
     * 在 Agent 拼装代码编译前，替换绑定变量引用。
     * 例如用户代码中写了 ${model}，替换为实际实例引用。
     */
    public String replaceBindings(String code, Map<String, Object> bindings) {
        String result = code;
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            // 不能直接 Object.toString()——这里是示意
            // 实际需要根据变量类型做合适替换
            result = result.replace("${" + entry.getKey() + "}", entry.getValue().toString());
        }
        return result;
    }
}
```

---

## 六、模块结构

```
agentscope-builder/                    # 现有项目名，后面如果想独立可改名
├── pom.xml
├── src/main/
│   ├── java/io/agentscope/builder/
│   │   ├── BuilderApp.java            # 入口（不改）
│   │   │
│   │   ├── runtime/                   # 复用 builder（不改）
│   │   │   └── ... (BuilderBootstrap, channel, config, gateway, session, marketplace)
│   │   │
│   │   ├── web/                       # 复用 + 扩展
│   │   │   ├── api/                   # 复用
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── AgentCatalogController.java (扩展: 新增 tool/middleware 字段)
│   │   │   │   └── ...
│   │   │   ├── compiler/              # ★★★ 新增模块 ★★★
│   │   │   │   ├── GroovyAgentCompiler.java      # 编译引擎
│   │   │   │   ├── CodePreprocessor.java          # 代码预处理
│   │   │   │   ├── SecurityAstAnalyzer.java       # AST 安全分析
│   │   │   │   ├── SandboxClassLoader.java        # 沙箱 ClassLoader
│   │   │   │   ├── CompilationCache.java          # 编译缓存
│   │   │   │   └── AgentAssembler.java            # Agent 动态装配
│   │   │   ├── controller/            # ★★★ 新增 REST 控制器 ★★★
│   │   │   │   ├── ToolDefinitionController.java      # 工具 CRUD + 编译
│   │   │   │   ├── MiddlewareDefinitionController.java # 中间件 CRUD + 编译
│   │   │   │   └── AgentCodeController.java           # Agent 拼装代码管理
│   │   │   ├── model/                 # ★★★ 实体 ★★★
│   │   │   │   ├── ToolDefinition.java
│   │   │   │   └── MiddlewareDefinition.java
│   │   │   ├── repository/            # ★★★ JPA Repository ★★★
│   │   │   │   ├── ToolDefinitionRepository.java
│   │   │   │   ├── MiddlewareDefinitionRepository.java
│   │   │   │   ├── AgentToolBindingRepository.java
│   │   │   │   └── AgentMiddlewareBindingRepository.java
│   │   │   └── service/               # ★★★ 业务逻辑 ★★★
│   │   │       ├── BuiltinToolRegistry.java   # ★ 内置工具自动发现注册
│   │   │       ├── ToolService.java
│   │   │       ├── MiddlewareService.java
│   │   │       └── DynamicAgentService.java  # Agent 动态装配主要逻辑
│   │   │
│   │   └── ...
│   │
│   └── resources/
│       └── compiler/                  # 编译配置
│           ├── whitelist-packages.txt  # AST 分析白名单包
│           └── blacklist-classes.txt   # AST 分析黑名单类
│
├── frontend/                          # 前端
│   └── src/
│       ├── api/                       # 新增 API 客户端
│       │   ├── tools.ts                 # Tool CRUD API
│       │   └── middlewares.ts           # Middleware CRUD API
│       ├── components/                # 新增组件
│       │   ├── CodeEditorPanel.tsx      # 代码编辑器面板（Monaco）
│       │   ├── ToolEditor.tsx           # 工具编辑器
│       │   ├── MiddlewareEditor.tsx     # 中间件编辑器
│       │   ├── ToolSelector.tsx         # 工具选择器（分「系统内置」「我创建的」两栏）
│       │   ├── BuiltinToolBadge.tsx     # 内置工具标识
│       │   ├── MiddlewareList.tsx        # 中间件排序列表
│       │   ├── CompileResultBadge.tsx   # 编译状态标签
│       │   └── CompileErrorPanel.tsx    # 编译错误面板
│       ├── pages/                     # 新增页面
│       │   ├── ToolsPage.tsx            # 工具管理页
│       │   ├── ToolCreatePage.tsx       # 创建工具页
│       │   ├── ToolEditPage.tsx         # 编辑工具页
│       │   ├── MiddlewaresPage.tsx       # 中间件管理页
│       │   └── AgentCodePage.tsx        # Agent 拼装代码页
│       └── ...
```

---

## 七、API 设计

### 7.1 Tool 管理 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tools` | 工具列表（返回 **系统内置** + **用户自建** 两类，支持 `?source=builtin/user` 过滤） |
| GET | `/api/tools/builtin` | 仅列出系统内置工具（快捷入口） |
| POST | `/api/tools` | 创建用户工具（source 自动设为 `user`） |
| GET | `/api/tools/{id}` | 工具详情（含源码或 className） |
| PUT | `/api/tools/{id}` | 更新工具代码（仅 `source=user` 可编辑） |
| DELETE | `/api/tools/{id}` | 删除工具（仅 `source=user` 可删除） |
| **POST** | **`/api/tools/{id}/compile`** | **编译用户工具（仅 `source=user`）** |
| GET | `/api/tools/{id}/compile-logs` | 编译历史 |

#### API 返回示例（工具列表）

```json
[
  {
    "id": 1,
    "name": "read_file",
    "description": "读取文件内容",
    "source": "builtin",
    "className": "io.agentscope.harness.tool.ReadFileTool",
    "status": "ready",
    "shared": true
  },
  {
    "id": 2,
    "name": "calculator",
    "description": "执行数学计算",
    "source": "user",
    "code": "class CalculatorTool { ... }",
    "status": "compiled",
    "shared": false,
    "createdBy": "alice"
  }
]
```

### 7.2 Middleware 管理 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/middlewares` | 中间件列表 |
| POST | `/api/middlewares` | 创建中间件 |
| GET | `/api/middlewares/{id}` | 详情 |
| PUT | `/api/middlewares/{id}` | 更新 |
| DELETE | `/api/middlewares/{id}` | 删除 |
| **POST** | **`/api/middlewares/{id}/compile`** | **编译中间件** |

### 7.3 Agent 管理 API（扩展）

| Method | Path | 说明 |
|--------|------|------|
| PUT | `/api/agents/{id}/tools` | 设置 agent 绑定的工具列表 |
| PUT | `/api/agents/{id}/middlewares` | 设置 agent 绑定的中间件列表（含排序） |
| PUT | `/api/agents/{id}/code` | 设置 agent 拼装代码 |
| GET | `/api/agents/{id}/code` | 获取 agent 拼装代码 |
| **POST** | **`/api/agents/{id}/assemble`** | **编译拼装代码 + 构建运行** |

### 7.4 编译接口规范

```json
// POST /api/tools/{id}/compile 请求
// 不需要 body（读取数据库中已保存的代码）

// 成功响应 (200)
{
  "status": "compiled",
  "className": "io.agentscope.user.MyTool",
  "cacheKey": "a1b2c3d4e5f6...",
  "compiledAt": "2026-06-06T10:30:00"
}

// 编译错误响应 (422)
{
  "status": "error",
  "errors": [
    {
      "line": 12,
      "column": 5,
      "message": "无法解析符号 'DashScopeChatModel'",
      "suggestion": "请添加 import: import io.agentscope.core.model.DashScopeChatModel"
    }
  ]
}

// 安全分析失败响应 (422)
{
  "status": "error",
  "errors": [
    {
      "line": 8,
      "column": 15,
      "message": "安全分析未通过",
      "suggestion": "禁止调用 java.lang.Runtime.exec()，请使用 AgentScope 提供的文件操作 API"
    }
  ]
}
```

### 7.5 Agent 装配接口规范

```json
// POST /api/agents/{id}/assemble 请求
{
  "modelProvider": "dashscope",
  "modelName": "qwen-max"
  // 工具和中间件从关联表读取，不用传
}

// 成功响应 (200)
{
  "status": "running",
  "agentId": 42,
  "gatewayId": "uca-user123-42",
  "tools": ["calculator", "file_reader"],
  "middlewares": ["audit_logger", "rate_limiter"],
  "assembledAt": "2026-06-06T10:30:00"
}

// 装配失败响应 (500)
{
  "status": "error",
  "phase": "tool_instantiation",
  "toolName": "calculator",
  "message": "实例化 CalculatorTool 失败: 缺少无参构造函数"
}
```

---

## 八、前端改动

### 8.1 新增页面

#### 工具管理入口
```
/agents → Agent 列表页
  ├── /agents/:id/edit → Agent 编辑页 (已有)
  │     ├── 选项卡：基本信息 / 模型配置
  │     ├── 选项卡：工具选择 ← 新增
  │     │     ├── 标签页：系统内置  |  我创建的
  │     │     ├── 已选工具列表（可删除、排序）
  │     │     └── 「+ 添加工具」→ 弹出工具选择器
  │     ├── 选项卡：中间件 ← 新增
  │     │     ├── 已选中间件列表（拖拽排序）
  │     │     └── 「+ 添加中间件」→ 弹出选择器
  │     └── 选项卡：拼装代码 ← 新增
  │           └── Monaco 编辑器 (Groovy)
  │
  └── /tools → 工具管理 (新增)
        ├── 工具列表（分标签：系统内置 | 我创建的）
        │   ├── 系统内置 — 只读列表，来自 AgentScope 框架
        │   │   ├── read_file, write_file, shell_execute, ...
        │   │   └── 状态总是 ready，无需编译
        │   └── 我创建的 — 用户自建 Groovy 工具
        │       ├── calculator, web_search, ...
        │       └── 状态: draft / compiled / error
        ├── 「+ 创建工具」→ ToolCreatePage
        └── 点击工具 → 编辑页
              ├── Monaco 编辑器 (Groovy)（仅用户自建）
              ├── 「编译」按钮（仅用户自建）
              └── 编译结果展示

      /middlewares → 中间件管理 (新增)
        └── 同上类似结构
```

#### 工具编辑页布局
```
┌──────────────────────────────────────────────────────────────┐
│  工具编辑  ← 返回工具列表                                      │
├──────────────────────────────────────────────────────────────┤
│  基本信息                                                      │
│  名称: [calculator       ]  描述: [执行数学计算          ]    │
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Tool.java — Groovy (Monaco 编辑器)                       │ │
│  │                                                           │ │
│  │  import io.agentscope.core.tool.annotation.Tool           │ │
│  │  import io.agentscope.core.tool.annotation.ToolParam      │ │
│  │                                                           │ │
│  │  class CalculatorTool {                                   │ │
│  │      @Tool(name="calculator", desc="数学计算")             │ │
│  │      public String calc(                                  │ │
│  │          @ToolParam(name="expr") String expr               │ │
│  │      ) {                                                   │ │
│  │          // 你的实现                                       │ │
│  │      }                                                     │ │
│  │  }                                                         │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│  编译状态: [◉ 编译成功]  [× 行 12: 缺少分号]    [⚡ 编译]    │
│                                                               │
│  共享设置: [☑ 共享给所有 Agent]                                │
│                                                               │
│  ┌────────────┐  ┌────────────┐                               │
│  │  💾 保存   │  │  ⚡ 编译   │                               │
│  └────────────┘  └────────────┘                               │
└──────────────────────────────────────────────────────────────┘
```

#### 工具选择器（给 Agent 选工具时的弹窗）

```
┌──────────────────────────────────────────────────────────────┐
│  [+ 添加工具]  ← 点击后弹出选择器                              │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  选择工具                                                     │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  📦 系统内置工具  (点击直接添加，无需编译)                       │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ☐  read_file       读取文件内容              [系统]     │ │
│  │  ☐  write_file      写入文件内容              [系统]     │ │
│  │  ☑  shell_execute   执行 Shell 命令           [系统]     │ │
│  │  ☐  memory_search   搜索长期记忆              [系统]     │ │
│  │  ☐  knowledge_retr  知识库检索                [系统]     │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│  🛠 我创建的工具  (编译通过后可用)                              │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  ☑  calculator     执行数学计算            [◉ 编译成功]  │ │
│  │  ☐  web_search     搜索网页                [× 编译失败]  │ │
│  │  ☐  image_gen      生成图片                [◉ 编译成功]  │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│                     [  取消  ]    [  确定  ]                   │
└──────────────────────────────────────────────────────────────┘
```

#### 工具列表页（/tools 路由）
```
┌──────────────────────────────────────────────────────────────┐
│  工具管理                                              [+ 新建]│
├──────────────────────────────────────────────────────────────┤
│  ┌──────────┬────────────────────────────────────────┐       │
│  │  系统内置 │  我创建的                              │       │
│  ├──────────┴────────────────────────────────────────┤       │
│  │  只读列表，来自 AgentScope 框架                      │       │
│  │                                                    │       │
│  │  名称          描述                类名                 │       │
│  │  ─────────────────────────────────────────────────      │       │
│  │  read_file     读取文件       ReadFileTool             │       │
│  │  write_file    写入文件       WriteFileTool            │       │
│  │  shell_execute 执行命令       ShellCommandTool         │       │
│  │  subagent      创建子代理     SubAgentTool             │       │
│  └────────────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────┘
```
```
┌──────────────────────────────────────────────────────────────┐
│  × 编译错误                                                    │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  第 12 行: 找不到符号 'DashScopeChatModel'                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ 建议: 请添加 import                                     │ │
│  │ import io.agentscope.core.model.DashScopeChatModel       │ │
│  │                                              [📋 复制]  │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│  第 25 行: 方法 getResult() 未定义                             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ 你可能想调用 getTextContent()                            │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 8.2 新增组件

| 组件 | 用途 |
|------|------|
| `CodeEditorPanel.tsx` | Monaco 编辑器封装（Groovy 语法高亮 + 补全） |
| `ToolEditor.tsx` | 工具编辑页 |
| `MiddlewareEditor.tsx` | 中间件编辑页 |
| `ToolSelector.tsx` | 选择已编译工具的 dialog |
| `MiddlewareList.tsx` | 可排序的中间件列表 |
| `CompileResultBadge.tsx` | 编译状态徽章（draft/compiled/error） |
| `CompileErrorPanel.tsx` | 编译错误面板（含建议） |

---

## 九、运行时装配流程

### 9.1 Agent 动态装配序列图

```
用户点击「启动」/ 发送第一条消息
    │
    ▼
AgentCatalogService.getOrInstantiateRunningAgent()
    │
    ├── [缓存命中] → 返回已运行的 HarnessAgent
    │
    └── [缓存未命中]
         │
         ▼
        DynamicAgentService.assemble(agentId)
            │
            ├── 1. 从数据库读取 AgentDefinition
            │     - provider_id, model_name
            │     - assembled_code (Agent 拼装代码)
            │     - 绑定的 tool_ids, middleware_ids（含排序）
            │
            ├── 2. 读取绑定的 Tools
            │     for each tool_id:
            │       ├── 读取 tool_definition
            │       ├── [source=builtin]
            │       │    ├── 直接从 className 反射实例化
            │       │    └── 无需编译，无需缓存
            │       ├── [source=user]
            │       │    ├── 从 CompilationCache 取 Class
            │       │    ├── [缓存未命中] → GroovyAgentCompiler.compileTool() → 重新编译
            │       │    └── clazz.getDeclaredConstructor().newInstance()
            │       └── → 加入 Toolkit
            │
            ├── 3. 读取绑定的 Middlewares
            │     for each middleware_id:
            │       ├── 从缓存取 Class
            │       ├── [缓存未命中] → GroovyAgentCompiler.compileMiddleware()
            │       └── newInstance() → 加入 List<MiddlewareBase>
            │
            ├── 4. 构建 Model 实例
            │     ModelProvider → provider.apiKey
            │     DashScopeChatModel.builder()
            │         .apiKey(decrypt(provider.apiKey))
            │         .modelName(def.modelName)
            │         .build()
            │
            ├── 5. 编译 Agent 拼装代码
            │     Map<String,Object> bindings = {
            │         model, toolkit, middlewares,
            │         session, filesystem, permCtx
            │     }
            │     GroovyAgentCompiler.compileAgentAssembly(code, bindings)
            │     → 返回 HarnessAgent 实例
            │
            ├── 6. 注册到 Gateway
            │     gateway.registerAgent(gatewayId, agent)
            │
            └── 7. 返回 HarnessAgent
```

### 9.2 DynamicAgentService 核心代码

```java
@Service
public class DynamicAgentService {

    @Autowired private GroovyAgentCompiler compiler;
    @Autowired private BuiltinToolRegistry builtinRegistry;  // ★ 内置工具注册表
    @Autowired private AgentDefinitionRepository defRepo;
    @Autowired private ToolDefinitionRepository toolRepo;
    @Autowired private MiddlewareDefinitionRepository mwRepo;
    @Autowired private AgentToolBindingRepository toolBindingRepo;
    @Autowired private AgentMiddlewareBindingRepository mwBindingRepo;
    @Autowired private ModelProviderRepository providerRepo;
    @Autowired private PermissionRuleRepository ruleRepo;
    @Autowired private BuilderBootstrap bootstrap;

    /** 编译缓存 */
    private final Map<Long, HarnessAgent> runningAgents = new ConcurrentHashMap<>();

    public HarnessAgent assemble(Long agentId, String userId) {
        AgentDefinition def = defRepo.findById(agentId)
            .orElseThrow(() -> new AgentNotFoundException(agentId));
        ModelProvider provider = providerRepo.findById(def.getProviderId())
            .orElseThrow(() -> new ProviderNotFoundException(def.getProviderId()));

        // 1. 构建 Model
        Model model = buildModel(provider);

        // 2. 装配 Tools
        Toolkit toolkit = new Toolkit();
        List<AgentToolBinding> toolBindings = toolBindingRepo
            .findByAgentIdOrderBySortOrder(agentId);
        for (AgentToolBinding tb : toolBindings) {
            ToolDefinition toolDef = toolRepo.findById(tb.getToolId())
                .orElseThrow(() -> new ToolNotFoundException(tb.getToolId()));

            Object instance;
            if (toolDef.isBuiltin()) {
                // ★ 内置工具：从 BuiltinToolRegistry 反射实例化，无需编译
                instance = builtinRegistry.instantiate(toolDef.getName());
            } else {
                // 用户自建工具：需要检查编译状态，必要时重新编译
                if (!"compiled".equals(toolDef.getStatus())) {
                    CompiledResult result = compiler.compileTool(agentId, toolDef.getCode());
                    if (!result.isSuccess()) {
                        throw new CompilationException("工具 " + toolDef.getName()
                            + " 编译失败: " + result.getErrors().get(0).getMessage());
                    }
                }
                instance = instantiate(toolDef);
            }
            toolkit.registerTool(instance);
        }

        // 3. 装配 Middlewares
        List<MiddlewareBase> middlewares = new ArrayList<>();
        List<AgentMiddlewareBinding> mwBindings = mwBindingRepo
            .findByAgentIdOrderBySortOrder(agentId);
        for (AgentMiddlewareBinding mb : mwBindings) {
            MiddlewareDefinition mwDef = mwRepo.findById(mb.getMiddlewareId())
                .orElseThrow(() -> new MiddlewareNotFoundException(mb.getMiddlewareId()));
            if (!"compiled".equals(mwDef.getStatus())) {
                // 自动重新编译
            }
            MiddlewareBase mw = (MiddlewareBase) instantiate(mwDef);
            middlewares.add(mw);
        }

        // 4. 编译 Agent 拼装代码
        Map<String, Object> bindings = Map.of(
            "model", model,
            "toolkit", toolkit,
            "middlewares", middlewares,
            "session", bootstrap.gateway().sessionAgentManager(),
            "permCtx", buildPermissionContext(agentId));

        CompiledResult result = compiler.compileAgentAssembly(
            agentId, def.getAssembledCode(), bindings);
        if (!result.isSuccess()) {
            throw new CompilationException("Agent 拼装代码编译失败");
        }

        // 执行代码拿到 HarnessAgent
        // （Groovy 编译后 eval 得到 agent 实例）
        HarnessAgent agent = evalAgent(result.getClazz(), bindings);

        // 5. 注册到 Gateway
        String gatewayId = "dyn-" + agentId;
        bootstrap.gateway().registerAgent(gatewayId, agent);

        runningAgents.put(agentId, agent);

        log.info("Assembled agent {}: tools={}, middlewares={}",
            agentId,
            toolBindings.size(),
            mwBindings.size());

        return agent;
    }

    private Model buildModel(ModelProvider provider) {
        // 根据 provider.name 创建对应 Model 实现
        return switch (provider.getName()) {
            case "dashscope" -> DashScopeChatModel.builder()
                .apiKey(decrypt(provider.getApiKey()))
                .modelName(provider.getDefaultModel())
                .stream(true)
                .build();
            case "openai" -> OpenAIChatModel.builder()
                .apiKey(decrypt(provider.getApiKey()))
                .modelName(provider.getDefaultModel())
                .build();
            // ...
        };
    }

    private Object instantiate(ToolDefinition def)
            throws ReflectiveOperationException {
        Class<?> clazz = compiler.getCachedClass(def.getId());
        if (clazz == null) {
            // 从缓存回退：重新编译
            CompiledResult result = compiler.compileTool(
                def.getAgentId(), def.getCode());
            if (!result.isSuccess()) {
                throw new CompilationException(def.getName() + " 编译失败");
            }
            clazz = result.getClazz();
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private PermissionContextState buildPermissionContext(Long agentId) {
        // 同原计划中的 PermissionRuleBuilder
    }
}
```

---

## 十、安全设计

### 10.1 三层安全防护

```
┌──────────────────────────────────────────────────────┐
│  Layer 1: 编译期 AST 分析                              │
│  ├── 白名单包检查（只允许 io.agentscope.*, java.util.*）│
│  ├── 黑名单 API 拦截（Runtime.exec, ClassLoader 等）   │
│  └── 反射调用拦截（forName, getMethod, invoke）        │
├──────────────────────────────────────────────────────┤
│  Layer 2: 沙箱 ClassLoader                             │
│  ├── 每个 Agent 独立 GroovyClassLoader                 │
│  ├── 停止后 GC 卸载，防类泄漏                          │
│  └── 限制可加载的包                                    │
├──────────────────────────────────────────────────────┤
│  Layer 3: 运行时执行隔离（可选）                        │
│  ├── 利用 Harness 的 SandboxLifecycleMiddleware        │
│  ├── Tool 执行在 Docker 容器内                         │
│  └── 即使 AST 分析有漏网之鱼，也影响不了宿主机          │
└──────────────────────────────────────────────────────┘
```

### 10.2 API Key 安全

```java
// 同原计划：AES 加密存储 + 编译时替换
// 前端代码中写 $apiKey$ 占位符
// 编译时 CodePreprocessor 替换为解密后的值
```

### 10.3 ClassLoader 隔离

```java
/**
 * 每个 Agent 独立 GroovyClassLoader。
 * agent 停止后 loader 无引用 → GC 卸载 → 防类泄漏。
 *
 * SandboxClassLoader 包装一层，拦截黑名单类的加载请求。
 */
public class SandboxClassLoader extends ClassLoader {
    private static final Set<String> BLOCKED = Set.of(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder"
    );

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (BLOCKED.contains(name)) {
            throw new SecurityException("禁止加载: " + name);
        }
        return super.loadClass(name);
    }
}
```

---

## 十一、开发阶段规划

### Phase 1: 数据层 + 实体（2-3天）

| 任务 | 产出 |
|------|------|
| 建表 SQL (tool_definition, middleware_definition, agent_tool_binding, agent_middleware_binding) | DDL（含 source 字段） |
| JPA Entity + Repository | 数据库访问层 |
| 扩展 AgentDefinition 实体（新增拼装代码字段） | 扩展实体 |
| **内置工具 seed SQL**（预置 read_file, write_file 等常见内置工具） | 基础工具集 |
| 初始化数据迁移脚本 | 数据库初始化 |

### Phase 1.5: 内置工具发现（1-2天）

| 任务 | 产出 |
|------|------|
| BuiltinToolRegistry（启动时扫描 ToolRegistry/ToolGroupManager） | 内置工具自动注册 |
| 从 className 反射实例化工具 | 内置工具实例化 |
| 与 builder 原有 ToolRegistry 对接 | 框架集成 |
| 测试：不同 AgentScope 版本下工具发现 | 测试用例 |

### Phase 2: 编译引擎（4-5天）

| 任务 | 产出 |
|------|------|
| GroovyAgentCompiler 核心（编译 + 缓存） | 编译引擎 |
| CodePreprocessor（import 注入 + 占位符替换） | 预处理 |
| SecurityAstAnalyzer（AST 遍历 + 白名单/黑名单） | 安全分析 |
| SandboxClassLoader（类加载隔离） | 沙箱加载器 |
| CompilationCache（按 md5 + agentId 缓存） | 编译缓存 |
| 测试：编译成功/失败/安全检查拦截 | 测试用例 |

### Phase 3: REST API（2-3天）

| 任务 | 产出 |
|------|------|
| ToolDefinitionController（CRUD + 编译） | Tool API |
| MiddlewareDefinitionController（CRUD + 编译） | Middleware API |
| AgentCodeController（拼装代码管理） | Agent 拼装 API |
| DynamicAgentService（agent 装配逻辑） | 装配服务 |
| 前端 API 客户端 (tools.ts, middlewares.ts) | API 调用 |

### Phase 4: Agent 动态装配集成（3-4天）

| 任务 | 产出 |
|------|------|
| 扩展 HarnessAgent.Builder 的调用方式 | 动态注入 |
| ModelProvider → Model 实例工厂 | 模型工厂 |
| 扩展 AgentCatalogService（新增 tool/middleware 字段） | 服务扩展 |
| 用户自定义 Agent 的「创建→选择工具→选择中间件→写拼装代码→启动」流程 | 完整链路 |

### Phase 5: 前端（5-7天）

| 任务 | 产出 |
|------|------|
| CodeEditorPanel（Monaco 编辑器封装） | 编辑器组件 |
| ToolEditor + ToolCreatePage | 工具编辑器页面 |
| MiddlewareEditor + MiddlewaresPage | 中间件页面 |
| ToolSelector + MiddlewareList | 选择/排序组件 |
| CompileResultBadge + CompileErrorPanel | 编译结果展示 |
| Agent编辑页增加「工具/中间件/拼装代码」选项卡 | 功能集成 |

### Phase 6: 打磨（3-5天）

| 任务 | 产出 |
|------|------|
| 编译错误信息友好化（行号映射 + 建议文案） | 用户体验 |
| 完善安全分析（覆盖更多攻击向量） | 安全加固 |
| 工具/Middleware 版本管理（可选） | 版本管理 |
| 示例工具代码模板 | 模板 |
| AI 辅助生成工具代码（可选） | 智能提示 |

---

## 十二、关键风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| Groovy 编译性能 | 低 | 中 | CompilationCache 缓存 + 只重新编译变更的代码 |
| 用户恶意代码 | 中 | **高** | 三层防护（AST + ClassLoader + 沙箱） |
| 用户代码导致 Agent 崩溃 | 中 | 中 | try-catch 包装工具执行 + 独立 GroovyClassLoader 隔离 |
| Groovy 与 Java 21 兼容性 | 低 | 中 | 先行原型验证，打桩测试常用语法 |
| 编译错误行号与用户代码行号不对齐 | 低 | 低 | import 注入不会改变用户代码行号，除非搞错 |
| 已有 builder 版本升级导致代码冲突 | 中 | 中 | 尽量保持扩展点独立，不做 deep 侵入式修改 |
| 运行时刷新工具定义 | 低 | 中 | 可以设计「重新装配」接口，中间件链更新不需要重启 JVM |
| AgentScope 版本升级导致内置工具类名/构造函数变化 | 低 | 中 | BuiltinToolRegistry 在启动时重新扫描，自动更新；使用无参构造函数或 Builder 模式实例化 |

---

## 十三、与 builder 的代码集成策略

### 文件级集成

```
agentscope-builder/src/main/java/io/agentscope/builder/
├── web/
│   ├── compiler/     ← 新增目录（不需要改 builder 已有代码）
│   ├── controller/   ← 新增目录
│   ├── model/        ← 新增目录
│   ├── repository/   ← 新增目录
│   └── service/      ← 新增目录
│
│   # 需要修改的现有文件：
│   ├── catalog/AgentCatalogService.java      # 新增 resolveGatewayAgentId 重载
│   ├── catalog/AgentDefinition.java          # 新增 toolIds/middlewareIds 字段
│   └── config/BuilderConfig.java             # 可选：添加 GroovyAgentCompiler Bean
```

### 数据流

```
应用启动 → BuiltinToolRegistry.scanAndRegister()
         → 扫描 AgentScope 框架中的 ToolRegistry/ToolGroupManager
         → 以 source=builtin 同步到 tool_definition 表
         → 用户可在前端直接勾选使用（无需编译）

用户创建 Tool → 以 source=user 存 tool_definition 表（新增表，不影响 builder 原有表）
用户创建 Middleware → 存 middleware_definition 表（新增表）
用户创建 Agent → 用 builder 原有的 agent_definition 流程
用户绑定工具 → 存 agent_tool_binding 表（新增关联表，支持 builtin + user 两类）
用户启动 → DynamicAgentService 读取所有新增表 + builder 原有表
         → source=builtin: 反射 className 直接实例化
         → source=user: GroovyAgentCompiler 编译 → 实例化
         → 装配 HarnessAgent → 注册 Gateway
运行时对话 → 完全复用 builder 的 ChatController + Gateway
```

**核心原则**：新增表不与 builder 原有表冲突，新增代码放在新增目录，少量修改 builder 文件保持向后兼容。

---

## 附：与原生 agentscope-builder 的区别总结

```
┌─────────────────────────────────────────────────────────────┐
│                     agentscope-builder（现有）                 │
│                                                              │
│  用户 → Web UI → 写 Markdown/JSON → AgentScope 运行时         │
│  不需要会编程，不需要理解 AgentScope 内部                         │
│  工具：框架预制，用户只能选 allow/deny                            │
│  Middleware：框架硬编码，用户不可见                                │
│  复杂度：低                                                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ 用户需求不同
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              本平台（在 builder 之上扩展）                       │
│                                                              │
│  用户 → Web UI → 写 Groovy 代码 → 编译 → AgentScope 运行时    │
│  需要会 Java（或愿意学），理解 Tool/Middleware 概念                │
│  工具：**系统内置工具 + 用户自己写 @Tool 注解的 Groovy 类**       │
│  Middleware：用户自己继承 MiddlewareBase 写 Groovy 类            │
│  复杂度：中 ~ 高                                                │
│                                                              │
│  复用 builder 的：Channel、Gateway、Session、Auth、             │
│                  Workspace、Audit、Template、Share 等           │
│  新增：编译引擎、BuiltinToolRegistry、Tool/Middleware 管理       │
│        API、前端编辑器                                          │
└─────────────────────────────────────────────────────────────┘
```
