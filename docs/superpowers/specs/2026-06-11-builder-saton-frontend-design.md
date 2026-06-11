# AgentScope Builder Saton — 前端设计文档

- **代号**: `agentscope-builder-saton-frontend`
- **作者**: chenygs
- **日期**: 2026-06-11
- **状态**: Draft（待 review）
- **配套后端 spec**: [2026-06-11-agentscope-builder-saton-design.md](./2026-06-11-agentscope-builder-saton-design.md)
- **风格参照**: [EKKOLearnAI/hermes-web-ui](https://github.com/EKKOLearnAI/hermes-web-ui) (Hermes Studio)

---

## 1. 项目定位

`agentscope-builder-saton` 后端配套的 Web 控制台。一句话：

> 用 Vue 3 + Naive UI 复刻 Hermes Studio 那种「资源型 sidebar + Agent 卡片网格 + 聊天主区」的多 Agent 工作台，跟后端 spec 第 7 节的 REST 接口 1:1 对接。

**关键差异**（相对于早期想法）：

- **不是 Maven 子模块**：前端是放在 `agentscope-builder-saton/frontend/` 下的**独立 pnpm 项目**，独立构建、独立部署，不打进 jar。
- **不是 vue-vben-admin**：vben-admin 是企业管理后台风格（顶部 tab 多页签 + 复杂菜单 + 厚重 layout），与 Hermes Studio 风格冲突，整体放弃改用 Naive UI 自搭。
- **不是 chat-first 的极简 UI**：sidebar 是资源式分类导航（Dashboard / Agent / 模型 / 工具 / 记忆），主区按路由切换；聊天只在 `/agents/:id/chat` 才出现。

---

## 2. 技术栈

| 维度 | 选型 | 选择理由 |
|---|---|---|
| 框架 | Vue 3.5 + TypeScript 5 | 与 Hermes Studio 同栈，生态成熟 |
| 构建 | Vite 6 | 默认事实标准 |
| UI 组件 | Naive UI 2.x | 深色友好、组件丰富、TS 友好；与 Hermes Studio 同款 |
| 路由 | Vue Router 4 | 标配 |
| 状态 | Pinia 2 | 比 Vuex 4 简单 50%，TS 推断好 |
| i18n | vue-i18n 9 | 中/英双语，默认中文 |
| HTTP | axios + 自封装拦截器 | 处理 sa-token cookie、401、统一错误 |
| SSE | @microsoft/fetch-event-source | 原生 EventSource 不支持 POST + 自定义 header，后端 `POST /api/agents/{id}/chat/stream` 必须用这个 |
| Markdown | markdown-it + highlight.js + KaTeX | 消息渲染 |
| 编辑器 | monaco-editor | workspace 文件 + sysPrompt 编辑 |
| 工具集 | @vueuse/core | hooks 工具集 |
| 原子 CSS | unocss | 少量微调，主体仍是 Naive UI |
| 包管理 | pnpm | 与 Vue 生态一致 |
| Node | 22 LTS | |

**不用**：tailwind（与 Naive UI token 冲突，单用 unocss 够了）、tdesign-vue-next/element-plus/ant-design-vue（与 Naive UI 二选一）、vuex 4、axios 拦截器之外的更复杂请求库。

---

## 3. 目录与构建

### 3.1 工程位置

```
agentscope-builder-saton/                ← 后端 Spring Boot 模块（已存在）
├── pom.xml                              ← 后端 pom，**不改**，不增加 frontend-maven-plugin
├── src/main/java/...                    ← 后端代码
├── src/main/resources/...               ← 后端资源（**不放前端产物**）
├── data/                                ← 后端 H2 数据库（已存在）
└── frontend/                            ← ★ 本文档涉及的范围
    ├── package.json
    ├── pnpm-lock.yaml
    ├── tsconfig.json
    ├── tsconfig.node.json
    ├── vite.config.ts
    ├── uno.config.ts
    ├── index.html
    ├── .gitignore                       (node_modules/, dist/)
    ├── .editorconfig
    ├── .prettierrc
    ├── public/
    │   └── favicon.svg
    └── src/
        ├── main.ts
        ├── App.vue
        ├── router/
        ├── stores/
        ├── api/
        ├── layouts/
        ├── views/
        ├── components/
        ├── composables/
        ├── i18n/
        ├── styles/
        └── types/
```

### 3.2 启动与构建命令

| 场景 | 命令 |
|---|---|
| 开发（vite dev server，:5173） | `cd frontend && pnpm dev` |
| 类型检查 | `pnpm typecheck` |
| 生产构建 | `pnpm build`（产物 `frontend/dist/`） |
| 预览构建 | `pnpm preview` |
| 后端独立启 | `mvn spring-boot:run`（沿用现有，仍是 :8080） |

### 3.3 部署形态

前端是**纯静态产物**，部署方式（生产）：

```
[ 用户浏览器 ]
      │
      ▼
[ nginx :80/:443 ]
      │
      ├─ / + /assets/* + 其他无扩展名 → 静态托管 frontend/dist/
      │                                  （SPA fallback 到 /index.html）
      │
      └─ /api/* → http://builder-saton-backend:8080
```

> 后端 spec §7 末尾保留了 `GET /, /assets/**, 其余无扩展名 → /static/index.html` 的兜底；现在前后端分离部署后，**这部分后端兜底逻辑可以不实现**（让 nginx 兜底），后端只暴露 `/api/**` 即可。这条作为「触发后端调整」记在第 12 节。

### 3.4 dev 代理配置

```ts
// vite.config.ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: false,
        // SSE 必须关 buffering
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform'
            }
          })
        },
      },
    },
  },
})
```

---

## 4. 路由表

```
/login                              LoginView (公开)

需登录（router guard 查 useAuth().isLoggedIn）:
/                                   redirect → /dashboard
/dashboard                          DashboardView (极简 hero)
/agents                             AgentListView (我的 Agent 网格列表)
/agents/new                         AgentSettingsView (创建走 settings 第一 tab)
/agents/:id/chat                    ChatView (★ 聊天主区)
/agents/:id/chat/:sessionKey        ChatView (历史 session)
/agents/:id/settings                redirect → /agents/:id/settings/basic
/agents/:id/settings/:tab           AgentSettingsView (基础/模型/工具/MCP/技能/Hook/子Agent/Workspace/记忆/沙箱/分享/活动/高级)
/models                             ModelsView
/tools                              ToolsView (内部 tabs: 工具/MCP/技能)
/memory                             MemoryView
/profile                            ProfileView (从头像 popover 点「个人资料」进入)
/:catchAll(.*)*                     NotFoundView
```

**路由守卫**：

```ts
router.beforeEach((to) => {
  if (to.path === '/login') return true
  if (!useAuth().isLoggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})
```

**鉴权**：sa-token 默认走 cookie（`Set-Cookie: satoken=xxx`），axios 加 `withCredentials: true` 即可，不手写 header。401 拦截器统一跳 `/login`。

---

## 5. 整体布局

### 5.1 MainLayout（所有需登录页都用）

```
┌────────────────────────────────────────────────────────────────────────┐
│ ┌─────────────────────┐ ┌────────────────────────────────────────────┐ │
│ │ Logo                │ │                                            │ │
│ │ AgentScope Builder  │ │                                            │ │
│ │                     │ │                                            │ │
│ │  ◻ Dashboard        │ │                                            │ │
│ │  ◻ Agent            │ │                                            │ │
│ │  ◻ 模型             │ │       <router-view />                      │ │
│ │  ◻ 工具             │ │                                            │ │
│ │  ◻ 记忆             │ │                                            │ │
│ │                     │ │                                            │ │
│ │  ─────────          │ │                                            │ │
│ │  🌐 中/英 切换       │ │                                            │ │
│ │  🌙 深/浅 切换       │ │                                            │ │
│ │  👤 admin ⌄         │ │                                            │ │
│ │  └─ popover:        │ │                                            │ │
│ │     ▸ 个人资料      │ │                                            │ │
│ │     ▸ 修改密码      │ │                                            │ │
│ │     ▸ 退出登录      │ │                                            │ │
│ └─────────────────────┘ └────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
   240px 固定 (可折叠为 64px 仅图标)        自适应主区
```

**关键约定**：

- sidebar 宽度 240px，可折叠到 64px（仅图标）；折叠状态写 `ui` store。
- sidebar 高亮匹配当前路由顶级段（如 `/agents/123/chat` → Agent 高亮）。
- 头像 popover 不是独立路由，而是 NPopover 弹出层。
- 「中/英」「深/浅」切换是即时生效，写 `localStorage` 持久化。

### 5.2 主题 token

启动时读 `localStorage.theme`（默认 `dark`），通过 Naive UI 的 `<n-config-provider :theme="darkTheme">` 切换。

主题色（深色态）：

```
背景:     #0a0a0a (深)  / #fafafa (浅)
面板:     #18181b      / #ffffff
边框:     #27272a      / #e4e4e7
文本主:   #fafafa      / #18181b
文本次:   #a1a1aa      / #71717a
主色:     #f97316 (橙) — 类 Anthropic
错误:     #ef4444
成功:     #22c55e
警告:     #eab308
信息:     #3b82f6
```

---

## 6. 各页面详细设计

### 6.1 Dashboard（极简 hero）

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│                                                                │
│                    AgentScope Builder                          │
│                                                                │
│              构建你的下一个 AI Agent                           │
│                                                                │
│                                                                │
│              ┌──────────────────────────┐                     │
│              │  + 创建你的第一个 Agent  │                     │
│              └──────────────────────────┘                     │
│                                                                │
│                                                                │
│              已有 Agent? → 进入 Agent 列表                    │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

无统计、无活动列表、无最近聊天。一句问候 + 主 CTA + 进入 Agent 列表的次级链接。

### 6.2 Agent 列表 `/agents`

```
┌────────────────────────────────────────────────────────────────────────┐
│  我的 Agent                                          [+ 新建 Agent]    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 🔍 搜索...                                  排序: 最近使用 ⌄      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│  │ 🤖 编码助手     │  │ 📊 数据分析师   │  │ 📝 写作助手     │        │
│  │ ─────────────   │  │ ─────────────   │  │ ─────────────   │        │
│  │ 帮你写代码、    │  │ SQL/Excel/...   │  │ 公文、报告、    │        │
│  │ 调试、重构...   │  │                 │  │ 邮件起草...     │        │
│  │                 │  │                 │  │                 │        │
│  │ 模型: 千问3     │  │ 模型: GPT-4     │  │ 模型: Claude    │        │
│  │ 工具:6  会话:12 │  │ 工具:3  会话:5  │  │ 工具:2  会话:8  │        │
│  │                 │  │                 │  │                 │        │
│  │ [💬 聊天][⚙ 设置]│  │ [💬 聊天][⚙ 设置]│  │ [💬 聊天][⚙ 设置]│        │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘        │
│  ┌─────────────────┐                                                   │
│  │  + 新建 Agent   │                                                   │
│  └─────────────────┘                                                   │
└────────────────────────────────────────────────────────────────────────┘
```

**Card 字段**（来自 `GET /api/agents`）：图标 (icon 字段，没有就按 agentType 给默认) / name / description (截断 2 行) / defaultModel 名 / 工具数 / 会话数。

**两按钮**：
- `💬 聊天` → `/agents/:id/chat`
- `⚙ 设置` → `/agents/:id/settings/basic`

**右上 `+ 新建 Agent`** → `/agents/new` → AgentSettings 页 stop 在 basic tab，无 id（保存后才生成）。

### 6.3 聊天页 `/agents/:id/chat`

```
┌────────────────────────────────────────────────────────────────────────────┐
│  ← 编码助手   模型: 千问3 ⌄   [Workspace][Sessions][子 Agent][分享][活动]  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │ 🤖  你好，我是编码助手。今天想做什么？                            │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │ 👤  帮我重构 util.py                                             │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │ 🤖  我看下文件内容                                                │      │
│  │     ┌───────────────────────────────────────────────────────────┐│      │
│  │     │ 🔧 read_file   ✓ 完成                                    ▾││      │
│  │     │   参数: { "path": "util.py" }                            ││      │
│  │     │   结果: def foo(): ...                                   ││      │
│  │     └───────────────────────────────────────────────────────────┘│      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │ 🤖  这是重构后的版本... [流式打字中 ▊]                            │      │
│  └──────────────────────────────────────────────────────────────────┘      │
│                                                                            │
├────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────┐      │
│  │  输入消息... (Shift+Enter 换行, Enter 发送)             [📎][▶] │      │
│  └──────────────────────────────────────────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────────────┘
```

**头部条**：
- 左：← 返回（去 `/agents`） + agent 名
- 中：模型 override 下拉（默认显示 agent 的 defaultModel；改成别的就是临时 override，发起 chat 时带 `overrideModelProviderId`）
- 右：5 个按钮，点击切右侧抽屉

**右侧抽屉**（默认收起，点按钮滑出，width 480px）：

| 抽屉 | 内容 |
|---|---|
| Workspace | 文件树（NTree）+ 选中文件预览/编辑（Monaco） + 上传 + 新建文件夹 |
| Sessions | 当前 agent 的会话列表（inbox），点切换 session |
| 子 Agent | 当前 agent 的 subagent 列表 |
| 分享 | 当前 agent 的 share ACL 列表（owner 可写） |
| 活动 | activity.jsonl 时间线 |

**消息渲染**：
- 用户消息：右对齐，简单 markdown
- Agent 消息：左对齐，完整 markdown（代码块带语法高亮 + 复制按钮、KaTeX 公式、表格、checklist）
- ToolCall 卡：嵌入 agent 消息底部，可折叠
  - 折叠态：`🔧 工具名   状态（pending/✓/✗）`
  - 展开态：参数（JSON 美化）+ 结果（markdown 或 JSON）

**输入框**（Composer）：
- 多行 NInput textarea，Enter 发送 / Shift+Enter 换行
- 📎 附件按钮（M2 之后做，先占位）
- ▶ 发送按钮，发送中变成 ■ 中断

### 6.4 Agent Settings `/agents/:id/settings/:tab`

```
┌────────────────────────────────────────────────────────────────────────┐
│ ← 返回   编码助手 / 设置                       [💬 进入聊天]            │
├────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐ ┌─────────────────────────────────────────────────┐│
│ │ ▸ 基础信息  ●   │ │ 当前 tab 表单                                   ││
│ │ ▸ 模型          │ │                                                 ││
│ │ ▸ 工具          │ │   [表单字段...]                                 ││
│ │ ▸ MCP           │ │                                                 ││
│ │ ▸ 技能          │ │                                                 ││
│ │ ▸ Hook          │ │                                                 ││
│ │ ▸ 子 Agent      │ │                                                 ││
│ │ ▸ Workspace     │ │                                                 ││
│ │ ▸ 记忆          │ │                                                 ││
│ │ ▸ 沙箱/权限     │ │                                                 ││
│ │ ▸ 分享          │ │                                                 ││
│ │ ▸ 活动日志      │ │                                                 ││
│ │ ▸ 高级          │ │                                                 ││
│ │ ─────────       │ │                                                 ││
│ │ 危险操作        │ │                                                 ││
│ │ ▸ Clone         │ │                                                 ││
│ │ ▸ 删除          │ │                              [取消] [保存 X]    ││
│ └─────────────────┘ └─────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────────────┘
```

**左 tab 栏**：垂直列表，分两组（常规 / 危险操作）。当前 tab 高亮；有未保存改动的 tab 显示 ●。

**右内容区**：当前 tab 的表单 + 底部独立「保存」按钮（每 tab 独立保存）。

**未保存提示**：切 tab 或离开页面时弹 NModal「你有未保存的修改，是否丢弃？」。

**Tab 内容速记**：

| Tab | 来源 API | 字段 |
|---|---|---|
| 基础信息 | `GET/PUT /api/agents/:id` | name, description, sysPrompt (Monaco markdown 编辑), agentType (harness/react), icon (emoji picker), maxIters |
| 模型 | `GET/PUT /api/agents/:id` + `GET /api/models` | defaultModelProviderId 下拉（选我已配置的 model） |
| 工具 | `GET/PUT /api/agents/:id/tools/config` + `GET /api/tools` (账号级启用集) | 我已启用工具勾选列表；勾上后展开该工具的 props 表单（JsonSchemaForm 渲染 `/api/factories/tool-types` 的 schema） |
| MCP | 同上，来源 `/api/mcp-servers?enabled=true` | 勾选要给 agent 用的 MCP server |
| 技能 | `GET/PUT /api/agents/:id/skills/workspace` + `GET /api/my-skills` | 已安装 skill 勾选 + 该 agent 的 workspace skill 文件管理 |
| Hook | 同基础 | hookSpecs 数组，每行 type 下拉 + JsonSchemaForm |
| 子 Agent | `GET/PUT /api/agents/:id/subagents` + `GET /api/agents` | 从我的其他 agent 多选 |
| Workspace | `GET /api/agents/:id/workspace/*` | 文件树 + 文件 CRUD + scaffold 按钮 + memory 文件查看 |
| 记忆 | `GET/PUT /api/agents/:id` (待后端补) | 长期记忆 provider 引用 + 模式 (LongTermMemoryMode) |
| 沙箱/权限 | 同基础 | sandboxMode / sandboxScope / runAs / workspacePath |
| 分享 | `GET/POST/DELETE /api/agents/:id/shares` | ACL 表（grantee_id, tier），加/删/改 tier |
| 活动日志 | `GET /api/agents/:id/activity` | 时间线视图 |
| 高级 | 同基础 | fork_of 信息（只读）、agent_id（只读）、原始 JSON dump |
| Clone | `POST /api/agents/:id/clone` | 弹窗确认 → 跳新 agent settings |
| 删除 | `DELETE /api/agents/:id` | 弹窗确认输入名字 → 删除 → 跳 `/agents` |

**「新建 Agent」走法**：

1. 用户点 `/agents` 上的 `+ 新建 Agent` → 跳 `/agents/new`
2. AgentSettings 页加载，路由检测 `:id === 'new'`，进入空白态：
   - tab 栏只有「基础信息」可点，其他 tab 灰掉（提示「请先保存基础信息」）
   - 表单初始值为空
3. 用户填 name (必填) + 选 defaultModelProviderId + 写 sysPrompt → 点保存
4. 后端返回 `{id: 123, ...}` → 前端 `router.replace('/agents/123/settings/basic')` → 其他 tab 解锁
5. 后续用户继续填其他 tab 即可

### 6.5 模型页 `/models`

```
┌────────────────────────────────────────────────────────────────────────┐
│  模型管理                                            [+ 新增模型]      │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 名称         类型         API 端点              更新时间   操作  │  │
│  │ 我的千问     dashscope    sk-xxxx****          2026-06-10  ✎ ⌫  │  │
│  │ GPT-4o       openai       sk-yyyy****          2026-06-09  ✎ ⌫  │  │
│  │ Claude 3.7   anthropic    sk-zzzz****          2026-06-08  ✎ ⌫  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

NDataTable 表格，每行一个 `model_provider`。

**新增/编辑** → 右滑抽屉：
1. 顶部下拉「模型类型」（从 `GET /api/factories/model-types` 取）
2. 中间是 JsonSchemaForm 根据所选类型的 schema 动态渲染（apiKey/baseUrl/modelName 等）
3. 底部「测试连接」按钮（M2+ 实现）+ 「保存」

**敏感字段 mask**：后端 list 返回 `sk-xxxx****`，编辑时不预填，前端用 placeholder 提示「留空表示不修改」。

### 6.6 工具页 `/tools`

```
┌────────────────────────────────────────────────────────────────────────┐
│  工具                                                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ [工具] [MCP 服务器] [技能]                          [+ 添加]    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                   │
│  │ shell-cmd    │ │ read-file    │ │ write-file   │                   │
│  │ 执行 shell   │ │ 读取文件     │ │ 写入文件     │                   │
│  │              │ │              │ │              │                   │
│  │ allowedCmds: │ │ rootPath:    │ │ rootPath:    │                   │
│  │ [ls, cat]    │ │ /workspace   │ │ /workspace   │                   │
│  │              │ │              │ │              │                   │
│  │ 激活 [✓ on]  │ │ 激活 [✓ on]  │ │ 激活 [○ off] │                   │
│  └──────────────┘ └──────────────┘ └──────────────┘                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                   │
│  │ plan-notebook│ │ sub-agent    │ │ mcp-bridge   │                   │
│  │ ...          │ │ ...          │ │ ...          │                   │
│  │ 激活 [✓ on]  │ │ 激活 [○ off] │ │ 激活 [✓ on]  │                   │
│  └──────────────┘ └──────────────┘ └──────────────┘                   │
└────────────────────────────────────────────────────────────────────────┘
```

**Tab 行为**：

| Tab | 数据源 | 卡片字段 | toggle 含义 |
|---|---|---|---|
| 工具 | `GET /api/tools` | type, displayName, props 摘要 | 账号级启用/禁用（PUT 后立即生效，影响所有 agent 下次重建） |
| MCP 服务器 | `GET /api/mcp-servers` | name, transport, props 摘要 | 同上 |
| 技能 | `GET /api/my-skills` | name, marketplace 来源, version | 同上 |

**`+ 添加` 按钮**：右滑抽屉，下拉选 type → JsonSchemaForm → 保存。

**toggle 语义**：点击立刻调 `PATCH /api/tools/:id?enabled=true|false`，乐观更新 UI。

### 6.7 记忆页 `/memory`

NDataTable 形态同 `/models`，每行一个 `memory_provider`。新增/编辑同样走 JsonSchemaForm。

### 6.8 个人资料页 `/profile`

```
┌────────────────────────────────────────────────────────────────────────┐
│  个人资料                                                              │
│                                                                        │
│  用户名: admin                                                         │
│  创建时间: 2026-06-01                                                  │
│                                                                        │
│  ── 修改密码 ──                                                       │
│  当前密码: [____]                                                      │
│  新密码:   [____]                                                      │
│  确认密码: [____]                                                      │
│  [保存]                                                                │
└────────────────────────────────────────────────────────────────────────┘
```

接 `GET /api/auth/me` 和 `POST /api/user/change-password`。

### 6.9 登录页 `/login`

```
┌────────────────────────────────────────────────────────────────────────┐
│                                                                        │
│                       AgentScope Builder                               │
│                                                                        │
│              ┌──────────────────────────────────┐                     │
│              │  用户名: [____________]          │                     │
│              │  密码:   [____________]          │                     │
│              │                                  │                     │
│              │  [登录]                          │                     │
│              └──────────────────────────────────┘                     │
│                                                                        │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

NCard + NForm + NInput。POST `/api/auth/login` 成功后 sa-token cookie 自动写入，跳 `query.redirect ?? '/dashboard'`。

---

## 7. 核心组件设计

### 7.1 `JsonSchemaForm`（★ 关键复用组件）

后端 6 大工厂（含新增的 MemoryFactory）的 Provider 都返回 `{type, displayName, description, schema: JsonSchema}`。前端这一个组件渲染所有场景：

- 模型表单（dashscope 要 apiKey/baseUrl/modelName）
- MCP 表单（stdio 要 command/args；http 要 url/headers）
- 工具表单（shell-cmd 要 allowedCommands）
- 技能仓库表单（git 要 url/branch/token）
- Hook 表单（audit-jsonl 要 path）
- 记忆表单（mem0 要 apiKey）

**实现策略**：自写 ~200 行组件，**不引第三方库**（Vue 生态目前无对标 react-jsonschema-form 的稳定库；引入后期反而难定制）。支持 JSON Schema 子集：

| Schema 关键字 | 渲染为 |
|---|---|
| `type: "string"` | NInput |
| `type: "string", enum: [...]` | NSelect |
| `type: "string", format: "password"` | NInput type=password + 「留空不修改」placeholder |
| `type: "string", format: "textarea"` | NInput type=textarea |
| `type: "integer"/"number"` | NInputNumber |
| `type: "boolean"` | NSwitch |
| `type: "array", items: {type: "string"}` | NDynamicTags |
| `type: "array", items: {type: "object"}` | NDynamicInput type=pair |
| `type: "object"` | 递归 |
| `required: [...]` | 字段必填校验 |
| `default` | 初始值 |
| `description` | 字段下方灰色提示文字 |
| `title` | 字段 label |

```vue
<!-- 使用 -->
<JsonSchemaForm
  v-model="formData"
  :schema="modelTypeSchema"
  @submit="save"
  @cancel="close"
/>
```

### 7.2 `MessageList` + `ToolCallCard`

SSE 事件流入 `useChat()` composable，按事件名（**不是 data 里的 type 字段**——后端 spec §12.15 已踩坑）分发：

| SSE event | 行为 |
|---|---|
| `token` | 追加到当前 assistant bubble 的 text，throttle 60ms 重渲染 |
| `tool_call` | 在 bubble 后插入新 `ToolCallCard`（状态=pending） |
| `tool_result` | 找到对应 `ToolCallCard`（按 callId），更新状态=done + 填 result |
| `done` | 收尾，bubble 落地 |
| `error` | 插入错误消息卡片 |

**ToolCallCard** 默认折叠态：`🔧 工具名   pending⟳ | ✓ 完成 | ✗ 失败`。点击展开看参数 JSON 和结果。

### 7.3 `AgentSettingsLayout`

通用左 tab 右内容布局组件，由 AgentSettingsView 复用：

```vue
<AgentSettingsLayout
  :tabs="tabs"
  :current="currentTab"
  :unsaved-tabs="unsavedSet"
  @change="onTabChange"
>
  <component :is="tabComponent" v-model="formData" @save="onSave" />
</AgentSettingsLayout>
```

切 tab 时检查 `unsavedSet.has(currentTab)`，是 → 弹未保存 NModal。

### 7.4 `ResourceCard`（工具页用）

```vue
<ResourceCard
  :icon="..."
  :title="..."
  :description="..."
  :props-summary="..."
  :enabled="..."
  @toggle="onToggle"
  @edit="onEdit"
  @delete="onDelete"
/>
```

---

## 8. 状态管理（Pinia store）

```
src/stores/
├── auth.ts           当前用户、登录态、登录/登出
├── ui.ts             侧栏折叠、主题、语言、抽屉开合
├── agents.ts         我的 agent 列表缓存
├── currentAgent.ts   当前查看的 agent 详情（含 settings 各 tab 数据）
├── sessions.ts       当前 agent 的会话列表/当前 session
├── chat.ts           当前 session 的消息流（SSE 状态、流式 buffer、ToolCall map）
├── factories.ts      /api/factories/* 元数据缓存（启动后拉一次，全程不刷）
├── resources.ts      models / mcp / my-skills / memory-providers 资源列表缓存
└── notifications.ts  全局 toast / 消息中心
```

**关键约定**：

- `factories` store 在 App 启动后立即拉一次所有 `/api/factories/*-types` 接口（5+ 个），结果缓存到内存；之后所有 JsonSchemaForm 直接从这里取 schema，不再请求。
- `currentAgent` store 离开 `/agents/:id/*` 路由时清空，避免脏数据。
- `chat` store 跟 sessionKey 绑定，切 session 时 reset。
- `auth` store 用 `pinia-plugin-persistedstate` 持久化到 localStorage。

---

## 9. API 层

```
src/api/
├── client.ts         axios 实例 + 拦截器（withCredentials, 401 跳 login, 统一错误）
├── sse.ts            fetch-event-source 封装（POST SSE）
├── auth.ts           login/logout/me/changePassword
├── agent.ts          CRUD/clone/draft/shares/activity
├── workspace.ts      scaffold/file/upload/memory/files
├── session.ts        inbox/get/reset/read/delete
├── chat.ts           session/send/stream
├── skill.ts          agent skills + marketplace
├── tool.ts           tools/active/config/catalog
├── subagent.ts       subagents
├── resource.ts       models/mcp/marketplaces/repos/my-tools/my-skills/memory-providers CRUD + toggle
├── factory.ts        /api/factories/*
└── template.ts       templates (即使 sidebar 不出现，创建流程仍用)
```

每个文件按 spec §7 的路由组织，一接口一函数：

```ts
// agent.ts
export function listAgents() {
  return client.get<AgentSummary[]>('/api/agents')
}
export function getAgent(id: number) {
  return client.get<AgentDetail>(`/api/agents/${id}`)
}
export function createAgent(req: CreateAgentReq) {
  return client.post<AgentDetail>('/api/agents', req)
}
// ...
```

### 9.1 SSE 客户端约定

```ts
// composables/useChat.ts
import { fetchEventSource } from '@microsoft/fetch-event-source'

export function useChat(agentId: number) {
  const chat = useChatStore()
  let ctrl: AbortController | null = null

  async function send(message: string, overrideModelProviderId?: number) {
    ctrl = new AbortController()
    chat.startTurn()

    await fetchEventSource(`/api/agents/${agentId}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ message, overrideModelProviderId }),
      signal: ctrl.signal,
      openWhenHidden: true,
      onmessage(ev) {
        switch (ev.event) {
          case 'token':       chat.appendToken(JSON.parse(ev.data)); break
          case 'tool_call':   chat.startToolCall(JSON.parse(ev.data)); break
          case 'tool_result': chat.endToolCall(JSON.parse(ev.data)); break
          case 'done':        chat.finishTurn(); break
          case 'error':       chat.errorTurn(ev.data); break
        }
      },
      onerror(err) {
        chat.errorTurn(String(err))
        throw err  // 否则 fetch-event-source 会自动重试
      },
    })
  }

  function abort() {
    ctrl?.abort()
    chat.abortTurn()
  }

  return { send, abort }
}
```

**注意**：

- `credentials: 'include'` 才会带 sa-token cookie
- 按 **event name** 路由，不读 `data.type` ——后端 spec §12.15 已说明 Jackson 3 不识别 Jackson 2 的 `@JsonSubTypes` 注解导致 data 里没有 type 字段
- `openWhenHidden: true` 让后台 tab 也保持连接
- `throw err` 抑制 fetch-event-source 默认的指数退避重连

---

## 10. i18n 约定

```ts
// i18n/index.ts
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') ?? 'zh-CN',
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

