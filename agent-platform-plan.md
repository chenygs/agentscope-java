# AgentScope 可视化智能体平台 — 执行计划

> 基于 AgentScope Java 2.0 构建的前端可编程智能体平台
> 创建日期: 2026-06-05

---

## 一、核心理念

**一切皆代码 + 配置与逻辑分离**

| 分层 | 存哪里 | 用户可编辑 | 示例 |
|------|--------|-----------|------|
| 敏感配置 | `model_provider` 表 | ❌ 管理员后台维护 | API Key / Base URL |
| 业务逻辑 | `agent_definition` 代码字段 | ✅ Monaco 编辑器 | Model.java / Tool.java |
| 文档资产 | `agent_definition` 文本字段 | ✅ Markdown 编辑器 | AGENTS.md / SKILL.md |
| 权限规则 | `agent_permission_rule` 表 | ✅ 在线 JSON + 运行时累积 | allow / deny / ask |
| 运行时记忆 | AgentState → Session | ❌ 自动 | 用户「记住我的选择」 |

---

## 二、数据库设计

### 2.1 模型厂商表 `model_provider`

```sql
CREATE TABLE model_provider (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL COMMENT '厂商标识: dashscope/openai/anthropic/gemini/ollama',
    display_name    VARCHAR(100) NOT NULL COMMENT '展示名称: 阿里云百炼',
    api_key         TEXT         COMMENT 'API Key, AES 加密存储',
    base_url        VARCHAR(255) COMMENT '自定义端点, 为空则用官方默认',
    default_model   VARCHAR(100) COMMENT '默认模型: qwen-plus',
    enabled         BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) COMMENT='模型厂商配置';
```

### 2.2 智能体定义表 `agent_definition`

```sql
CREATE TABLE agent_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL COMMENT '智能体名称',
    description     VARCHAR(500) COMMENT '描述',
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/running/stopped',

    -- 模型选择
    provider_id     BIGINT       NOT NULL COMMENT '引用 model_provider.id',
    model_name      VARCHAR(100) NOT NULL COMMENT '实际使用的模型名',

    -- 代码字段 (Monaco 编辑器)
    model_code      MEDIUMTEXT   COMMENT 'Model.java 源码',
    tool_code       MEDIUMTEXT   COMMENT 'Tool.java 源码 (Groovy)',
    middleware_code MEDIUMTEXT   COMMENT 'Middleware.java 源码 (Groovy)',
    agent_code      MEDIUMTEXT   COMMENT 'Agent.java 源码 (Groovy, 拼装)',
    agents_md       MEDIUMTEXT   COMMENT 'AGENTS.md 内容',
    memory_md       MEDIUMTEXT   COMMENT 'MEMORY.md 初始内容',

    -- 配置
    permission_mode VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK',
    compaction_json JSON         COMMENT '压缩配置: {triggerMessages, keepMessages, ...}',
    react_config    JSON         COMMENT 'ReAct 配置: {maxIters, ...}',

    -- 文件系统模式
    filesystem_mode VARCHAR(20)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql/local/sandbox',

    -- 运行时
    session_backend VARCHAR(20)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql/redis',

    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    KEY idx_status (status)
) COMMENT='智能体定义';
```

### 2.3 权限规则表 `agent_permission_rule`

```sql
CREATE TABLE agent_permission_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL COMMENT '引用 agent_definition.id',
    tool_name       VARCHAR(100) NOT NULL COMMENT '工具名',
    rule_content    VARCHAR(500) COMMENT '匹配模式, null=匹配所有',
    behavior        VARCHAR(20)  NOT NULL COMMENT 'ALLOW / DENY / ASK',
    source          VARCHAR(30)  NOT NULL DEFAULT 'userSettings' COMMENT 'userSettings/projectSettings/session/suggested',
    enabled         BOOLEAN      DEFAULT TRUE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_id (agent_id),
    KEY idx_tool_name (tool_name)
) COMMENT='权限规则';
```

### 2.4 智能体 Skill 表 `agent_skill`

```sql
CREATE TABLE agent_skill (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL COMMENT '引用 agent_definition.id',
    name            VARCHAR(100) NOT NULL COMMENT 'skill 名称, 也是目录名',
    description     VARCHAR(500) COMMENT 'Agent 判断是否使用的依据',
    content         MEDIUMTEXT   NOT NULL COMMENT 'SKILL.md 内容',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_id (agent_id),
    UNIQUE KEY uk_agent_name (agent_id, name)
) COMMENT='Skill 定义';
```

