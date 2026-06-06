# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Full build (skip tests for faster iteration)
./mvnw clean install -DskipTests

# Build and run all tests
./mvnw clean verify

# Run tests for a specific module
./mvnw -pl agentscope-core test

# Run a single test class
./mvnw -pl agentscope-core -Dtest=ReActAgentTest test

# Code formatting check (google-java-format via Spotless)
./mvnw spotless:check

# Apply code formatting
./mvnw spotless:apply

# License header check
./mvnw license:check

# Generate JaCoCo coverage report
./mvnw jacoco:report

# Skip flaky or integration tests
./mvnw verify -DskipITs

# Build with mvnd (daemon Maven, used in CI)
mvnd -B -T1 clean verify
```

**Note:** CLAUDE.md is in `.gitignore` — it exists locally for code-assistance sessions but is not committed.

## Project Overview

AgentScope Java is an agent-oriented programming framework for building LLM-powered applications (by Alibaba). It uses the **ReAct (Reasoning-Acting)** paradigm — agents dynamically decide which tools to call and when, rather than following rigid workflows. The framework is reactive (Project Reactor), production-grade, and supports multi-agent collaboration.

### Modules

```
agentscope-parent (pom.xml, Java 17+)
├── agentscope-core              — Core framework: agents, models, messages, tools, hooks, middleware, memory, RAG, skills, planning, sessions, permissions, state, tracing
├── agentscope-harness           — Production runtime: sandbox execution, filesystem abstraction, skill curation, subagent management, memory compaction, workspace management
├── agentscope-extensions        — Integrations & extensions (see below)
├── agentscope-examples          — Example applications (builder, claw, coding, data agents + docs)
├── agentscope-dependencies-bom  — BOM for dependency management
└── agentscope-distribution      — Distribution artifacts (all-in-one uber-jar, BOM)
```

### agentscope-extensions Sub-modules

| Extension | Purpose |
|-----------|---------|
| `extensions-a2a` (client/server) | A2A protocol for distributed multi-agent collaboration |
| `extensions-agent-protocol` | Standard agent protocol implementation |
| `extensions-agui` | Agent GUI / visualization |
| `extensions-chat-completions-web` | OpenAI-compatible chat completions web API |
| `extensions-higress` | Higress API gateway integration |
| `extensions-kotlin` | Kotlin DSL support |
| `extensions-mem0` | Mem0 memory backend |
| `extensions-memory-bailian` | Alibaba Cloud Bailian memory |
| `extensions-nacos` (a2a/prompt/skill) | Nacos service discovery + config |
| `extensions-rag-*` | RAG backends (Bailian, Dify, Haystack, RAGFlow, Simple) |
| `extensions-reme` | Reme integration |
| `extensions-rocketmq` | RocketMQ messaging |
| `extensions-scheduler` (common/quartz/xxl-job) | Task scheduling |
| `extensions-session-mysql/redis` | Persistent session stores |
| `extensions-skill-*-repository` | Skill repositories (Git, MySQL) |
| `extensions-studio` | AgentScope Studio (visual debugging/monitoring) |
| `extensions-training` | Model fine-tuning support |
| `spring-boot-starters` | Spring Boot auto-configuration (A2A, Admin, AGUI, Chat, Nacos, core) |

### Core Architecture

#### Agent Hierarchy
```
Agent (interface — CallableAgent + StreamableAgent + ObservableAgent)
  └── AgentBase (abstract — hooks, subscriptions, interrupt handling, state management, tracing)
       └── ReActAgent (ReAct loop — memory, tools, middleware chain, structured output, streaming)
            └── HarnessAgent (harness module — sandbox, filesystem, skill curation, subagents)