**所有用户可见文本必须走 i18n**。命名空间按页面分：

```
zh-CN.ts
{
  common: { save: '保存', cancel: '取消', delete: '删除', ... },
  auth: { username: '用户名', password: '密码', login: '登录', ... },
  agent: { list: '我的 Agent', create: '新建 Agent', ... },
  chat: { placeholder: '输入消息...', send: '发送', ... },
  settings: { basic: '基础信息', model: '模型', tools: '工具', ... },
  // ...
}
```

后端报错文案目前是中文硬编码（如「not logged in」），先用后端原文展示；M+ 阶段再做错误码映射。

---

## 11. 关键交互细节

### 11.1 未保存状态

每个 settings tab 是独立 form，本地 state 与 `currentAgent` store 中的服务器态对比：

```ts
const isDirty = computed(() => !isEqual(formData.value, serverData.value))
```

切 tab / 离开页面前 hook `beforeRouteLeave` + `beforeunload`：

```ts
onBeforeRouteLeave((to, from) => {
  if (!hasUnsavedTabs()) return true
  return new Promise((resolve) => {
    dialog.warning({
      title: '未保存的修改',
      content: `Tab [${unsavedTabsList()}] 有未保存改动，确认离开？`,
      positiveText: '丢弃并离开',
      negativeText: '留下',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
    })
  })
})
```