### 2.5 智能体知识库表 `agent_knowledge`

```sql
CREATE TABLE agent_knowledge (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL COMMENT '引用 agent_definition.id',
    file_name       VARCHAR(200) NOT NULL COMMENT '文件名, 含相对路径: knowledge/api-ref.md',
    content         MEDIUMTEXT   NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_id (agent_id),
    UNIQUE KEY uk_agent_file (agent_id, file_name)
) COMMENT='知识库文件';
```

### 2.6 工具模板表 (可选) `tool_template`

```sql
CREATE TABLE tool_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    code            MEDIUMTEXT   NOT NULL COMMENT 'Groovy 源码',
    shared          BOOLEAN DEFAULT FALSE COMMENT '是否共享给所有 Agent',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='工具模板';
```

### 2.7 对话记录表 `chat_session` / `chat_message`

```sql
CREATE TABLE chat_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT       NOT NULL,
    session_id      VARCHAR(100) NOT NULL COMMENT 'AgentScope sessionId',
    user_id         VARCHAR(100) DEFAULT 'anonymous',
    title           VARCHAR(200),
    status          VARCHAR(20)  DEFAULT 'active' COMMENT 'active/archived',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_session (agent_id, session_id)
) COMMENT='对话会话';

CREATE TABLE chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL COMMENT 'user/assistant/tool',
    content         MEDIUMTEXT   NOT NULL,
    tokens          INT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session_id (session_id)
) COMMENT='对话消息';
```

---



## 二点五、JPA 实体与 Repository 示例

### 实体类注解

```java
@Entity
@Table(name = "agent_definition")
public class AgentDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    // 模型选择 (1:1)
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    // 代码字段 (Monaco 编辑器) — 以下均为 1:1
    @Column(columnDefinition = "MEDIUMTEXT")
    private String modelCode;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String agentCode;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String agentsMd;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String memoryMd;

    // 一对多子表关系
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AgentTool> tools = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AgentMiddleware> middlewares = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentHook> hooks = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentKnowledge> knowledges = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PermissionRule> permissionRules = new ArrayList<>();

    // 时间戳
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

子表典型结构:

```java
@Entity
@Table(name = "agent_tool")
public class AgentTool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private AgentDefinition agent;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String code;

    private Boolean enabled = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
```

### Repository 接口

所有 Repository 继承 `JpaRepository`, 无需手写实现:

```java
// 主表
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, Long> {
    Optional<AgentDefinition> findByName(String name);
}

// 子表 — JPA 方法命名约定自动实现查询
public interface AgentToolRepository extends JpaRepository<AgentTool, Long> {
    List<AgentTool> findByAgentIdOrderBySortOrder(Long agentId);
    List<AgentTool> findByAgentIdAndEnabledTrueOrderBySortOrder(Long agentId);
}

public interface AgentMiddlewareRepository extends JpaRepository<AgentMiddleware, Long> {
    List<AgentMiddleware> findByAgentIdOrderBySortOrder(Long agentId);
}

public interface AgentHookRepository extends JpaRepository<AgentHook, Long> {
    List<AgentHook> findByAgentId(Long agentId);
}

public interface PermissionRuleRepository extends JpaRepository<PermissionRule, Long> {
    List<PermissionRule> findByAgentId(Long agentId);
}

public interface UserFilesystemRepository extends JpaRepository<UserFilesystem, Long> {
    List<UserFilesystem> findByUserId(String userId);
}