```

**Key agent interfaces:**
- `CallableAgent.call(List<Msg>) → Mono<Msg>` — process messages, return one reply
- `StreamableAgent.stream(List<Msg>) → Flux<AgentEvent>` — stream execution events
- `ObservableAgent.observe(Msg)` — receive messages without replying (multi-agent pattern)

#### Middleware (Onion Architecture)
Middleware wraps agent execution in layers. Each layer can intercept and modify input/output:
- **Interception points:** `AgentInput`, `ReasoningInput`, `ActingInput`, `ModelCallInput`
- **Core middlewares:** `GracefulShutdownMiddleware`, `DynamicSkillMiddleware`, `PlanHintMiddleware`, `TaskReminderMiddleware`
- **Harness middlewares:** `CompactionMiddleware`, `SandboxLifecycleMiddleware`, `WorkspaceContextMiddleware`, `AtPathExpansionMiddleware`, `SubagentsMiddleware`, `HarnessSkillMiddleware`, `AgentTraceMiddleware`, `MemoryFlushMiddleware`, `MemoryMaintenanceMiddleware`, `ToolResultEvictionMiddleware`, `PlanModeMiddleware`, `SkillCuratorMiddleware`

#### Hook System (ReAct Loop Lifecycle)
Hooks fire at each stage of the ReAct loop:
```
PreCall → PreReasoning → Reasoning (streaming chunks) → PostReasoning
       → PreActing → Acting (streaming chunks) → PostActing
       → PreSummary → Summary (streaming chunks) → PostSummary