### 11.2 chat 中断

发送中点 ■ 按钮 → `ctrl.abort()` → store 标记 turn=aborted → UI 显示「已中断」。

### 11.3 模型 override 即时生效

聊天页头部 model 下拉切换 → 不触发后端调用，只更新 `chat.overrideModelId`，下次 `send()` 时带上。后端 spec §10.6 已注明 override 校验权限。

### 11.4 工具页 toggle 乐观更新

```ts
async function toggle(id: number, enabled: boolean) {
  const prev = !enabled
  list.value.find(t => t.id === id)!.enabled = enabled  // 乐观
  try {
    await api.tools.patchEnabled(id, enabled)
  } catch (e) {
    list.value.find(t => t.id === id)!.enabled = prev  // 回滚
    notification.error({ content: '切换失败' })
  }
}
```

### 11.5 错误处理

axios 响应拦截器：

- 401 → 清 auth store → 跳 `/login?redirect=...`
- 403 → toast「无权限」
- 4xx 其他 → toast 后端 error message
- 5xx → toast「服务器错误」+ 日志

业务组件不写 try/catch（除非要特殊处理），统一靠拦截器。

---

## 12. 触发后端调整清单

实现前端的过程中发现，后端 spec 需要补这些才能闭环：

### 12.1 数据表