public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFile, Long> {
    WorkspaceFile findByAgentIdAndPath(Long agentId, String path);
    boolean existsByAgentIdAndPath(Long agentId, String path);
    List<WorkspaceFile> findByAgentIdAndPathStartingWith(Long agentId, String dirPath);
}
```

> **JPA vs MyBatis 要点:**
> - 不再需要手写 SQL, CRUD 由 JPA 自动生成
> - 关联查询通过 `@Entity` 注解的关联关系 + `@EntityGraph` / JPQL
> - 子表通过 `@OneToMany(cascade = ALL, orphanRemoval = true)` 级联保存/删除
> - 复杂统计可通过 `@Query` JPQL 或 Specification 实现

## 三、项目模块结构

```
agent-scope-platform/
├── pom.xml                          # 父 POM
├── agent-api/                       # 接口定义模块
│   ├── src/main/java/.../dto/       # DTO
│   │   ├── AgentCreateRequest.java
│   │   ├── AgentDTO.java
│   │   └── PermissionRuleDTO.java
│   └── src/main/java/.../vo/        # 返回 VO
│       ├── AgentStatusVO.java
│       └── CompileResultVO.java
│
├── agent-compiler/                  # 编译引擎 (核心)
│   ├── pom.xml
│   ├── src/main/java/.../compiler/
│   │   ├── GroovyAgentCompiler.java      # Groovy 编译入口
│   │   ├── CodePreprocessor.java          # 占位符替换 ($apiKey$ → 实际值)
│   │   ├── SecurityAstAnalyzer.java       # AST 安全分析
│   │   └── SandboxClassLoader.java        # 隔离 ClassLoader
│   └── src/test/.../CompilerTest.java
│
├── agent-filesystem/                # MySQL 文件系统实现
│   ├── pom.xml
│   └── src/main/java/.../filesystem/
│       ├── MysqlFilesystemSpec.java       # extends AbstractFilesystemSpec
│       ├── MysqlFilesystemConfig.java     # @Configuration
│       └── mapper/FileSystemMapper.java   # MyBatis Plus Mapper
│
├── agent-session/                   # MySQL Session 实现
│   ├── pom.xml
│   └── src/main/java/.../session/
│       ├── MysqlAgentSession.java         # implements Session
│       └── repository/SessionRepository.java
│
├── agent-skill/                     # MySQL Skill 仓库实现
│   ├── pom.xml
│   └── src/main/java/.../skill/
│       ├── MysqlSkillRepository.java      # implements AgentSkillRepository
│       └── repository/SkillRepository.java
│
├── agent-manager/                   # Agent 生命周期管理 (核心)
│   ├── pom.xml
│   └── src/main/java/.../manager/
│       ├── AgentManager.java              # 创建/启动/停止/恢复
│       ├── AgentRegistry.java             # 运行中 Agent 注册表
│       ├── AgentBuilder.java              # 编译 + 装配 HarnessAgent
│       └── PermissionRuleBuilder.java     # 权限规则装配
│
├── agent-web/                       # Web 层 (Controller)
│   ├── pom.xml
│   └── src/main/java/.../web/
│       ├── controller/
│       │   ├── AgentController.java       # CRUD + 创建启动
│       │   ├── ChatController.java        # SSE 对话流
│       │   ├── ModelProviderController.java
│       │   ├── PermissionRuleController.java
│       │   ├── SkillController.java
│       │   └── WorkspaceFileController.java  # AGENTS.md / MEMORY.md 编辑
│       └── interceptor/
│           └── SecurityInterceptor.java
│
├── agent-bootstrap/                 # Spring Boot 启动
│   ├── pom.xml
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── repository/             # JPA Repository
│   └── src/main/java/.../
│       └── Application.java
│
└── agent-ui/                        # Vue 3 + TypeScript
    ├── package.json
    ├── vite.config.ts
    ├── src/
    │   ├── App.vue
    │   ├── router/
    │   ├── views/
    │   │   ├── dashboard/               # 仪表盘
    │   │   ├── agent/                   # 智能体列表/创建
    │   │   ├── editor/                  # Monaco 代码编辑器
    │   │   ├── chat/                    # 对话面板
    │   │   └── settings/               # 权限规则编辑器
    │   └── components/
    │       ├── MonacoEditor.vue          # 代码编辑器组件
    │       ├── MarkdownEditor.vue        # Markdown 编辑器
    │       ├── HitlDialog.vue            # 人工确认弹窗
    │       └── AgentStatusBadge.vue
    └── public/
```

---

## 四、核心实现

### 4.1 编译引擎 `GroovyAgentCompiler.java`

```java
public class GroovyAgentCompiler {
    private final GroovyClassLoader loader;
    private final CodePreprocessor preprocessor;
    private final SecurityAstAnalyzer securityAnalyzer;

    // ===== 编译 Model.java =====
    // 输入: 用户写的 Groovy 代码 + provider 信息
    // 处理: 替换 $apiKey$ $baseUrl$ $modelName$
    // 输出: Model 实例
    public Model compileModel(String code, ModelProvider provider, String modelName) {
        String processed = preprocessor.replaceModelPlaceholders(code, provider, modelName);
        securityAnalyzer.analyze(processed);
        return (Model) eval(processed, "model");
    }