```
Chunk events (`ReasoningChunkEvent`, `ActingChunkEvent`, `SummaryChunkEvent`) enable real-time streaming. Error events propagate through `ErrorEvent`. Recorders like `JsonlTraceExporter` persist hook events.

#### Message System
- `Msg` — core message with `role` (USER/ASSISTANT/SYSTEM/TOOL), `content` (list of `ContentBlock`), `metadata` (Map)
- Content blocks: `TextBlock`, `ThinkingBlock`, `ImageBlock`, `AudioBlock`, `VideoBlock`, `DataBlock`, `ToolUseBlock`, `ToolResultBlock`, `HintBlock`
- Message subtypes: `UserMessage`, `AssistantMessage`, `SystemMessage`, `ToolResultMessage`

#### Model Layer
- `Model` interface: `stream(messages, tools, options) → Flux<ChatResponse>`
- Built-in models: `OpenAIChatModel`, `AnthropicChatModel`, `DashScopeChatModel`, `GeminiChatModel`, `OllamaChatModel`
- Each model uses a **Formatter** per provider to convert AgentScope `Msg` objects to provider-specific API formats (request/response DTOs live in `formatter/<provider>/dto/`)
- HTTP transport abstraction: `HttpTransport` (JDK or OkHttp), WebSocket transport support
- `ModelRegistry` for model discovery, `GenerateOptions` for per-call config

#### Tool System
- `Tool` / `ToolBase` — core tool interface with schema generation
- `ToolGroup` / `ToolGroupManager` — organize tools into groups
- `ToolRegistry` — central registration point
- `ToolSchemaGenerator` — auto-generates JSON Schema from Java classes via Jackson
- `ReflectiveFunctionTool` — wraps annotated Java methods as tools
- `McpClientManager` — manages MCP (Model Context Protocol) client connections
- Built-in tools: `ReadFileTool`, `WriteFileTool`, `ShellCommandTool`, `SubAgentTool`, multimodal tools, `TodoTools`
- `Toolkit` — declarative tool configuration (YAML/properties-based)

#### Memory System
- `Memory` interface with `InMemoryMemory` (conversation buffer)
- `LongTermMemory` — persistent, semantic-searchable memory with `LongTermMemoryMode` (AUTO, AGENT, HYBRID)
- `AgentStateMemoryView` — state-backed memory projection
- Harness: `MemoryConsolidator`, `MemoryFlushManager`, `ConversationCompactor` (with token counting and `ToolResultEvictionConfig`)

#### RAG (Retrieval-Augmented Generation)
- `Knowledge` interface — knowledge base access
- `KnowledgeRetrievalTools` — tool-based retrieval
- `GenericRAGHook` — hooks into ReAct loop at summary stage
- `RAGMode` — configurable behavior
- Extension modules for Bailian, Dify, Haystack, RAGFlow, Simple (in-memory/embedding)

#### Permission Engine
- `PermissionEngine` — rule-based permission checking for tool execution
- `PermissionMode` — enforcement levels, `PermissionRule` — individual rules
- Human-in-the-loop via `RequireUserConfirmEvent` and `UserAgent`

#### Skill System
- `AgentSkill` — skill definition (prompt + tools)
- `SkillBox` / `SkillRegistry` — management and discovery
- `SkillToolFactory` — creates tools from skill definitions
- Repositories: `ClasspathSkillRepository`, `FileSystemSkillRepository`
- Harness: `SkillCurator` (security scanning, allowlist, approval gates), `SkillRuntime`, `SkillCatalog`

#### Planning
- `PlanNotebook` — structured task management for decomposing complex goals
- `Plan` / `SubTask` — hierarchical task model with `PlanState` / `SubTaskState`
- `PlanHintMiddleware` injects plan context into agent prompts
- Storage: `InMemoryPlanStorage`, `PlanStorage` interface

#### Event System (Streaming)
Events drive both streaming and observation. Key event types:
- Agent lifecycle: `AgentStartEvent`, `AgentEndEvent`
- Model calls: `ModelCallStartEvent`, `ModelCallEndEvent`
- Streaming blocks: `TextBlock*Event`, `ThinkingBlock*Event`, `ToolCall*Event`, `ToolResult*Event`, `DataBlock*Event`
- Control: `ExceedMaxItersEvent`, `RequestStopEvent`, `RequireUserConfirmEvent`

#### State Management
- `AgentState` — serializable agent state with `SessionKey` scoping
- `State` interface — implemented by `Msg` and other stateful objects
- `PlanModeContextState`, `TaskContextState`, `ToolContextState` for scoped state

#### Tracing & Observability
- OpenTelemetry integration via `OtelTracingMiddleware`
- `Tracer` / `TracerRegistry` abstraction
- `JsonlTraceExporter` for hook event recording

#### Interruption & Graceful Shutdown
- `InterruptSource` / `InterruptContext` — reactive interrupt mechanism (checkpoints in Mono chain)
- `GracefulShutdownManager` — tracks active requests, drains pending work
- `PartialReasoningPolicy` — how to handle in-flight reasoning on shutdown

#### Session Management
- `Session` interface: `InMemorySession` (default), `JsonSession` (file-based)
- Extensions: MySQL (`extensions-session-mysql`), Redis (`extensions-session-redis`)
- Harness: `WorkspaceSession` — session tied to a workspace/sandbox

#### Harness Module (Production Runtime)
The `agentscope-harness` module adds enterprise features on top of core:
- **Sandbox execution:** Docker, E2B, Daytona, Kubernetes, AgentRun — isolated environments for tool code
- **Filesystem abstraction:** Local, Remote, Sandbox-backed filesystems with overlay/composite patterns
- **Skill curation:** `SkillCurator` with security scanning, allowlist/blocklist gates, approval workflows, and promotion
- **Subagent management:** Dynamic spawning, background tasks, task repositories, A2A-based remote subagents
- **Memory compaction:** Token-aware conversation compression, tool result eviction, session tree management
- **Workspace management:** File indexing, path policies, plan mode management

### Testing Patterns
- Mock models in `agentscope-core/src/test/java/io/agentscope/core/agent/test/MockModel.java`
- Reactor Test (`StepVerifier`) for reactive stream assertions
- Tests use `TestUtils` and mock toolkits for isolated agent testing
- Structured output tests validate JSON schema enforcement
- Integration tests live in `integration/` subdirectories

### Commit Convention
Follows [Conventional Commits](https://www.conventionalcommits.org/):
```
<type>(<scope>): <subject>
```
Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`