| 表名 | 改动 | 理由 |
|---|---|---|
| `my_tool` | **新增** (id, owner_id, type, props_json 加密, enabled, created_at, updated_at) | 「工具页」需要账号级工具实例 + 启用 toggle |
| `mcp_server` | **加字段** `enabled BOOLEAN DEFAULT TRUE` | 同上，MCP 也要账号级 toggle |
| `my_skill` | **新增** (id, owner_id, source_marketplace_id?, name, version, props_json, enabled, created_at) | 同上 |
| `memory_provider` | **新增** (id, owner_id, name, type, props_json 加密, enabled, created_at, updated_at) | sidebar「记忆」页 |

### 12.2 工厂层

| 工厂 | 改动 |
|---|---|
| 五大工厂改 **六大工厂** | 新增 `MemoryFactory` + `MemoryProviderType` SPI 接口 + 内置 mem0/reme/bailian 三个 Provider |
| `AgentBuildOrchestrator` | 接收 `MemoryFactory` 装配进 HarnessAgent |

### 12.3 REST 接口

新增/修改：

```
GET/POST/PUT/DELETE/PATCH  /api/tools[/:id]                ← my_tool CRUD + toggle
PATCH                       /api/mcp-servers/:id            ← 给 mcp_server.enabled 加端点
GET/POST/PUT/DELETE/PATCH  /api/my-skills[/:id]            ← my_skill CRUD + toggle
GET/POST/PUT/DELETE/PATCH  /api/memory-providers[/:id]     ← memory_provider CRUD
GET                         /api/factories/memory-types     ← MemoryFactory 元数据
GET                         /api/agents/:id/icon            ← 可选：列表卡片用的 icon 字段
```