    // ===== 编译 Tool.java =====
    // 输入: 用户写的 Groovy 代码
    // 输出: Object 实例 (含 @Tool 方法)
    public Object compileTool(String code) {
        securityAnalyzer.analyze(code);
        Class<?> clazz = loader.parseClass(code);
        return clazz.getDeclaredConstructor().newInstance();
    }

    // ===== 编译 Middleware.java =====
    // 输入: 用户写的 Groovy 代码
    // 输出: MiddlewareBase 实例
    public MiddlewareBase compileMiddleware(String code) {
        securityAnalyzer.analyze(code);
        return (MiddlewareBase) eval(code, "mw");
    }

    // ===== 编译 Agent.java (拼装) =====
    // 输入: 用户写的拼装代码 + 预编译的依赖实例
    // 输出: HarnessAgent 实例
    public HarnessAgent compileAgent(String code, Map<String, Object> bindings) {
        securityAnalyzer.analyze(code);
        return (HarnessAgent) eval(code, bindings, "agent");
    }

    private Object eval(String code, String returnVar) {
        return eval(code, Map.of(), returnVar);
    }

    private Object eval(String code, Map<String, Object> bindings, String returnVar) {
        GroovyShell shell = new GroovyShell(loader);
        bindings.forEach((k, v) -> shell.setVariable(k, v));
        Object result = shell.evaluate(code);
        // 或者: 要求用户代码以 "return xxx;" 结尾
        return result;
    }
}
```

### 4.2 占位符预处理 `CodePreprocessor.java`

```java
public class CodePreprocessor {

    public String replaceModelPlaceholders(String code,
                                           ModelProvider provider,
                                           String modelName) {
        return code
            .replace("$apiKey$", decrypt(provider.getApiKey()))
            .replace("$baseUrl$", provider.getBaseUrl() != null
                ? provider.getBaseUrl() : "")
            .replace("$modelName$", modelName);
    }

    public String replaceAgentPlaceholders(String code, AgentDefinition def) {
        return code.replace("$agentName$", def.getName());
    }
}
```

### 4.3 MySQL 文件系统 `MysqlFilesystemSpec.java`

```java
/**
 * 核心思路: 实现 AgentScope 2.0 的 AbstractFilesystemSpec
 *
 * 框架在每轮推理时, 通过这个接口读取:
 *   - AGENTS.md        → 注入 system prompt
 *   - MEMORY.md        → 注入长期记忆
 *   - skills/*/SKILL.md → 技能列表
 *   - knowledge/*       → 知识库
 *   - tools.json        → 工具配置
 */
public class MysqlFilesystemSpec extends AbstractFilesystemSpec {

    private final Long agentId;
    private final FileSystemMapper mapper;

    public MysqlFilesystemSpec(Long agentId, FileSystemMapper mapper) {
        super(/* LocalFilesystemSpec 的默认配置 */);
        this.agentId = agentId;
        this.mapper = mapper;
    }

    @Override
    public Mono<byte[]> read(String path) {
        // path 示例: "AGENTS.md" / "skills/code-reviewer/SKILL.md"
        // 从 MySQL 查询对应文件内容
        return Mono.fromCallable(() -> {
            WorkspaceFile file = repo.findByAgentIdAndPath(agentId, path);
            if (file == null) {
                throw new FileNotFoundException("File not found: " + path);
            }
            return file.getContent().getBytes(StandardCharsets.UTF_8);
        });
    }

    @Override
    public Mono<Void> write(String path, byte[] content) {
        return Mono.fromRunnable(() -> {
            repo.save(new WorkspaceFile(agentId, path, new String(content, StandardCharsets.UTF_8)));
        });
    }

    @Override
    public Mono<Boolean> exists(String path) {
        return Mono.fromCallable(() ->
            repo.countByAgentIdAndPath(agentId, path) > 0);
    }

    @Override
    public Mono<List<String>> list(String dirPath) {
        return Mono.fromCallable(() ->
            repo.findByAgentIdAndPathStartingWith(agentId, dirPath));
    }
}
```

### 4.4 Agent 管理器 `AgentManager.java`

```java
@Component
public class AgentManager {

    // agentId → HarnessAgent 实例
    private final Map<Long, HarnessAgent> runningAgents = new ConcurrentHashMap<>();

    // agentId → 最后一次编译的 AgentCodeDTO (用于重启恢复)
    private final Map<Long, AgentCodeDTO> agentCodes = new ConcurrentHashMap<>();

    @Autowired private GroovyAgentCompiler compiler;
    @Autowired private AgentDefinitionMapper defMapper;
    @Autowired private ModelProviderMapper providerMapper;
    @Autowired private PermissionRuleMapper ruleMapper;
    @Autowired private SkillMapper skillMapper;
    @Autowired private FileSystemMapper fsMapper;
    @Autowired private DataSource dataSource;

    /** 创建并启动 Agent */
    public HarnessAgent createAndStart(Long agentId) {
        AgentDefinition def = defMapper.selectById(agentId);
        ModelProvider provider = providerMapper.selectById(def.getProviderId());

        // 1. 编译 Model
        Model model = compiler.compileModel(def.getModelCode(), provider, def.getModelName());

        // 2. 编译 Tool
        Toolkit toolkit = new Toolkit();
        if (def.getToolCode() != null) {
            Object toolInstance = compiler.compileTool(def.getToolCode());
            toolkit.registerTool(toolInstance);
        }

        // 3. 编译 Middleware (可选)
        List<MiddlewareBase> middlewares = new ArrayList<>();
        if (def.getMiddlewareCode() != null) {
            MiddlewareBase mw = compiler.compileMiddleware(def.getMiddlewareCode());
            middlewares.add(mw);
        }

        // 4. 装配权限规则
        PermissionContextState permCtx = buildPermissionContext(agentId);

        // 5. 构建 MySQL 文件系统 / Session / Skill 仓库
        MysqlFilesystemSpec fs = new MysqlFilesystemSpec(agentId, fsMapper);
        MysqlAgentSession session = new MysqlAgentSession(dataSource);
        MysqlSkillRepository skillRepo = new MysqlSkillRepository(agentId, skillMapper);

        // 6. 编译 Agent 拼装代码
        Map<String, Object> bindings = Map.of(
            "model", model,
            "toolkit", toolkit,
            "middlewares", middlewares,
            "mysqlFilesystem", fs,
            "mysqlSession", session,
            "mysqlSkillRepo", skillRepo,
            "permCtx", permCtx
        );

        HarnessAgent agent = compiler.compileAgent(def.getAgentCode(), bindings);

        // 7. 注册到运行时
        runningAgents.put(agentId, agent);
        agentCodes.put(agentId, toCodeDTO(def));

        // 8. 更新状态
        def.setStatus("running"); defRepo.save(def);

        return agent;
    }

    /** 停止 Agent */
    public void stop(Long agentId) {
        HarnessAgent agent = runningAgents.remove(agentId);
        if (agent != null) {
            // AgentState 由 shutdownManager 自动保存
            // 无需手动操作
        }
        def.setStatus("stopped"); defRepo.save(def);
    }

    /** 获取运行时 Agent 并对话 */
    public HarnessAgent getRunningAgent(Long agentId) {
        HarnessAgent agent = runningAgents.get(agentId);
        if (agent == null) {
            // 尝试从数据库恢复: 重新编译启动
            return createAndStart(agentId);
        }
        return agent;
    }

    /** 在线更新权限规则 (不需要重启 Agent) */
    public void updatePermissionRules(Long agentId) {
        HarnessAgent agent = runningAgents.get(agentId);
        if (agent == null) return;

        PermissionContextState newPermCtx = buildPermissionContext(agentId);
        // AgentScope 2.0 支持运行时替换 PermissionContext
        // 通过 middleware 或直接替换 AgentState 中的上下文
        agent.getAgentState().setPermissionContext(newPermCtx.toJson());
    }

    /** 装配权限规则 */
    private PermissionContextState buildPermissionContext(Long agentId) {
        AgentDefinition def = defMapper.selectById(agentId);
        List<PermissionRule> rules = ruleMapper.selectByAgentId(agentId);

        PermissionContextState ctx = PermissionContextState.builder()
            .mode(PermissionMode.valueOf(def.getPermissionMode()))
            .build();

        for (PermissionRule rule : rules) {
            if (!rule.getEnabled()) continue;
            PermissionRule pr = new PermissionRule(
                rule.getToolName(), rule.getRuleContent(),
                PermissionBehavior.valueOf(rule.getBehavior()),
                rule.getSource());
            switch (rule.getBehavior()) {
                case "ALLOW" -> ctx.addAllowRule(rule.getToolName(), pr);
                case "DENY"  -> ctx.addDenyRule(rule.getToolName(), pr);
                case "ASK"   -> ctx.addAskRule(rule.getToolName(), pr);
            }
        }
        return ctx;
    }
}
```

### 4.5 对话 SSE 接口

```java
@RestController
@RequestMapping("/api/agent/{agentId}/chat")
public class ChatController {