`agent_definition` 加字段：

```
icon                   VARCHAR(16)  emoji 或 icon name（列表卡片显示）
memory_provider_id     BIGINT       FK → memory_provider.id（可空）
memory_mode            VARCHAR(32)  对应 LongTermMemoryMode
```

### 12.4 SPA fallback

后端 spec §7 末尾的 `GET /, /assets/**, 其余无扩展名 → /static/index.html` 兜底**不再实现**（由 nginx 兜底），后端只暴露 `/api/**`。后端 controller 不挂任何根路径路由。

### 12.5 CORS

后端开发模式（profile=dev）允许 `http://localhost:5173` 跨域，带 cookie：

```yaml
spring:
  webflux:
    base-path: /
sa-token:
  is-cors: false  # 自己写 CorsWebFilter，因为要 allowCredentials
```

`CorsWebFilter` 设置 `Access-Control-Allow-Credentials: true` + `Allow-Origin: http://localhost:5173`（精确匹配，不能用 *）+ `Allow-Methods: *` + `Allow-Headers: *` + `Expose-Headers: Set-Cookie`。

生产模式同源部署（nginx 代理 /api），不需要 CORS。

### 12.6 SSE 序列化

后端 spec §12.15 已记录「Jackson 3 不识别 Jackson 2 注解，SSE data 没 type 字段」，前端约定按 event name 路由已绕开。**后端不需要改**，本节仅记录前端依赖此约定。