    @Autowired private AgentManager agentManager;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @PathVariable Long agentId,
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        HarnessAgent agent = agentManager.getRunningAgent(agentId);
        RuntimeContext ctx = RuntimeContext.builder()
            .sessionId(sessionId)
            .userId(getCurrentUserId())
            .build();

        return agent.streamEvents(new UserMessage(message), ctx)
            .map(this::toSSE);
    }

    /** HITL: 用户确认 */
    @PostMapping("/confirm")
    public Mono<Map<String, String>> confirm(
            @PathVariable Long agentId,
            @RequestParam String sessionId,
            @RequestBody List<ConfirmResult> results) {

        HarnessAgent agent = agentManager.getRunningAgent(agentId);
        RuntimeContext ctx = RuntimeContext.builder()
            .sessionId(sessionId).userId(getCurrentUserId()).build();

        // 如果用户勾选了「记住我的选择」
        // ConfirmResult 中已包含 acceptedRules
        // Agent 自动写入 AgentState.permissionContext
        // 由 Session 持久化

        UserMessage resumeMsg = UserMessage.builder()
            .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
            .build();

        return agent.call(List.of(resumeMsg), ctx)
            .map(msg -> Map.of("reply", msg.getTextContent()));
    }
}
```

---

## 五、前端结构

### 5.1 路由设计

```
/                    → Dashboard (智能体列表)
/agent/create        → 创建智能体
/agent/:id/edit      → 编辑智能体 (代码编辑器)
/agent/:id/chat      → 对话面板
/agent/:id/settings  → 权限规则编辑器
/providers           → 模型厂商管理
/skills              → 技能管理
```

### 5.2 创建智能体页面布局

```
┌──────────────────────────────────────────────────────────────────┐
│  创建智能体                                                       │
├──────────────────────────────────────────────────────────────────┤
│  基本信息                                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 名称: [________]  描述: [________________________]         │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  模型配置                                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 厂商: [阿里云百炼 ▼]   模型: [qwen-plus ▼]                │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ Model.java (Monaco 编辑器)                            │  │ │
│  │ │ DashScopeChatModel model = DashScopeChatModel          │  │ │
│  │ │     .builder()                                        │  │ │
│  │ │     .apiKey("$apiKey$")   // 自动替换                   │  │ │
│  │ │     .modelName("$modelName$")                          │  │ │
│  │ │     .stream(true)                                      │  │ │
│  │ │     .formatter(new DashScopeChatFormatter())           │  │ │
│  │ │     .build();                                          │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  工具 (可选)                                                      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ ○ 使用模板: [hello-world ▼]  [加载]                        │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ Tool.java (Monaco 编辑器)                              │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  中间件 (可选)                                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ Middleware.java (Monaco 编辑器)                         │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Workspace 文件                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ [AGENTS.md] [KNOWLEDGE.md] [+ 添加文件]                    │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ AGENTS.md (Markdown 编辑器)                            │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  权限                                                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Mode: [DEFAULT ▼]                                          │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ 预设 Deny 规则: drop_table, force_push               │  │ │
│  │ │ [+] 添加                                              │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  压缩配置                                                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 触发消息数: [30]  保留消息数: [10]  大结果卸载: [✓]     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────┐  ┌────────────┐                                 │
│  │ 💾 保存草稿 │  │ 🚀 创建启动 │                                 │
│  └────────────┘  └────────────┘                                 │
└──────────────────────────────────────────────────────────────────┘
```

### 5.3 对话页面布局

```
┌──────────────────────────────────────────────────────────────────┐
│  my-assistant                                  [权限规则] [设置]  │
├──────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 用户: 帮我把 /etc/config.yml 里的 timeout 改成 30          │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ Agent: [Thinking...]                                       │ │
│  │        调用 read_file...                                    │ │
│  │        ├── /etc/config.yml 的内容: ...                      │ │
│  │        │   timeout: 60                                     │ │
│  │        │   ...                                             │ │
│  │        └── 已读取                                          │ │
│  │                                                             │ │
│  │  ╔══════════════════════════════════════════════════════╗   │ │
│  │  ║ 🔔 智能体需要 write_file                              ║   │ │
│  │  ║  路径: /etc/config.yml                               ║   │ │
│  │  ║  内容: timeout: 30  ...                              ║   │ │
│  │  ║                                                      ║   │ │
│  │  ║  ☑ 记住我的选择                                      ║   │ │
│  │  ║    对此工具的: ○ 全部放行  ● 仅此路径  ○ 拒绝        ║   │ │
│  │  ║                                                      ║   │ │
│  │  ║        [ 🚫 拒绝 ]        [ ✅ 允许 ]                ║   │ │
│  │  ╚══════════════════════════════════════════════════════╝   │ │
│  │                                                             │ │
│  │ Agent: 已将 /etc/config.yml 的 timeout 从 60 改为 30。     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  [________________________________________________] [发送]      │
└──────────────────────────────────────────────────────────────────┘
```

### 5.4 权限规则编辑器页面

```
┌──────────────────────────────────────────────────────────────────┐
│  权限设置 — my-assistant                      [保存]             │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Mode                                                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ [DEFAULT ▼]   — 未命中规则的操作需用户确认                  │ │
│  │ 其他选项: ACCEPT_EDITS / EXPLORE / BYPASS / DONT_ASK       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  运行时记忆的规则 (AgentState 自动持久化)                        │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 来源: 用户「记住我的选择」                                   │ │
│  │                                                             │ │
│  │ 工具         匹配模式        行为      来源       操作      │ │
│  │ ─────────────────────────────────────────────────────       │ │
│  │ write_file   /etc/*         ASK       session    [删除]     │ │
│  │ read_file    null           ALLOW     session    [删除]     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  用户预设规则 (存 agent_permission_rule 表)                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ [+ 添加规则]                                                │ │
│  │                                                             │ │
│  │ 工具         匹配内容        行为      来源       操作      │ │
│  │ ─────────────────────────────────────────────────────       │ │
│  │ drop_table    null           DENY      user       [编辑][删] │ │
│  │ execute       rm -rf         DENY      user       [编辑][删] │ │
│  │ write_file    /workspace/**  ALLOW     user       [编辑][删] │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  JSON 编辑器 (高级)                                             │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ [切换到 JSON 编辑]                                          │ │
│  │ {                                                            │ │
│  │   "mode": "DEFAULT",                                        │ │
│  │   "rules": [ ... ]                                          │ │
│  │ }                                                            │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## 六、关键接口

### 6.1 REST API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/agent` | 智能体列表 |
| POST | `/api/agent` | 创建智能体 (存草稿) |
| GET | `/api/agent/{id}` | 智能体详情 |
| PUT | `/api/agent/{id}` | 更新智能体 |
| DELETE | `/api/agent/{id}` | 删除智能体 |
| POST | `/api/agent/{id}/start` | **编译 + 启动** |
| POST | `/api/agent/{id}/stop` | 停止 |
| GET | `/api/agent/{id}/chat/stream` | SSE 对话流 |
| POST | `/api/agent/{id}/chat/confirm` | HITL 确认 |
| GET/PUT | `/api/agent/{id}/permissions` | 权限规则 CRUD |
| GET/PUT | `/api/agent/{id}/skills` | Skill CRUD |
| GET/PUT | `/api/agent/{id}/files/*` | Workspace 文件 CRUD |
| GET/PUT | `/api/providers` | 模型厂商 CRUD |
| GET | `/api/agent/{id}/compile` | **只编译不启动 (语法验证)** |

### 6.2 SSE 事件类型 (转发到前端)

```json
{"type":"TEXT_BLOCK_DELTA","delta":"你好"}
{"type":"TOOL_CALL_START","toolName":"read_file","arguments":"{\"path\":\"/etc/config.yml\"}"}
{"type":"TOOL_RESULT_END","toolName":"read_file","state":"SUCCESS"}
{"type":"REQUIRE_CONFIRM","replyId":"xxx","toolCalls":[{"name":"write_file","input":"..."}]}
{"type":"AGENT_END","replyId":"xxx"}
```

---

## 七、安全设计

### 7.1 AST 安全分析 `SecurityAstAnalyzer.java`

用户代码编译前必须经过 AST 分析，禁止：

```java
// 黑名单 API 调用 (示例)
BLACKLIST = [
    "java.lang.Runtime.exec",
    "java.lang.ProcessBuilder",
    "java.lang.ClassLoader",
    "java.io.FileOutputStream",  // 通过 MysqlFilesystem 走
    "java.net.Socket",
    "java.sql.DriverManager",
]

// 白名单包 (放行)
WHITELIST_PACKAGES = [
    "io.agentscope",
    "java.time",
    "java.util",
    "com.fasterxml.jackson",
]
```

### 7.2 API Key 加密

```java
// AES 加密存储
@Column(name = "api_key")
private String apiKey;  // 存入时 AES 加密, 读出时解密

// 编译时替换占位符, 不暴露给前端
String resolved = code.replace("$apiKey$", decrypt(provider.getApiKey()));
```

### 7.3 隔离 ClassLoader

```java
// 每个 Agent 有独立的 GroovyClassLoader
// Agent 停止后卸载, 防止类泄漏
public class AgentClassLoader extends GroovyClassLoader {
    private final Long agentId;

    public AgentClassLoader(Long agentId) {
        super(AgentClassLoader.class.getClassLoader());
        this.agentId = agentId;
    }
}
```

---

## 八、开发阶段规划

### Phase 1: 基础设施 (3-5天)

| 任务 | 产出 |
|------|------|
| 新建 Maven 项目 + 模块结构 | 项目骨架 |
| 引入 AgentScope 2.0 + Spring Boot 4.x + MyBatis Plus | pom.xml |
| 建表 SQL + 实体类 + Repository | 数据库层 |
| GroovyAgentCompiler (编译管道) | 编译引擎 |
| MysqlFilesystemSpec | 文件系统 |

### Phase 2: Agent 管理 (3-5天)

| 任务 | 产出 |
|------|------|
| AgentManager (创建/启动/停止) | 生命周期管理 |
| AgentBuilder (编译+装配 HarnessAgent) | 构建引擎 |
| PermissionRuleBuilder (权限规则装配) | 权限系统 |
| AgentRegistry (运行时注册表) | 运行管理 |

### Phase 3: Web 接口 (3-5天)

| 任务 | 产出 |
|------|------|
| AgentController CRUD | REST API |
| ChatController SSE 流式 | 对话接口 |
| HITL 确认接口 | 人工审核 |
| PermissionRuleController | 权限编辑 |

### Phase 4: 前端 (5-10天)

| 任务 | 产出 |
|------|------|
| 项目初始化 (Vue 3 + Vite + Ant Design) | 前端骨架 |
| Monaco Editor 集成 | 代码编辑器 |
| Markdown Editor 集成 | 文档编辑器 |
| 创建智能体页面 | 用户交互 |
| 对话面板 (SSE 渲染 + HITL 弹窗) | 实时对话 |
| 权限规则编辑器 | 规则管理 |

### Phase 5: 打磨 (3-5天)

| 任务 | 产出 |
|------|------|
| 安全 AST 分析 | 代码安全 |
| API Key 加密存储 | 敏感信息保护 |
| 错误处理和提示 | 用户体验 |
| 对话历史存储 | 历史记录 |

---

## 九、关键风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| Groovy 编译性能 | 低 | 中 | 编译结果缓存, 只改过的代码重新编译 |
| 用户恶意代码 | 中 | 高 | AST 分析 + 黑名单 API + Docker 沙箱执行 |
| 类加载泄漏 | 中 | 中 | 独立 ClassLoader, 停止时 GC |
| Agent 实例内存占用 | 低 | 中 | 闲置 Agent 自动休眠, 按需恢复 |
| API Key 泄露 | 低 | 高 | 加密存储 + 编译时替换 + 前端不可见 |

---

## 十、技术栈汇总

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 4.x |
| 智能体框架 | AgentScope Java 2.0.0-RC1 |
| 数据库 | MySQL 8.0 + Spring Data JPA (Hibernate) |
| 动态编译 | Groovy 4.0.14 (内置) |
| 前端 | Vue 3 + TypeScript + Vite |
| 代码编辑器 | Monaco Editor |
| SSE 流式 | Spring WebFlux |
| 安全 | AST 分析 (参考 apboa/security) |
| 构建工具 | Maven 3.9+ |
| JDK | 21 |