---

## 13. 落地里程碑

每个 milestone 都能跑通 demo。

| 里程碑 | 内容 | 完成标志 |
|---|---|---|
| **F1 工程骨架** | pnpm create vite + naive-ui + pinia + router + i18n + unocss；MainLayout + Login + Dashboard；axios 拦截器；fetch-event-source 装好 | 启动 `pnpm dev` → 登录 → 看到 Dashboard，sidebar 5 项可点（仅 Dashboard 有内容） |
| **F2 资源页（模型/MCP/记忆）** | ModelsView + MemoryView + JsonSchemaForm 通用组件；factories store；CRUD 全通；敏感字段 mask | 后端 M2 跑起来后，能加一个 DashScope，列表显示 `sk-xxxx****` |
| **F3 工具页** | ToolsView 三 tab；ResourceCard；账号级 toggle；CRUD（依赖后端补 my_tool / my_skill / mcp_server.enabled） | 后端补完后，三 tab 都能加/启用/禁用 |
| **F4 Agent 列表 + Settings 骨架** | AgentListView 卡片网格；AgentSettingsLayout + 基础信息/模型/沙箱三个 tab；新建走 settings 流程；每 tab 独立保存 + 未保存提示 | 能创建一个空 agent → 填基础信息 + 选模型 → 保存 → 列表出现 |
| **F5 聊天页（核心）** | ChatView + MessageList + ToolCallCard + Composer；useChat composable；SSE 接通；model override 下拉 | 聊一个 agent，token 流式打字、tool_call 卡片实时展开 |
| **F6 Agent Settings 剩余 tab** | 工具/MCP/技能/Hook/子 Agent/Workspace/记忆/分享/活动/高级 tab 全做；Workspace 抽屉用 Monaco | settings 全 tab 可读可写 |
| **F7 聊天右侧抽屉** | Workspace/Sessions/子 Agent/分享/活动 五抽屉 | 抽屉可开可用，sessions 可切换 |
| **F8 个人资料 + i18n 全量 + 主题切换** | ProfileView；i18n 中英两套全配齐；深浅色切换 | 切英文 / 切浅色 体验完整 |
| **F9 polish** | 错误页、空状态、loading skeleton、移动端 fallback（≥768px 全功能，<768px 简化提示）、键盘快捷键、accessibility | 视觉体感对齐 Hermes Studio |

---

## 14. Out of Scope（明确不做）

- 移动端原生体验（< 768px 仅显示「请用桌面端」）
- 离线模式 / PWA
- 多 workspace 切换（单用户单 workspace）
- 富文本编辑器（消息渲染只用 markdown，输入框只用 plain text）
- 群聊房间 / 多 Agent 协作 UI（后端 spec 已明确删除 IM 渠道）
- 主题 token 自定义编辑器（仅深浅两套预设）
- 插件市场 / 第三方扩展机制
- WebSocket（一律 SSE，简单稳定）
- iframe 嵌入第三方 chat widget
- 桌面端打包（Electron / Tauri）—— 前期就是 web

---

## 15. 开放问题（实施期再决定）

- JsonSchemaForm 自写到何种 schema 子集深度（嵌套 oneOf/anyOf 要不要支持？M+ 视后端 schema 复杂度决定）
- 大文件上传是否走分片（默认 multipart 单请求，超 100MB 再考虑分片）
- chat 消息历史本地缓存策略（目前每次切 session 都重拉，后续上 IndexedDB）
- workspace Monaco 编辑器是按 web worker 还是直接走主线程（大文件再优化）
- pinia 持久化只持久 auth 还是连 ui 偏好也持久（先只 auth，ui 偏好分散到 localStorage）

---

**End of frontend design** — 待 review 后进入 writing-plans 阶段，并需先把第 12 节「触发后端调整清单」回写到后端 spec。
