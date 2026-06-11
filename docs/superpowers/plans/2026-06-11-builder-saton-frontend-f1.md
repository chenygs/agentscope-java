# F1 — 工程骨架实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**配套 spec:** [2026-06-11-builder-saton-frontend-design.md](../specs/2026-06-11-builder-saton-frontend-design.md) §13 F1

**Goal:** 把 `agentscope-builder-saton/frontend/` 从空起到可启动；登录后能看到 sidebar + Dashboard 极简页，MSW 全套 mock 设施 + IndexedDB 持久化就绪。

**Architecture:** Vite 6 构建，Naive UI 组件库 + Pinia + Vue Router 4；MSW 拦截 `/api/*`，数据持久化到 IndexedDB；i18n 中英双语骨架；深浅主题切换。F1 完成后形成"地基" —— F2-F9 在此之上添加功能页。

**Tech Stack:** Vue 3.5 + TS 5 + Vite 6 + Naive UI 2.x + Pinia 2 + Vue Router 4 + vue-i18n 9 + MSW 2 + idb 8 + @microsoft/fetch-event-source + unocss + @vueuse/core

**F1 完成标志:** 启动 `pnpm dev` → 浏览器 `http://localhost:5173` 自动跳 `/login` → 用 admin/admin 登录 → 跳 Dashboard → sidebar 5 项均可点（其他 4 项暂跳占位空页）→ 头像 popover 切换语言/主题/退出全部生效 → 刷新页面登录态保留。

---

## 任务 1-7：MSW 设施 + 基础架子（已写）

### 任务 1: pnpm 项目初始化 + 依赖安装

- [ ] **创建目录并 pnpm init**
```bash
mkdir -p D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend
cd D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend
pnpm create vite . --template vue-ts
```

- [ ] **安装核心依赖**
```bash
pnpm add naive-ui @juggle/resize-observer
pnpm add vue-router@4 pinia pinia-plugin-persistedstate
pnpm add vue-i18n@9 @vueuse/core
pnpm add axios @microsoft/fetch-event-source
pnpm add msw@2 idb
pnpm add markdown-it highlight.js @highlightjs/vue-plugin
pnpm add monaco-editor
pnpm add unocss @unocss/reset
pnpm add @iconify/vue @iconify-json/ph
pnpm add -D typescript@5 @types/node @vitejs/plugin-vue
pnpm add -D unocss @unocss/preset-uno @unocss/transformer-directives
pnpm add -D @types/markdown-it @types/highlight.js
pnpm add -D eslint prettier eslint-plugin-vue
```

- [ ] **初始化 MSW 浏览器 worker**
```bash
cd D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend
npx msw init public/
```

- [ ] **提交: pnpm + deps + msw init**
```bash
git add -A && git commit -m "feat(saton): pnpm project init with all deps + MSW"
```

### 任务 2: TypeScript + Vite + UnoCSS 配置

- [ ] **创建 tsconfig.json**
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "jsx": "preserve",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noUnusedLocals": false,
    "noUnusedParameters": false,
    "paths": { "@/*": ["./src/*"] },
    "baseUrl": ".",
    "types": ["vite/client"]
  },
  "include": ["src/**/*.ts", "src/**/*.vue", "src/**/*.d.ts"],
  "exclude": ["node_modules", "dist"]
}
```

- [ ] **更新 vite.config.ts**
```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), UnoCSS()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    open: false,
  },
})
```

- [ ] **创建 uno.config.ts**
```ts
import { defineConfig, presetUno, transformerDirectives } from 'unocss'

export default defineConfig({
  presets: [presetUno()],
  transformers: [transformerDirectives()],
  shortcuts: {
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
  },
})
```

- [ ] **提交: config files**
```bash
git add -A && git commit -m "feat(saton): ts/vite/unocss config"
```

### 任务 3: 创建目录结构 + 入口文件骨架

- [ ] **创建所有空目录**
```bash
cd D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend\src
mkdir -p router stores api api/mock api/mock/handlers api/mock/db api/mock/fixtures
mkdir -p layouts views views/auth views/dashboard views/agents views/chat views/tools views/settings views/profile
mkdir -p components components/chat components/form components/common
mkdir -p composables i18n styles types
```

- [ ] **创建 main.ts**
```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPersistedstate from 'pinia-plugin-persistedstate'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import 'virtual:uno.css'
import '@unocss/reset/tailwind.css'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPersistedstate)
app.use(pinia).use(router).use(i18n)
app.mount('#app')
```

- [ ] **创建 App.vue(骨架)**
```vue
<script setup lang="ts">
import { darkTheme, NConfigProvider } from 'naive-ui'
import { useTheme } from './composables/useTheme'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const { theme, themeOverrides } = useTheme()
</script>

<template>
  <NConfigProvider
    :theme="theme"
    :theme-overrides="themeOverrides"
    :locale="undefined"
    :date-locale="undefined"
  >
    <router-view />
  </NConfigProvider>
</template>
```

- [ ] **创建 .gitignore**
```
node_modules/
dist/
*.local
public/mockServiceWorker.js
```

- [ ] **提交: 目录结构 + 入口**
```bash
git add -A && git commit -m "feat(saton): directory scaffold + app entry"
```

### 任务 4: 主题 composable

- [ ] **创建 src/composables/useTheme.ts**
```ts
import { computed, ref, watch } from 'vue'
import type { GlobalThemeOverrides } from 'naive-ui'

const isDark = ref(localStorage.getItem('theme') !== 'light')

export function useTheme() {
  const theme = computed(() => (isDark.value ? darkTheme : null))

  const themeOverrides = computed<GlobalThemeOverrides>(() => ({
    common: {
      primaryColor: '#f97316',
      primaryColorHover: '#fb923c',
      primaryColorPressed: '#ea580c',
      bodyColor: isDark.value ? '#0a0a0a' : '#fafafa',
      cardColor: isDark.value ? '#18181b' : '#ffffff',
      modalColor: isDark.value ? '#18181b' : '#ffffff',
      popoverColor: isDark.value ? '#18181b' : '#ffffff',
      borderColor: isDark.value ? '#27272a' : '#e4e4e7',
      textColor1: isDark.value ? '#fafafa' : '#18181b',
      textColor2: isDark.value ? '#a1a1aa' : '#71717a',
      tableHeaderColor: isDark.value ? '#202022' : '#f4f4f5',
      inputColor: isDark.value ? '#202022' : '#f4f4f5',
      hoverColor: isDark.value ? '#202022' : '#f4f4f5',
    },
  }))

  function toggleTheme() {
    isDark.value = !isDark.value
    localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
  }

  return { isDark, theme, themeOverrides, toggleTheme }
}
```

- [ ] **提交: useTheme**
```bash
git add -A && git commit -m "feat(saton): useTheme composable with dark/light"
```

### 任务 5: i18n 中英架子

- [ ] **创建 src/i18n/index.ts**
```ts
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export default createI18n({
  legacy: false,
  locale: localStorage.getItem('locale') ?? 'zh-CN',
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

- [ ] **创建 src/i18n/zh-CN.ts (完整版，覆盖所有页面)**
```ts
export default {
  common: {
    save: '保存',
    cancel: '取消',
    delete: '删除',
    edit: '编辑',
    create: '新建',
    confirm: '确认',
    discard: '丢弃',
    search: '搜索...',
    back: '返回',
    noData: '暂无数据',
    loading: '加载中...',
    error: '出错了',
    retry: '重试',
    status: '状态',
    action: '操作',
    name: '名称',
    type: '类型',
    description: '描述',
    enabled: '启用',
    disabled: '禁用',
    yes: '是',
    no: '否',
    required: '必填',
  },
  auth: {
    title: 'AgentScope Builder',
    username: '用户名',
    password: '密码',
    login: '登录',
    loggingIn: '登录中...',
    logout: '退出登录',
    loginFailed: '用户名或密码错误',
    changePassword: '修改密码',
    currentPassword: '当前密码',
    newPassword: '新密码',
    confirmPassword: '确认密码',
    passwordChanged: '密码修改成功',
    profile: '个人资料',
    createdAt: '创建时间',
  },
  dashboard: {
    title: 'AgentScope Builder',
    subtitle: '构建你的下一个 AI Agent',
    cta: '创建你的第一个 Agent',
    secondary: '已有 Agent？进入 Agent 列表',
  },
  sidebar: {
    dashboard: 'Dashboard',
    agents: 'Agent',
    models: '模型',
    tools: '工具',
    memory: '记忆',
    language: '语言',
    theme: '主题',
  },
  agent: {
    list: '我的 Agent',
    create: '新建 Agent',
    chat: '聊天',
    settings: '设置',
    clone: 'Clone',
    delete: '删除',
    deleteConfirm: '确认删除？删除后不可恢复。',
    deleteConfirmName: '请输入 agent 名称确认删除',
    noAgents: '还没有创建 Agent，点击上方按钮开始创建',
    searchPlaceholder: '搜索 Agent...',
    sortRecent: '最近使用',
    sortName: '按名称',
    sortCreated: '按创建时间',
    model: '模型',
    tools: '工具',
    sessions: '会话',
    // Settings tabs
    tabBasic: '基础信息',
    tabModel: '模型',
    tabTools: '工具',
    tabMcp: 'MCP',
    tabSkills: '技能',
    tabHook: 'Hook',
    tabSubAgents: '子 Agent',
    tabWorkspace: 'Workspace',
    tabMemory: '记忆',
    tabSandbox: '沙箱/权限',
    tabShares: '分享',
    tabActivity: '活动日志',
    tabAdvanced: '高级',
    tabDanger: '危险操作',
    unsavedWarning: '你有未保存的修改，是否丢弃？',
    saveSuccess: '保存成功',
    // Basic tab
    name: '名称',
    icon: '图标',
    sysPrompt: '系统提示词',
    agentType: 'Agent 类型',
    maxIters: '最大迭代次数',
    defaultModel: '默认模型',
    noModelConfig: '请先保存基础信息后再配置模型',
    // Sandbox tab
    sandboxMode: '沙箱模式',
    sandboxScope: '沙箱范围',
    runAs: '运行身份',
    workspacePath: '工作区路径',
  },
  chat: {
    placeholder: '输入消息... (Shift+Enter 换行, Enter 发送)',
    send: '发送',
    stop: '中断',
    interrupted: '已中断',
    toolCall: '调用',
    toolResult: '完成',
    toolError: '失败',
    modelOverride: '模型',
    workspace: 'Workspace',
    sessions: '会话',
    subAgents: '子 Agent',
    shares: '分享',
    activity: '活动',
    messageCopied: '消息已复制',
  },
  model: {
    title: '模型管理',
    add: '新增模型',
    edit: '编辑模型',
    delete: '删除模型',
    deleteConfirm: '确认删除此模型？',
    name: '名称',
    type: '类型',
    endpoint: 'API 端点',
    updatedAt: '更新时间',
    addTitle: '新增模型',
    editTitle: '编辑模型',
    modelType: '模型类型',
    testConnection: '测试连接',
    placeholderNoChange: '留空表示不修改',
    saveSuccess: '模型保存成功',
    deleteSuccess: '模型已删除',
    noData: '还没有添加模型，点击右上角按钮开始添加',
  },
  tools: {
    title: '工具',
    tabTools: '工具',
    tabMcp: 'MCP 服务器',
    tabSkills: '技能',
    add: '添加',
    addTitle: '添加',
    editTitle: '编辑',
    deleteConfirm: '确认删除？',
    enable: '激活',
    disable: '停用',
    toggleSuccess: '切换成功',
    saveSuccess: '保存成功',
    deleteSuccess: '已删除',
    noData: '暂无数据',
    propsSummary: '配置',
  },
  memory: {
    title: '记忆',
    add: '新增记忆',
    edit: '编辑记忆',
    delete: '删除记忆',
    deleteConfirm: '确认删除此记忆配置？',
    name: '名称',
    type: '类型',
    addTitle: '新增记忆提供商',
    editTitle: '编辑记忆提供商',
    saveSuccess: '保存成功',
    deleteSuccess: '已删除',
    noData: '还没有配置记忆，点击右上角按钮添加',
  },
  settings: {
    profile: '个人资料',
  },
  profile: {
    title: '个人资料',
    username: '用户名',
    createdAt: '创建时间',
  },
  notFound: {
    title: '404',
    message: '页面不存在',
    back: '返回首页',
  },
}
```

- [ ] **创建 src/i18n/en-US.ts**
```ts
export default {
  common: {
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    edit: 'Edit',
    create: 'New',
    confirm: 'Confirm',
    discard: 'Discard',
    search: 'Search...',
    back: 'Back',
    noData: 'No data',
    loading: 'Loading...',
    error: 'Something went wrong',
    retry: 'Retry',
    status: 'Status',
    action: 'Actions',
    name: 'Name',
    type: 'Type',
    description: 'Description',
    enabled: 'Enabled',
    disabled: 'Disabled',
    yes: 'Yes',
    no: 'No',
    required: 'Required',
  },
  auth: {
    title: 'AgentScope Builder',
    username: 'Username',
    password: 'Password',
    login: 'Login',
    loggingIn: 'Logging in...',
    logout: 'Logout',
    loginFailed: 'Invalid username or password',
    changePassword: 'Change Password',
    currentPassword: 'Current Password',
    newPassword: 'New Password',
    confirmPassword: 'Confirm Password',
    passwordChanged: 'Password changed successfully',
    profile: 'Profile',
    createdAt: 'Created At',
  },
  dashboard: {
    title: 'AgentScope Builder',
    subtitle: 'Build your next AI Agent',
    cta: 'Create Your First Agent',
    secondary: 'Have an Agent? Go to Agent List',
  },
  sidebar: {
    dashboard: 'Dashboard',
    agents: 'Agents',
    models: 'Models',
    tools: 'Tools',
    memory: 'Memory',
    language: 'Language',
    theme: 'Theme',
  },
  agent: {
    list: 'My Agents',
    create: 'New Agent',
    chat: 'Chat',
    settings: 'Settings',
    clone: 'Clone',
    delete: 'Delete',
    deleteConfirm: 'Are you sure? This cannot be undone.',
    deleteConfirmName: 'Type the agent name to confirm deletion',
    noAgents: 'No agents yet. Click the button above to create one.',
    searchPlaceholder: 'Search agents...',
    sortRecent: 'Most Recent',
    sortName: 'By Name',
    sortCreated: 'By Created',
    model: 'Model',
    tools: 'Tools',
    sessions: 'Sessions',
    tabBasic: 'Basic',
    tabModel: 'Model',
    tabTools: 'Tools',
    tabMcp: 'MCP',
    tabSkills: 'Skills',
    tabHook: 'Hook',
    tabSubAgents: 'Sub-Agents',
    tabWorkspace: 'Workspace',
    tabMemory: 'Memory',
    tabSandbox: 'Sandbox',
    tabShares: 'Shares',
    tabActivity: 'Activity',
    tabAdvanced: 'Advanced',
    tabDanger: 'Danger Zone',
    unsavedWarning: 'You have unsaved changes. Discard them?',
    saveSuccess: 'Saved successfully',
    name: 'Name',
    icon: 'Icon',
    sysPrompt: 'System Prompt',
    agentType: 'Agent Type',
    maxIters: 'Max Iterations',
    defaultModel: 'Default Model',
    noModelConfig: 'Please save basic info first before configuring the model.',
    sandboxMode: 'Sandbox Mode',
    sandboxScope: 'Sandbox Scope',
    runAs: 'Run As',
    workspacePath: 'Workspace Path',
  },
  chat: {
    placeholder: 'Type a message... (Shift+Enter newline, Enter send)',
    send: 'Send',
    stop: 'Stop',
    interrupted: 'Interrupted',
    toolCall: 'Calling',
    toolResult: 'Completed',
    toolError: 'Failed',
    modelOverride: 'Model',
    workspace: 'Workspace',
    sessions: 'Sessions',
    subAgents: 'Sub-Agents',
    shares: 'Shares',
    activity: 'Activity',
    messageCopied: 'Message copied',
  },
  model: {
    title: 'Models',
    add: 'Add Model',
    edit: 'Edit Model',
    delete: 'Delete Model',
    deleteConfirm: 'Delete this model?',
    name: 'Name',
    type: 'Type',
    endpoint: 'API Endpoint',
    updatedAt: 'Updated',
    addTitle: 'Add Model',
    editTitle: 'Edit Model',
    modelType: 'Model Type',
    testConnection: 'Test Connection',
    placeholderNoChange: 'Leave empty to keep current value',
    saveSuccess: 'Model saved',
    deleteSuccess: 'Model deleted',
    noData: 'No models yet. Click the button above to add one.',
  },
  tools: {
    title: 'Tools',
    tabTools: 'Tools',
    tabMcp: 'MCP Servers',
    tabSkills: 'Skills',
    add: 'Add',
    addTitle: 'Add',
    editTitle: 'Edit',
    deleteConfirm: 'Delete this item?',
    enable: 'Enable',
    disable: 'Disable',
    toggleSuccess: 'Toggled successfully',
    saveSuccess: 'Saved',
    deleteSuccess: 'Deleted',
    noData: 'No data',
    propsSummary: 'Config',
  },
  memory: {
    title: 'Memory',
    add: 'Add Memory',
    edit: 'Edit Memory',
    delete: 'Delete Memory',
    deleteConfirm: 'Delete this memory provider?',
    name: 'Name',
    type: 'Type',
    addTitle: 'Add Memory Provider',
    editTitle: 'Edit Memory Provider',
    saveSuccess: 'Saved',
    deleteSuccess: 'Deleted',
    noData: 'No memory providers yet.',
  },
  settings: {
    profile: 'Profile',
  },
  profile: {
    title: 'Profile',
    username: 'Username',
    createdAt: 'Created At',
  },
  notFound: {
    title: '404',
    message: 'Page not found',
    back: 'Go Home',
  },
}
```

- [ ] **提交: i18n 中英初始全量**
```bash
git add -A && git commit -m "feat(saton): i18n zh-CN + en-US full keys"
```

### 任务 6: 核心类型定义

- [ ] **创建 src/types/index.ts**
```ts
// ── 用户 ──
export interface User {
  userId: string
  username: string
  createdAt: number
}

// ── 资源 ──
export interface ModelProvider {
  id: number
  ownerId: string
  name: string
  type: string
  props: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

export interface McpServer {
  id: number
  ownerId: string
  name: string
  transport: 'stdio' | 'sse' | 'http'
  props: Record<string, unknown>
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface MySkill {
  id: number
  ownerId: string
  name: string
  sourceMarketplaceId?: string
  version?: string
  props: Record<string, unknown>
  enabled: boolean
  createdAt: number
}

export interface MyTool {
  id: number
  ownerId: string
  type: string
  props: Record<string, unknown>
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface MemoryProvider {
  id: number
  ownerId: string
  name: string
  type: string
  props: Record<string, unknown>
  enabled: boolean
  createdAt: number
  updatedAt: number
}

export interface SkillMarketplace {
  id: number
  ownerId: string
  marketplaceId: string
  type: string
  props: Record<string, unknown>
  createdAt: number
}

export interface SkillRepository {
  id: number
  ownerId: string
  name: string
  type: string
  props: Record<string, unknown>
  createdAt: number
}

// ── Agent ──
export interface ToolSpec {
  type: string
  props?: Record<string, unknown>
}

export interface SkillRef {
  repoId: number
  name: string
}

export interface HookSpec {
  type: string
  props?: Record<string, unknown>
}

export interface AgentSummary {
  id: number
  agentId: string
  ownerId: string
  name: string
  description: string
  icon?: string
  agentType: string
  defaultModelProviderId: number
  defaultModelName?: string
  toolCount?: number
  sessionCount?: number
  createdAt: number
  updatedAt: number
}

export interface AgentDetail {
  id: number
  agentId: string
  ownerId: string
  name: string
  description: string
  icon?: string
  sysPrompt: string
  agentType: string
  defaultModelProviderId: number
  defaultModelName?: string
  maxIters: number
  workspacePath: string
  toolSpecs: ToolSpec[]
  skillRefs: SkillRef[]
  hookSpecs: HookSpec[]
  subagentRefs: string[]
  skillRepositoryIds: number[]
  sandboxMode: string
  sandboxScope: string
  runAs: string
  forkOf?: string
  memoryProviderId?: number
  memoryMode?: string
  createdAt: number
  updatedAt: number
}

export interface AgentShare {
  id: number
  agentDefId: number
  granteeType: string
  granteeId: string
  tier: 'EDIT' | 'RUN' | 'CLONE'
  createdBy: string
  createdAt: number
}

// ── Session / Chat ──
export interface Session {
  key: string
  agentId: string
  messageCount: number
  createdAt: number
  updatedAt: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  timestamp: number
  toolCalls?: ToolCallInfo[]
}

export interface ToolCallInfo {
  callId: string
  toolName: string
  status: 'pending' | 'running' | 'done' | 'error'
  args?: string
  result?: string
}

// ── Factory ──
export interface ProviderMeta {
  type: string
  displayName: string
  description: string
  schema: JsonSchema
}

export interface JsonSchema {
  type: string
  title?: string
  description?: string
  properties?: Record<string, JsonSchemaProperty>
  required?: string[]
}

export interface JsonSchemaProperty {
  type: string
  title?: string
  description?: string
  default?: unknown
  enum?: string[]
  format?: string
  properties?: Record<string, JsonSchemaProperty>
  items?: JsonSchemaProperty
  required?: string[]
}

// ── Activity ──
export interface ActivityEntry {
  timestamp: number
  action: string
  detail: string
}

// ── Workspace ──
export interface WorkspaceFile {
  name: string
  path: string
  type: 'file' | 'directory'
  size?: number
  children?: WorkspaceFile[]
}

// ── Factory response ──
export interface FactoryTypesResponse {
  [key: string]: ProviderMeta[]
}
```

- [ ] **提交: types/**
```bash
git add -A && git commit -m "feat(saton): core TypeScript types"
```

### 任务 7: MSW + IndexedDB 基础设施

- [ ] **创建 src/api/mock/db/index.ts**
```ts
import { openDB, type IDBPDatabase } from 'idb'

const DB_NAME = 'agentscope-builder'
const DB_VERSION = 1

let dbPromise: Promise<IDBPDatabase> | null = null

export async function getDb(): Promise<IDBPDatabase> {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('models')) {
          db.createObjectStore('models', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('mcpServers')) {
          db.createObjectStore('mcpServers', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('mySkills')) {
          db.createObjectStore('mySkills', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('myTools')) {
          db.createObjectStore('myTools', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('memoryProviders')) {
          db.createObjectStore('memoryProviders', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('agents')) {
          db.createObjectStore('agents', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('sessions')) {
          db.createObjectStore('sessions', { keyPath: 'key' })
        }
        if (!db.objectStoreNames.contains('messages')) {
          db.createObjectStore('messages', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('shares')) {
          db.createObjectStore('shares', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('activities')) {
          db.createObjectStore('activities', { keyPath: 'id', autoIncrement: true })
        }
        if (!db.objectStoreNames.contains('workspaceFiles')) {
          db.createObjectStore('workspaceFiles', { keyPath: 'id', autoIncrement: true })
        }
      },
    })
  }
  return dbPromise
}

// 通用 CRUD helpers
export async function getAll<T>(store: string): Promise<T[]> {
  const db = await getDb()
  return db.getAll(store)
}

export async function getById<T>(store: string, id: number): Promise<T | undefined> {
  const db = await getDb()
  return db.get(store, id)
}

export async function add<T>(store: string, item: T): Promise<IDBValidKey> {
  const db = await getDb()
  return db.add(store, item)
}

export async function put<T>(store: string, item: T): Promise<IDBValidKey> {
  const db = await getDb()
  return db.put(store, item)
}

export async function del(store: string, id: number): Promise<void> {
  const db = await getDb()
  return db.delete(store, id)
}

export async function clear(store: string): Promise<void> {
  const db = await getDb()
  return db.clear(store)
}
```

- [ ] **创建 src/api/mock/fixtures/seed.ts**
```ts
import { getDb } from '../db'

export async function seedData() {
  const db = await getDb()

  // 只在空库时种数据
  const models = await db.getAll('models')
  if (models.length > 0) return

  // seed 模型
  const now = Date.now()
  await db.add('models', { ownerId: 'admin', name: '我的千问', type: 'dashscope', props: { apiKey: 'sk-xxxx1234', baseUrl: 'https://dashscope.aliyuncs.com', modelName: 'qwen-max' }, createdAt: now, updatedAt: now })
  await db.add('models', { ownerId: 'admin', name: 'GPT-4o', type: 'openai', props: { apiKey: 'sk-yyyy5678', baseUrl: 'https://api.openai.com', modelName: 'gpt-4o' }, createdAt: now, updatedAt: now })
  await db.add('models', { ownerId: 'admin', name: 'Claude 3.5', type: 'anthropic', props: { apiKey: 'sk-zzzz9012', baseUrl: 'https://api.anthropic.com', modelName: 'claude-3-5-sonnet-20241022' }, createdAt: now, updatedAt: now })

  // seed MCP
  await db.add('mcpServers', { ownerId: 'admin', name: '本地文件系统', transport: 'stdio', props: { command: 'npx', args: ['-y', '@modelcontextprotocol/server-filesystem', '/tmp'] }, enabled: true, createdAt: now, updatedAt: now })
  await db.add('mcpServers', { ownerId: 'admin', name: 'GitHub', transport: 'sse', props: { url: 'https://api.github.com/mcp' }, enabled: true, createdAt: now, updatedAt: now })

  // seed 工具
  await db.add('myTools', { ownerId: 'admin', type: 'shell-cmd', props: { allowedCommands: ['ls', 'cat', 'pwd'] }, enabled: true, createdAt: now, updatedAt: now })
  await db.add('myTools', { ownerId: 'admin', type: 'read-file', props: { rootPath: '/workspace' }, enabled: true, createdAt: now, updatedAt: now })
  await db.add('myTools', { ownerId: 'admin', type: 'write-file', props: { rootPath: '/workspace' }, enabled: true, createdAt: now, updatedAt: now })
  await db.add('myTools', { ownerId: 'admin', type: 'plan-notebook', props: {}, enabled: true, createdAt: now, updatedAt: now })
  await db.add('myTools', { ownerId: 'admin', type: 'sub-agent', props: {}, enabled: false, createdAt: now, updatedAt: now })
  await db.add('myTools', { ownerId: 'admin', type: 'mcp-bridge', props: {}, enabled: true, createdAt: now, updatedAt: now })

  // seed 技能
  await db.add('mySkills', { ownerId: 'admin', name: 'git-flow', version: '1.0.0', props: { repo: 'https://github.com/example/git-flow' }, enabled: true, createdAt: now })
  await db.add('mySkills', { ownerId: 'admin', name: 'sql-query', version: '0.5.0', props: {}, enabled: true, createdAt: now })

  // seed memory
  await db.add('memoryProviders', { ownerId: 'admin', name: '我的 Mem0', type: 'mem0', props: { apiKey: 'mem0-xxxx' }, enabled: true, createdAt: now, updatedAt: now })

  // seed agents
  await db.add('agents', {
    ownerId: 'admin', agentId: 'agent_001', name: '编码助手', description: '帮你写代码、调试、重构，支持多种编程语言', icon: '💻', sysPrompt: '你是一个资深的软件开发工程师。帮助用户编写、调试和重构代码。', agentType: 'harness',
    defaultModelProviderId: 1, maxIters: 20, workspacePath: '/workspace/agent_001', toolSpecs: [{ type: 'shell-cmd', props: { allowedCommands: ['ls', 'cat', 'pwd'] } }, { type: 'read-file', props: { rootPath: '/workspace' } }], skillRefs: [{ repoId: 1, name: 'git-flow' }], hookSpecs: [{ type: 'logging' }], subagentRefs: [], skillRepositoryIds: [], sandboxMode: 'local', sandboxScope: 'AGENT', runAs: 'OWNER', forkOf: undefined,
    memoryProviderId: 1, memoryMode: 'SESSION', createdAt: now, updatedAt: now,
  })
  await db.add('agents', {
    ownerId: 'admin', agentId: 'agent_002', name: '数据分析师', description: 'SQL / Excel / Python 数据分析，生成可视化报告', icon: '📊', sysPrompt: '你是一个数据分析专家。帮助用户处理数据、分析趋势。', agentType: 'harness',
    defaultModelProviderId: 2, maxIters: 15, workspacePath: '/workspace/agent_002', toolSpecs: [{ type: 'read-file', props: { rootPath: '/workspace' } }], skillRefs: [], hookSpecs: [], subagentRefs: [], skillRepositoryIds: [], sandboxMode: 'local', sandboxScope: 'AGENT', runAs: 'OWNER',
    memoryProviderId: undefined, memoryMode: undefined, createdAt: now - 86400000, updatedAt: now - 86400000,
  })
  await db.add('agents', {
    ownerId: 'admin', agentId: 'agent_003', name: '写作助手', description: '公文、报告、邮件起草，中英文润色和翻译', icon: '📝', sysPrompt: '你是一个专业的写作助理。帮助用户撰写和润色各种文档。', agentType: 'harness',
    defaultModelProviderId: 3, maxIters: 10, workspacePath: '/workspace/agent_003', toolSpecs: [], skillRefs: [], hookSpecs: [], subagentRefs: [], skillRepositoryIds: [], sandboxMode: 'local', sandboxScope: 'AGENT', runAs: 'OWNER',
    memoryProviderId: undefined, memoryMode: undefined, createdAt: now - 172800000, updatedAt: now - 172800000,
  })

  // seed sessions
  await db.add('sessions', { key: 'sess_001', agentId: 'agent_001', messageCount: 12, createdAt: now, updatedAt: now })
  await db.add('sessions', { key: 'sess_002', agentId: 'agent_001', messageCount: 5, createdAt: now - 3600000, updatedAt: now - 3600000 })
  await db.add('sessions', { key: 'sess_003', agentId: 'agent_002', messageCount: 8, createdAt: now - 7200000, updatedAt: now - 7200000 })
  await db.add('sessions', { key: 'sess_004', agentId: 'agent_002', messageCount: 3, createdAt: now - 86400000, updatedAt: now - 86400000 })
  await db.add('sessions', { key: 'sess_005', agentId: 'agent_003', messageCount: 15, createdAt: now - 43200000, updatedAt: now - 43200000 })

  // seed messages for sess_001
  await db.add('messages', { id: 'msg_001', sessionKey: 'sess_001', role: 'user', text: '帮我重构 util.py 文件', timestamp: now - 300000, toolCalls: undefined })
  await db.add('messages', { id: 'msg_002', sessionKey: 'sess_001', role: 'assistant', text: '好的，我先看看 util.py 的内容。', timestamp: now - 295000, toolCalls: [{ callId: 'tc_001', toolName: 'read_file', status: 'done', args: '{"path":"util.py"}', result: 'def foo(): pass\n\ndef bar(x):\n    return x * 2\n' }] })
  await db.add('messages', { id: 'msg_003', sessionKey: 'sess_001', role: 'assistant', text: '这是重构后的版本：\n\n```python\ndef foo():\n    """..."""\n    pass\n\n\ndef bar(x: int) -> int:\n    return x * 2\n```\n\n主要改动：添加了类型注解和 docstring。', timestamp: now - 290000, toolCalls: [{ callId: 'tc_002', toolName: 'write_file', status: 'done', args: '{"path":"util.py"}', result: 'Written 3 lines' }] })
  await db.add('messages', { id: 'msg_004', sessionKey: 'sess_001', role: 'user', text: '加一个函数来计算平均值', timestamp: now - 280000, toolCalls: undefined })

  // seed shares
  await db.add('shares', { agentDefId: 1, granteeType: 'USER', granteeId: 'user_bob', tier: 'RUN', createdBy: 'admin', createdAt: now })
  await db.add('shares', { agentDefId: 1, granteeType: 'USER', granteeId: 'user_alice', tier: 'CLONE', createdBy: 'admin', createdAt: now })

  // seed activities
  await db.add('activities', { agentDefId: 1, timestamp: now - 60000, action: 'chat', detail: '用户发送了消息' })
  await db.add('activities', { agentDefId: 1, timestamp: now - 120000, action: 'tool_call', detail: '调用了 read_file' })
  await db.add('activities', { agentDefId: 2, timestamp: now - 300000, action: 'chat', detail: '用户要求分析销售数据' })

  // seed workspace files
  await db.add('workspaceFiles', { agentDefId: 1, name: 'util.py', path: '/util.py', type: 'file', content: 'def foo():\n    pass\n' })
  await db.add('workspaceFiles', { agentDefId: 1, name: 'README.md', path: '/README.md', type: 'file', content: '# My Agent\n\nThis agent helps with coding tasks.' })
  await db.add('workspaceFiles', { agentDefId: 1, name: 'src', path: '/src', type: 'directory' })
  await db.add('workspaceFiles', { agentDefId: 1, name: 'main.py', path: '/src/main.py', type: 'file', content: 'print("hello")\n' })
}
```

- [ ] **创建 src/api/mock/handlers/authHandler.ts**
```ts
import { http, HttpResponse, delay } from 'msw'
import type { User } from '@/types'

// 内存中存一个当前用户
let currentUser: User = { userId: 'admin', username: 'admin', createdAt: 1718000000000 }

export const authHandlers = [
  // POST /api/auth/login
  http.post('/api/auth/login', async ({ request }) => {
    await delay(300)
    const body = (await request.json()) as { username?: string; password?: string }
    if (body?.username === 'admin' && body?.password === 'admin') {
      return HttpResponse.json({ token: 'mock-sa-token', userId: 'admin', username: 'admin' }, {
        headers: { 'Set-Cookie': 'satoken=mock-sa-token; Path=/; HttpOnly' },
      })
    }
    return HttpResponse.json({ message: '用户名或密码错误' }, { status: 401 })
  }),

  // POST /api/auth/logout
  http.post('/api/auth/logout', async () => {
    await delay(100)
    return new Response(null, { status: 200 })
  }),

  // GET /api/auth/me
  http.get('/api/auth/me', async () => {
    await delay(100)
    return HttpResponse.json(currentUser)
  }),

  // POST /api/user/change-password
  http.post('/api/user/change-password', async ({ request }) => {
    await delay(200)
    const body = (await request.json()) as Record<string, string>
    if (body.currentPassword !== 'admin') {
      return HttpResponse.json({ message: '当前密码错误' }, { status: 400 })
    }
    return HttpResponse.json({ message: '密码修改成功' })
  }),
]
```

- [ ] **创建 src/api/mock/handlers/resourceHandler.ts**
```ts
import { http, HttpResponse, delay } from 'msw'
import { getAll, getById, add, put, del } from '../db'

const STORE_MAP: Record<string, string> = {
  models: 'models',
  'mcp-servers': 'mcpServers',
  'my-skills': 'mySkills',
  tools: 'myTools',
  'memory-providers': 'memoryProviders',
  'skill-marketplaces': 'skillMarketplaces',
  'skill-repositories': 'skillRepositories',
}

export function createResourceHandlers(prefix: string, storeName: string) {
  const dbStore = STORE_MAP[storeName] || storeName
  return [
    // GET list
    http.get(`${prefix}`, async () => {
      await delay(150)
      const data = await getAll(dbStore)
      return HttpResponse.json(data)
    }),

    // GET by id
    http.get(`${prefix}/:id`, async ({ params }) => {
      await delay(100)
      const item = await getById(dbStore, Number(params.id))
      if (!item) return HttpResponse.json({ message: 'not found' }, { status: 404 })
      return HttpResponse.json(item)
    }),

    // POST create
    http.post(`${prefix}`, async ({ request }) => {
      await delay(200)
      const body = await request.json()
      const now = Date.now()
      const newItem = { ...body as Record<string, unknown>, ownerId: 'admin', createdAt: now, updatedAt: now, id: undefined }
      const id = await add(dbStore, newItem)
      const saved = await getById(dbStore, id as number)
      return HttpResponse.json(saved, { status: 201 })
    }),

    // PUT update
    http.put(`${prefix}/:id`, async ({ params, request }) => {
      await delay(200)
      const body = await request.json() as Record<string, unknown>
      const existing = await getById(dbStore, Number(params.id))
      if (!existing) return HttpResponse.json({ message: 'not found' }, { status: 404 })
      const updated = { ...existing as Record<string, unknown>, ...body, updatedAt: Date.now() }
      await put(dbStore, updated)
      return HttpResponse.json(updated)
    }),

    // PATCH (for toggle)
    http.patch(`${prefix}/:id`, async ({ params, request }) => {
      await delay(150)
      const body = await request.json() as Record<string, unknown>
      const existing = await getById(dbStore, Number(params.id))
      if (!existing) return HttpResponse.json({ message: 'not found' }, { status: 404 })
      const updated = { ...existing as Record<string, unknown>, ...body, updatedAt: Date.now() }
      await put(dbStore, updated)
      return HttpResponse.json(updated)
    }),

    // DELETE
    http.delete(`${prefix}/:id`, async ({ params }) => {
      await delay(150)
      await del(dbStore, Number(params.id))
      return new Response(null, { status: 204 })
    }),
  ]
}

export const resourceHandlers = [
  ...createResourceHandlers('/api/models', 'models'),
  ...createResourceHandlers('/api/mcp-servers', 'mcpServers'),
  ...createResourceHandlers('/api/my-skills', 'mySkills'),
  ...createResourceHandlers('/api/tools', 'tools'),
  ...createResourceHandlers('/api/memory-providers', 'memoryProviders'),
  ...createResourceHandlers('/api/skill-marketplaces', 'skillMarketplaces'),
  ...createResourceHandlers('/api/skill-repositories', 'skillRepositories'),
]
```

- [ ] **创建 src/api/mock/handlers/agentHandler.ts**
```ts
import { http, HttpResponse, delay } from 'msw'
import { getAll, getById, add, put, del } from '../db'
import type { AgentDetail, AgentSummary, AgentShare, ActivityEntry } from '@/types'

export const agentHandlers = [
  // GET /api/agents
  http.get('/api/agents', async () => {
    await delay(200)
    const agents = await getAll<AgentDetail>('agents')
    const models = await getAll<any>('models')
    const modelMap = new Map(models.map(m => [m.id, m.name || m.type]))
    const summaries: AgentSummary[] = agents.map(a => ({
      id: a.id as number,
      agentId: a.agentId,
      ownerId: a.ownerId,
      name: a.name,
      description: a.description,
      icon: a.icon,
      agentType: a.agentType,
      defaultModelProviderId: a.defaultModelProviderId,
      defaultModelName: modelMap.get(a.defaultModelProviderId) || '未知',
      toolCount: (a.toolSpecs || []).length,
      sessionCount: 0,
      createdAt: a.createdAt,
      updatedAt: a.updatedAt,
    }))
    return HttpResponse.json(summaries)
  }),

  // GET /api/agents/:id
  http.get('/api/agents/:id', async ({ params }) => {
    await delay(150)
    const agents = await getAll<AgentDetail>('agents')
    const agent = agents.find(a => a.id === Number(params.id) || a.agentId === params.id)
    if (!agent) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    return HttpResponse.json(agent)
  }),

  // POST /api/agents
  http.post('/api/agents', async ({ request }) => {
    await delay(300)
    const body = await request.json() as Partial<AgentDetail>
    const now = Date.now()
    const newAgent: AgentDetail = {
      id: 0, agentId: `agent_${String(now).slice(-6)}`, ownerId: 'admin',
      name: body.name || '未命名 Agent', description: body.description || '', icon: body.icon || '🤖',
      sysPrompt: body.sysPrompt || '', agentType: body.agentType || 'harness',
      defaultModelProviderId: body.defaultModelProviderId || 1, maxIters: body.maxIters || 20,
      workspacePath: body.workspacePath || `/workspace/agent_${String(now).slice(-6)}`,
      toolSpecs: body.toolSpecs || [], skillRefs: body.skillRefs || [], hookSpecs: body.hookSpecs || [],
      subagentRefs: body.subagentRefs || [], skillRepositoryIds: body.skillRepositoryIds || [],
      sandboxMode: body.sandboxMode || 'local', sandboxScope: body.sandboxScope || 'AGENT', runAs: body.runAs || 'OWNER',
      forkOf: body.forkOf, memoryProviderId: body.memoryProviderId, memoryMode: body.memoryMode,
      createdAt: now, updatedAt: now,
    }
    const id = await add('agents', newAgent)
    const saved = await getById<AgentDetail>('agents', id as number)
    return HttpResponse.json(saved, { status: 201 })
  }),

  // PUT /api/agents/:id
  http.put('/api/agents/:id', async ({ params, request }) => {
    await delay(200)
    const body = await request.json() as Partial<AgentDetail>
    const agents = await getAll<AgentDetail>('agents')
    const idx = agents.findIndex(a => a.id === Number(params.id))
    if (idx < 0) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    const updated = { ...agents[idx], ...body, id: agents[idx].id, agentId: agents[idx].agentId, ownerId: agents[idx].ownerId, updatedAt: Date.now() }
    await put('agents', updated)
    return HttpResponse.json(updated)
  }),

  // DELETE /api/agents/:id
  http.delete('/api/agents/:id', async ({ params }) => {
    await delay(200)
    await del('agents', Number(params.id))
    return new Response(null, { status: 204 })
  }),

  // POST /api/agents/:id/clone
  http.post('/api/agents/:id/clone', async ({ params }) => {
    await delay(300)
    const agents = await getAll<AgentDetail>('agents')
    const original = agents.find(a => a.id === Number(params.id))
    if (!original) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    const now = Date.now()
    const clone: AgentDetail = {
      ...original, id: 0, agentId: `agent_${String(now).slice(-6)}`, name: `${original.name} (Clone)`,
      forkOf: original.agentId, createdAt: now, updatedAt: now,
    }
    const id = await add('agents', clone)
    const saved = await getById<AgentDetail>('agents', id as number)
    return HttpResponse.json(saved, { status: 201 })
  }),

  // GET /api/agents/:id/shares
  http.get('/api/agents/:id/shares', async ({ params }) => {
    await delay(100)
    const allShares = await getAll<AgentShare>('shares')
    const agentId = Number(params.id) || (await getAll<AgentDetail>('agents')).find(a => a.agentId === params.id)?.id
    return HttpResponse.json(allShares.filter(s => s.agentDefId === agentId))
  }),

  // POST /api/agents/:id/shares
  http.post('/api/agents/:id/shares', async ({ params, request }) => {
    await delay(200)
    const body = await request.json() as Partial<AgentShare>
    const newShare = { ...body, agentDefId: Number(params.id), createdBy: 'admin', createdAt: Date.now() }
    const id = await add('shares', newShare)
    return HttpResponse.json({ ...newShare, id }, { status: 201 })
  }),

  // DELETE /api/agents/:id/shares/:shareId
  http.delete('/api/agents/:id/shares/:shareId', async ({ params }) => {
    await delay(150)
    await del('shares', Number(params.shareId))
    return new Response(null, { status: 204 })
  }),

  // GET /api/agents/:id/activity
  http.get('/api/agents/:id/activity', async ({ params }) => {
    await delay(100)
    const all = await getAll<any>('activities')
    const activities = all.filter(a => a.agentDefId === Number(params.id)).map(a => ({
      timestamp: a.timestamp, action: a.action, detail: a.detail,
    }))
    return HttpResponse.json(activities)
  }),

  // GET /api/agents/:id/workspace
  http.get('/api/agents/:id/workspace', async ({ params }) => {
    await delay(100)
    const all = await getAll<any>('workspaceFiles')
    const files = all.filter(f => f.agentDefId === Number(params.id))
    return HttpResponse.json(files)
  }),

  // GET /api/agents/:id/workspace/file?path=xxx
  http.get('/api/agents/:id/workspace/file', async ({ request, params }) => {
    await delay(100)
    const url = new URL(request.url)
    const filePath = url.searchParams.get('path')
    const all = await getAll<any>('workspaceFiles')
    const file = all.find(f => f.agentDefId === Number(params.id) && f.path === filePath)
    if (!file) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    return HttpResponse.json(file)
  }),

  // PUT /api/agents/:id/workspace/file
  http.put('/api/agents/:id/workspace/file', async ({ request, params }) => {
    await delay(200)
    const body = await request.json() as any
    const all = await getAll<any>('workspaceFiles')
    const idx = all.findIndex(f => f.agentDefId === Number(params.id) && f.path === body.path)
    if (idx >= 0) {
      all[idx].content = body.content
      await put('workspaceFiles', all[idx])
      return HttpResponse.json(all[idx])
    }
    return HttpResponse.json({ message: 'not found' }, { status: 404 })
  }),

  // GET /api/agents/:id/sessions/inbox
  http.get('/api/agents/:id/sessions/inbox', async ({ params }) => {
    await delay(100)
    const all = await getAll<any>('sessions')
    const agent = (await getAll<AgentDetail>('agents')).find(a => a.id === Number(params.id))
    const agentSessions = agent ? all.filter(s => s.agentId === agent.agentId) : []
    return HttpResponse.json(agentSessions)
  }),

  // GET /api/agents/:id/sessions/:key
  http.get('/api/agents/:id/sessions/:key', async ({ params }) => {
    await delay(100)
    const session = await getById<any>('sessions', params.key as string)
    if (!session) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    const allMessages = await getAll<any>('messages')
    const messages = allMessages.filter(m => m.sessionKey === params.key)
    return HttpResponse.json({ session, messages })
  }),

  // DELETE /api/agents/:id/sessions/:key
  http.delete('/api/agents/:id/sessions/:key', async ({ params }) => {
    await delay(150)
    await del('sessions', params.key as string)
    return new Response(null, { status: 204 })
  }),

  // POST /api/agents/:id/sessions/:key/reset
  http.post('/api/agents/:id/sessions/:key/reset', async () => {
    await delay(200)
    return HttpResponse.json({ message: 'session reset' })
  }),

  // PATCH /api/agents/:id/sessions/:key/read
  http.patch('/api/agents/:id/sessions/:key/read', async () => {
    await delay(100)
    return new Response(null, { status: 200 })
  }),
]
```

- [ ] **创建 src/api/mock/handlers/chatHandler.ts**
```ts
import { http, HttpResponse, delay } from 'msw'
import { getAll } from '../db'
import type { AgentDetail } from '@/types'

// SSE 模拟数据 — 模拟 Agent 回复
const SAMPLE_REPLIES = [
  { text: '好的，我来看看这个问题。\n\n首先，我建议从以下几个方面入手：\n\n1. **需求分析** — 确认核心功能\n2. **架构设计** — 选择合适的技术方案\n3. **实现** — 逐步编码\n\n需要我详细展开哪部分？', toolCalls: [] },
  { text: '让我先查看一下相关文件。', toolCalls: [{ callId: 'tc_001', toolName: 'read_file', status: 'done' as const, args: '{"path": "src/main.ts"}', result: 'The quick brown fox...' }] },
  { text: '根据分析，这是重构后的代码：\n\n```typescript\nfunction calculateTotal(items: number[]): number {\n  return items.reduce((sum, item) => sum + item, 0)\n}\n```\n\n主要改进：\n- 添加了类型注解\n- 使用 `reduce` 替代循环\n- 更加函数式', toolCalls: [{ callId: 'tc_002', toolName: 'write_file', status: 'done' as const, args: '{"path": "src/utils.ts"}', result: 'Written 3 lines' }] },
  { text: '抱歉，我没有权限执行这个操作。请跟管理员确认。', toolCalls: [{ callId: 'tc_003', toolName: 'shell-cmd', status: 'error' as const, args: '{"cmd": "rm -rf /"}', result: 'Error: Permission denied' }] },
]

// 模拟 SSE 流式输出（走 chunked encoding）
http.post('/api/agents/:id/chat/stream', async () => {
  // MSW 不能完美模拟 SSE，这里用自定义 Response + ReadableStream
  const reply = SAMPLE_REPLIES[Math.floor(Math.random() * SAMPLE_REPLIES.length)]
  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    async start(controller) {
      // 流式的 token：逐个字符吐
      for (let i = 0; i < reply.text.length; i++) {
        const chunk = reply.text[i]
        const payload = JSON.stringify({ delta: chunk, index: 0 })
        controller.enqueue(encoder.encode(`event: token\ndata: ${payload}\n\n`))
        await new Promise(r => setTimeout(r, 15 + Math.random() * 20))
      }
      // tool_call 事件
      for (const tc of reply.toolCalls) {
        controller.enqueue(encoder.encode(`event: tool_call\ndata: ${JSON.stringify({ callId: tc.callId, toolName: tc.toolName, arguments: tc.args })}\n\n`))
        await new Promise(r => setTimeout(r, 300))
        controller.enqueue(encoder.encode(`event: tool_result\ndata: ${JSON.stringify({ callId: tc.callId, result: tc.result, isError: tc.status === 'error' })}\n\n`))
        await new Promise(r => setTimeout(r, 200))
      }
      // done
      controller.enqueue(encoder.encode(`event: done\ndata: {}\n\n`))
      controller.close()
    },
  })

  return new HttpResponse(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
  })
})

export const chatHandlers = [
  // GET /api/agents/:id/chat/session
  http.get('/api/agents/:id/chat/session', async ({ params }) => {
    await delay(100)
    const agents = await getAll<AgentDetail>('agents')
    const agent = agents.find(a => a.id === Number(params.id))
    if (!agent) return HttpResponse.json({ message: 'not found' }, { status: 404 })
    const allSessions = await getAll<any>('sessions')
    const agentSessions = allSessions.filter(s => s.agentId === agent.agentId)
    const current = agentSessions[0]
    return HttpResponse.json({ sessionKey: current?.key || 'sess_new', sessionKeyNew: 'sess_new' })
  }),

  // POST /api/agents/:id/chat/send (同步)
  http.post('/api/agents/:id/chat/send', async () => {
    await delay(800)
    return HttpResponse.json({
      role: 'assistant',
      text: '好的，我来处理你的请求。\n\n这是模拟回复（同步模式）。',
    })
  }),

  // POST /api/agents/:id/chat/stream (SSE)
  // 在文件顶部定义——上面的 http.post 作为 chatHandlers 数组的一部分
]

// 重新导出 chatHandlers（上面的 http.post 会在数组里）
// 注意：上面的 http.post 定义在文件作用域中，需要在数组里引用
// 我们用变量引用它
export { chatHandlers as _chatHandlers }
```

- [ ] **修正：在 chatHandler.ts 末尾将流式 handler 加入数组**
```ts
// 追加到 chatHandlers
chatHandlers.push(
  http.post('/api/agents/:id/chat/stream', async () => {
    const reply = SAMPLE_REPLIES[Math.floor(Math.random() * SAMPLE_REPLIES.length)]
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      async start(controller) {
        // 流式 token
        for (let i = 0; i < reply.text.length; i++) {
          const payload = JSON.stringify({ delta: reply.text[i], index: 0 })
          controller.enqueue(encoder.encode(`event: token\ndata: ${payload}\n\n`))
          await new Promise(r => setTimeout(r, 15 + Math.random() * 20))
        }
        // tool calls
        for (const tc of reply.toolCalls) {
          controller.enqueue(encoder.encode(`event: tool_call\ndata: ${JSON.stringify({ callId: tc.callId, toolName: tc.toolName, arguments: tc.args })}\n\n`))
          await new Promise(r => setTimeout(r, 300))
          controller.enqueue(encoder.encode(`event: tool_result\ndata: ${JSON.stringify({ callId: tc.callId, result: tc.result, isError: tc.status === 'error' })}\n\n`))
          await new Promise(r => setTimeout(r, 200))
        }
        controller.enqueue(encoder.encode(`event: done\ndata: {}\n\n`))
        controller.close()
      },
    })
    return new HttpResponse(stream, {
      headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' },
    })
  })
)
```

- [ ] **创建 src/api/mock/handlers/factoryHandler.ts**
```ts
import { http, HttpResponse, delay } from 'msw'
import type { ProviderMeta, FactoryTypesResponse } from '@/types'

const MODEL_TYPES: ProviderMeta[] = [
  { type: 'dashscope', displayName: 'DashScope (阿里云)', description: '阿里云通义千问系列', schema: { type: 'object', title: 'DashScope 配置', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password', description: 'DashScope API Key' }, baseUrl: { type: 'string', title: 'Base URL', default: 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation' }, modelName: { type: 'string', title: '模型名', default: 'qwen-max' } }, required: ['apiKey'] } },
  { type: 'openai', displayName: 'OpenAI', description: 'OpenAI GPT 系列 + 兼容 API', schema: { type: 'object', title: 'OpenAI 配置', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' }, baseUrl: { type: 'string', title: 'Base URL', default: 'https://api.openai.com/v1' }, modelName: { type: 'string', title: '模型名', default: 'gpt-4o' } }, required: ['apiKey'] } },
  { type: 'anthropic', displayName: 'Anthropic', description: 'Claude 系列', schema: { type: 'object', title: 'Anthropic 配置', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' }, baseUrl: { type: 'string', title: 'Base URL', default: 'https://api.anthropic.com/v1' }, modelName: { type: 'string', title: '模型名', default: 'claude-3-5-sonnet-20241022' } }, required: ['apiKey'] } },
  { type: 'gemini', displayName: 'Gemini', description: 'Google Gemini 系列', schema: { type: 'object', title: 'Gemini 配置', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' }, modelName: { type: 'string', title: '模型名', default: 'gemini-pro' } }, required: ['apiKey'] } },
  { type: 'ollama', displayName: 'Ollama', description: '本地 Ollama', schema: { type: 'object', title: 'Ollama 配置', properties: { baseUrl: { type: 'string', title: 'Base URL', default: 'http://localhost:11434' }, modelName: { type: 'string', title: '模型名', default: 'llama3' } }, required: [] } },
]

const TOOL_TYPES: ProviderMeta[] = [
  { type: 'shell-cmd', displayName: 'Shell 命令', description: '安全执行 shell 命令', schema: { type: 'object', properties: { allowedCommands: { type: 'array', title: '允许的命令', items: { type: 'string' } } }, required: [] } },
  { type: 'read-file', displayName: 'Read File', description: '读取文件', schema: { type: 'object', properties: { rootPath: { type: 'string', title: '根路径', default: '/workspace' } }, required: [] } },
  { type: 'write-file', displayName: 'Write File', description: '写入文件', schema: { type: 'object', properties: { rootPath: { type: 'string', title: '根路径', default: '/workspace' } }, required: [] } },
  { type: 'plan-notebook', displayName: 'Plan Notebook', description: '任务规划', schema: { type: 'object', properties: {}, required: [] } },
  { type: 'sub-agent', displayName: 'Sub Agent', description: '子智能体', schema: { type: 'object', properties: {}, required: [] } },
  { type: 'mcp-bridge', displayName: 'MCP Bridge', description: 'MCP 桥接', schema: { type: 'object', properties: {}, required: [] } },
]

const MEMORY_TYPES: ProviderMeta[] = [
  { type: 'mem0', displayName: 'Mem0', description: 'Mem0 长期记忆', schema: { type: 'object', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' } }, required: ['apiKey'] } },
  { type: 'reme', displayName: 'Reme', description: 'Reme 记忆', schema: { type: 'object', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' } }, required: ['apiKey'] } },
  { type: 'bailian', displayName: 'Bailian', description: '阿里云百炼', schema: { type: 'object', properties: { apiKey: { type: 'string', title: 'API Key', format: 'password' }, workspaceId: { type: 'string', title: 'Workspace ID' } }, required: ['apiKey'] } },
]

export const factoryHandlers = [
  http.get('/api/factories/model-types', async () => { await delay(100); return HttpResponse.json(MODEL_TYPES) }),
  http.get('/api/factories/tool-types', async () => { await delay(100); return HttpResponse.json(TOOL_TYPES) }),
  http.get('/api/factories/memory-types', async () => { await delay(100); return HttpResponse.json(MEMORY_TYPES) }),
  http.get('/api/factories/skill-repo-types', async () => { await delay(100); return HttpResponse.json([{ type: 'git', displayName: 'Git', description: 'Git 仓库', schema: { type: 'object', properties: { url: { type: 'string', title: 'Git URL' }, branch: { type: 'string', title: 'Branch', default: 'main' } }, required: ['url'] } }]) }),
  http.get('/api/factories/hook-types', async () => { await delay(100); return HttpResponse.json([{ type: 'logging', displayName: 'Logging', description: '日志', schema: { type: 'object', properties: {} } }, { type: 'tool-notification', displayName: 'Tool Notification', description: '工具通知', schema: { type: 'object', properties: {} } }, { type: 'audit-jsonl', displayName: 'Audit JSONL', description: '审计日志', schema: { type: 'object', properties: { path: { type: 'string', title: '日志路径', default: './audit.jsonl' } } } }, { type: 'tracing-otel', displayName: 'Tracing (OpenTelemetry)', description: '链路追踪', schema: { type: 'object', properties: { endpoint: { type: 'string', title: 'Endpoint' } } } }]) }),
  http.get('/api/factories/agent-types', async () => { await delay(100); return HttpResponse.json([{ type: 'harness', displayName: 'Harness', description: 'Harness 运行时', schema: { type: 'object', properties: {} } }, { type: 'react', displayName: 'ReAct', description: 'ReAct 循环', schema: { type: 'object', properties: {} } }]) }),
]

export { MODEL_TYPES, TOOL_TYPES, MEMORY_TYPES }
```

- [ ] **创建 src/api/mock/browser.ts**
```ts
import { setupWorker } from 'msw/browser'
import { authHandlers } from './handlers/authHandler'
import { resourceHandlers } from './handlers/resourceHandler'
import { agentHandlers } from './handlers/agentHandler'
import { chatHandlers } from './handlers/chatHandler'
import { factoryHandlers } from './handlers/factoryHandler'
import { handlerTemplates } from './handlers/templateHandler'

export const worker = setupWorker(
  ...authHandlers,
  ...resourceHandlers,
  ...agentHandlers,
  ...chatHandlers,
  ...factoryHandlers,
  ...handlerTemplates,
)
```

- [ ] **创建 src/api/mock/handlers/templateHandler.ts (最小 2 模板)**
```ts
import { http, HttpResponse, delay } from 'msw'

export const handlerTemplates = [
  http.get('/api/templates', async () => {
    await delay(100)
    return HttpResponse.json([
      { id: 1, name: '编码助手', description: '帮你编写和调试代码', icon: '💻' },
      { id: 2, name: '通用助手', description: '通用的 AI 助手', icon: '🤖' },
    ])
  }),
  http.get('/api/templates/:id', async ({ params }) => {
    await delay(100)
    return HttpResponse.json({ id: Number(params.id), name: '编码助手', description: '...', icon: '💻', config: {} })
  }),
]
```

- [ ] **更新 main.ts —— 启动 MSW + seed**
```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPersistedstate from 'pinia-plugin-persistedstate'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { worker } from './api/mock/browser'
import { seedData } from './api/mock/fixtures/seed'
import 'virtual:uno.css'
import '@unocss/reset/tailwind.css'

async function bootstrap() {
  // 启动 MSW worker
  await worker.start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  })

  // 种 seed 数据
  await seedData()

  const app = createApp(App)
  const pinia = createPinia()
  pinia.use(piniaPersistedstate)
  app.use(pinia).use(router).use(i18n)
  app.mount('#app')
}

bootstrap()
```

- [ ] **验证 MSW 能启动**
```bash
cd D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend
pnpm dev
# → 浏览器打开 http://localhost:5173
# → 控制台应有 "MSW: [MockServiceWorker] activated"
```

- [ ] **提交: MSW 设施 + seed + handler**
```bash
git add -A && git commit -m "feat(saton): MSW mock handlers + IndexedDB seed data"
```

---

## 任务 8：axios client + 拦截器

### 任务 8：axios 实例 + 401 拦截

**Files:**
- Create: `src/api/client.ts`

- [ ] **创建 src/api/client.ts**
```ts
import axios, { type AxiosInstance } from 'axios'
import router from '@/router'

const client: AxiosInstance = axios.create({
  baseURL: '/',
  withCredentials: true,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      // 401 跳登录
      const current = router.currentRoute.value.fullPath
      if (!current.startsWith('/login')) {
        router.push({ path: '/login', query: { redirect: current } })
      }
    }
    return Promise.reject(err)
  }
)

export default client
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): axios client with 401 interceptor"
```

---

## 任务 9：auth API + auth store

**Files:**
- Create: `src/api/auth.ts`
- Create: `src/stores/auth.ts`

- [ ] **创建 src/api/auth.ts**
```ts
import client from './client'
import type { User } from '@/types'

export interface LoginReq { username: string; password: string }
export interface LoginRes { token: string; userId: string; username: string }

export function login(req: LoginReq) {
  return client.post<LoginRes>('/api/auth/login', req).then(r => r.data)
}

export function logout() {
  return client.post('/api/auth/logout').then(r => r.data)
}

export function getMe() {
  return client.get<User>('/api/auth/me').then(r => r.data)
}

export function changePassword(currentPassword: string, newPassword: string) {
  return client.post('/api/user/change-password', { currentPassword, newPassword }).then(r => r.data)
}
```

- [ ] **创建 src/stores/auth.ts**
```ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.token
    user.value = { userId: res.userId, username: res.username, createdAt: Date.now() }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // 忽略 logout 错误
    }
    token.value = null
    user.value = null
  }

  async function fetchMe() {
    if (!token.value) return
    try {
      user.value = await authApi.getMe()
    } catch (e) {
      token.value = null
      user.value = null
    }
  }

  return { user, token, isLoggedIn, login, logout, fetchMe }
}, {
  persist: {
    storage: localStorage,
    pick: ['token', 'user'],
  },
})
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): auth API + auth store with persistence"
```

---

## 任务 10：router + 鉴权守卫

**Files:**
- Create: `src/router/index.ts`
- Create: `src/router/routes.ts`

- [ ] **创建 src/router/routes.ts**
```ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
      { path: 'agents', name: 'AgentList', component: () => import('@/views/agents/AgentListView.vue') },
      { path: 'agents/new', name: 'AgentNew', component: () => import('@/views/agents/AgentSettingsView.vue') },
      { path: 'agents/:id/chat', name: 'AgentChat', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'agents/:id/chat/:sessionKey', name: 'AgentChatSession', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'agents/:id/settings', redirect: to => ({ path: `/agents/${to.params.id}/settings/basic` }) },
      { path: 'agents/:id/settings/:tab', name: 'AgentSettings', component: () => import('@/views/agents/AgentSettingsView.vue') },
      { path: 'models', name: 'Models', component: () => import('@/views/settings/ModelsView.vue') },
      { path: 'tools', name: 'Tools', component: () => import('@/views/tools/ToolsView.vue') },
      { path: 'memory', name: 'Memory', component: () => import('@/views/settings/MemoryView.vue') },
      { path: 'profile', name: 'Profile', component: () => import('@/views/profile/ProfileView.vue') },
    ],
  },
  {
    path: '/:catchAll(.*)*',
    name: 'NotFound',
    component: () => import('@/views/common/NotFoundView.vue'),
  },
]

export default routes
```

- [ ] **创建 src/router/index.ts**
```ts
import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta?.public) return true
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): router + auth guard"
```

---

## 任务 11：UI store (sidebar 折叠、抽屉状态)

**Files:**
- Create: `src/stores/ui.ts`

- [ ] **创建 src/stores/ui.ts**
```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const activeDrawer = ref<string | null>(null)  // 当前打开的聊天右侧抽屉名

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function openDrawer(name: string) {
    activeDrawer.value = activeDrawer.value === name ? null : name
  }

  function closeDrawer() {
    activeDrawer.value = null
  }

  return { sidebarCollapsed, activeDrawer, toggleSidebar, openDrawer, closeDrawer }
}, {
  persist: {
    storage: localStorage,
    pick: ['sidebarCollapsed'],
  },
})
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): ui store for sidebar and drawers"
```

---

## 任务 12：LoginView

**Files:**
- Create: `src/views/auth/LoginView.vue`

- [ ] **创建 src/views/auth/LoginView.vue**
```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NCard, NForm, NFormItem, NInput, NButton, useMessage } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()

const username = ref('admin')
const password = ref('admin')
const loading = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) {
    message.warning(t('common.required'))
    return
  }
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (e: any) {
    message.error(e?.response?.data?.message || t('auth.loginFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <NCard class="login-card" :title="t('auth.title')">
      <NForm @keyup.enter="handleLogin">
        <NFormItem :label="t('auth.username')">
          <NInput v-model:value="username" :placeholder="t('auth.username')" />
        </NFormItem>
        <NFormItem :label="t('auth.password')">
          <NInput v-model:value="password" type="password" show-password-on="click" :placeholder="t('auth.password')" />
        </NFormItem>
        <NButton type="primary" block :loading="loading" @click="handleLogin">
          {{ loading ? t('auth.loggingIn') : t('auth.login') }}
        </NButton>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--n-color);
}
.login-card {
  width: 360px;
}
</style>
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): login view"
```

---

## 任务 13：DashboardView (极简 hero)

**Files:**
- Create: `src/views/dashboard/DashboardView.vue`

- [ ] **创建 src/views/dashboard/DashboardView.vue**
```vue
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton } from 'naive-ui'

const { t } = useI18n()
const router = useRouter()

function goNewAgent() {
  router.push('/agents/new')
}

function goAgents() {
  router.push('/agents')
}
</script>

<template>
  <div class="dashboard">
    <h1 class="title">{{ t('dashboard.title') }}</h1>
    <p class="subtitle">{{ t('dashboard.subtitle') }}</p>
    <NButton type="primary" size="large" class="cta" @click="goNewAgent">
      + {{ t('dashboard.cta') }}
    </NButton>
    <div class="secondary">
      <a @click="goAgents">{{ t('dashboard.secondary') }}</a>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  gap: 24px;
}
.title {
  font-size: 48px;
  font-weight: 700;
  margin: 0;
}
.subtitle {
  font-size: 18px;
  opacity: 0.7;
  margin: 0;
}
.cta {
  font-size: 16px;
  padding: 0 32px;
  height: 48px;
  margin-top: 16px;
}
.secondary {
  margin-top: 16px;
  font-size: 14px;
  opacity: 0.7;
}
.secondary a {
  cursor: pointer;
  text-decoration: underline;
}
.secondary a:hover {
  color: var(--n-color-primary);
}
</style>
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): dashboard view (hero)"
```

---

## 任务 14：NotFoundView + 占位 placeholder views

**Files:**
- Create: `src/views/common/NotFoundView.vue`
- Create: `src/views/common/PlaceholderView.vue`
- Create: `src/views/agents/AgentListView.vue` (占位)
- Create: `src/views/agents/AgentSettingsView.vue` (占位)
- Create: `src/views/chat/ChatView.vue` (占位)
- Create: `src/views/tools/ToolsView.vue` (占位)
- Create: `src/views/settings/ModelsView.vue` (占位)
- Create: `src/views/settings/MemoryView.vue` (占位)
- Create: `src/views/profile/ProfileView.vue` (占位)

- [ ] **创建 src/views/common/NotFoundView.vue**
```vue
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NResult } from 'naive-ui'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <NResult status="404" :title="t('notFound.title')" :description="t('notFound.message')">
      <template #footer>
        <NButton @click="router.push('/')">{{ t('notFound.back') }}</NButton>
      </template>
    </NResult>
  </div>
</template>

<style scoped>
.not-found {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
```

- [ ] **创建 src/views/common/PlaceholderView.vue** (供 F2-F8 替换前作 placeholder)
```vue
<script setup lang="ts">
import { useRoute } from 'vue-router'
import { NEmpty } from 'naive-ui'
const route = useRoute()
</script>

<template>
  <div class="placeholder">
    <NEmpty :description="`此页面将在 F2+ milestone 中实现 (${route.path})`" />
  </div>
</template>

<style scoped>
.placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
```

- [ ] **创建占位页 src/views/agents/AgentListView.vue**
```vue
<script setup lang="ts">
</script>
<template>
  <component :is="() => import('@/views/common/PlaceholderView.vue')" />
</template>
```

- [ ] **以同样模式创建：** AgentSettingsView.vue / ChatView.vue / ToolsView.vue / ModelsView.vue / MemoryView.vue / ProfileView.vue
每个文件内容都用 PlaceholderView 占位：
```vue
<template>
  <PlaceholderView />
</template>

<script setup lang="ts">
import PlaceholderView from '@/views/common/PlaceholderView.vue'
</script>
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): 404 + placeholder views for F2-F8"
```

---

## 任务 15：MainLayout (sidebar + 主区)

**Files:**
- Create: `src/layouts/MainLayout.vue`

- [ ] **创建 src/layouts/MainLayout.vue**
```vue
<script setup lang="ts">
import { computed, h } from 'vue'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NLayout, NLayoutSider, NLayoutContent,
  NMenu, NIcon, NPopover, NButton, NDivider, NSpace, useMessage,
  type MenuOption,
} from 'naive-ui'
import { Icon } from '@iconify/vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useTheme } from '@/composables/useTheme'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const auth = useAuthStore()
const ui = useUiStore()
const { isDark, toggleTheme } = useTheme()

// 当前激活的菜单（按路由顶层段）
const activeKey = computed(() => {
  const seg = route.path.split('/')[1]
  return seg || 'dashboard'
})

const menuOptions = computed<MenuOption[]>(() => [
  { label: t('sidebar.dashboard'), key: 'dashboard', icon: () => h(NIcon, null, { default: () => h(Icon, { icon: 'ph:gauge' }) }) },
  { label: t('sidebar.agents'),    key: 'agents',    icon: () => h(NIcon, null, { default: () => h(Icon, { icon: 'ph:robot' }) }) },
  { label: t('sidebar.models'),    key: 'models',    icon: () => h(NIcon, null, { default: () => h(Icon, { icon: 'ph:brain' }) }) },
  { label: t('sidebar.tools'),     key: 'tools',     icon: () => h(NIcon, null, { default: () => h(Icon, { icon: 'ph:wrench' }) }) },
  { label: t('sidebar.memory'),    key: 'memory',    icon: () => h(NIcon, null, { default: () => h(Icon, { icon: 'ph:database' }) }) },
])

function handleMenuSelect(key: string) {
  router.push('/' + key)
}

function toggleLocale() {
  const next = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = next
  localStorage.setItem('locale', next)
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

function gotoProfile() {
  router.push('/profile')
}
</script>

<template>
  <NLayout has-sider style="height: 100vh;">
    <NLayoutSider
      :collapsed="ui.sidebarCollapsed"
      collapse-mode="width"
      :collapsed-width="64"
      :width="240"
      :show-trigger="true"
      bordered
      @update:collapsed="(v) => ui.sidebarCollapsed = v"
    >
      <div class="logo-area">
        <Icon icon="ph:brain-bold" :width="28" />
        <span v-if="!ui.sidebarCollapsed" class="logo-text">AgentScope</span>
      </div>

      <NMenu
        :collapsed="ui.sidebarCollapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="activeKey"
        @update:value="handleMenuSelect"
      />

      <div class="sider-footer">
        <NDivider style="margin: 8px 0;" />
        <NSpace vertical size="small" align="center">
          <NButton text @click="toggleLocale">
            <Icon icon="ph:translate" :width="18" />
            <span v-if="!ui.sidebarCollapsed" style="margin-left: 8px;">{{ locale === 'zh-CN' ? '中' : 'EN' }}</span>
          </NButton>
          <NButton text @click="toggleTheme">
            <Icon :icon="isDark ? 'ph:moon' : 'ph:sun'" :width="18" />
          </NButton>

          <NPopover trigger="click" placement="top-end">
            <template #trigger>
              <NButton text>
                <Icon icon="ph:user-circle" :width="22" />
                <span v-if="!ui.sidebarCollapsed" style="margin-left: 8px;">{{ auth.user?.username }}</span>
              </NButton>
            </template>
            <NSpace vertical>
              <NButton text @click="gotoProfile">
                <Icon icon="ph:user" /> <span style="margin-left: 6px;">{{ t('auth.profile') }}</span>
              </NButton>
              <NButton text @click="handleLogout">
                <Icon icon="ph:sign-out" /> <span style="margin-left: 6px;">{{ t('auth.logout') }}</span>
              </NButton>
            </NSpace>
          </NPopover>
        </NSpace>
      </div>
    </NLayoutSider>

    <NLayoutContent>
      <router-view />
    </NLayoutContent>
  </NLayout>
</template>

<style scoped>
.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid var(--n-border-color);
}
.logo-text {
  font-weight: 600;
  font-size: 16px;
}
.sider-footer {
  position: absolute;
  bottom: 16px;
  left: 0;
  right: 0;
}
</style>
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): MainLayout with sidebar"
```

---

## 任务 16：注释加进 main.ts —— 启动时拉 me

刷新页面后，`auth.token` 从 localStorage 恢复，但需要拉一次 `/api/auth/me` 验证 token 仍有效。

- [ ] **修改 src/main.ts**
```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPersistedstate from 'pinia-plugin-persistedstate'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { worker } from './api/mock/browser'
import { seedData } from './api/mock/fixtures/seed'
import { useAuthStore } from './stores/auth'
import 'virtual:uno.css'
import '@unocss/reset/tailwind.css'

async function bootstrap() {
  // 启动 MSW worker
  await worker.start({
    onUnhandledRequest: 'bypass',
    quiet: true,
  })

  // 种 seed 数据
  await seedData()

  const app = createApp(App)
  const pinia = createPinia()
  pinia.use(piniaPersistedstate)
  app.use(pinia).use(router).use(i18n)

  // 启动时如有 token，拉一次 me
  const auth = useAuthStore(pinia)
  if (auth.token) {
    await auth.fetchMe().catch(() => {})
  }

  app.mount('#app')
}

bootstrap()
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): hydrate user on app start"
```

---

## 任务 17：全局 message provider

Naive UI 的 `useMessage()` 需要外层有 `NMessageProvider`，否则报警告。同理 `NDialog`、`NNotification`。

- [ ] **更新 src/App.vue**
```vue
<script setup lang="ts">
import { darkTheme, NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NLoadingBarProvider, zhCN, enUS, dateZhCN, dateEnUS } from 'naive-ui'
import { computed } from 'vue'
import { useTheme } from './composables/useTheme'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const { theme, themeOverrides } = useTheme()

const naiveLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))
const naiveDateLocale = computed(() => (locale.value === 'zh-CN' ? dateZhCN : dateEnUS))
</script>

<template>
  <NConfigProvider
    :theme="theme"
    :theme-overrides="themeOverrides"
    :locale="naiveLocale"
    :date-locale="naiveDateLocale"
  >
    <NLoadingBarProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <NMessageProvider>
            <router-view />
          </NMessageProvider>
        </NNotificationProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
```

- [ ] **提交**
```bash
git add -A && git commit -m "feat(saton): naive-ui providers in App.vue"
```

---

## 任务 18：F1 端到端验证 + 提交

- [ ] **启动 dev server**
```bash
cd D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\frontend
pnpm dev
```

- [ ] **浏览器逐项验证**：
  - [ ] `http://localhost:5173` 自动跳 `/login`
  - [ ] 浏览器 DevTools console 显示 "MSW activated"
  - [ ] DevTools Application > IndexedDB 看到 `agentscope-builder` DB + 多个 store + seed 数据
  - [ ] 用 `admin` / `admin` 登录成功 → 跳 `/dashboard`
  - [ ] Dashboard 显示 hero + 「+ 创建你的第一个 Agent」按钮
  - [ ] sidebar 5 项可点：Dashboard / Agent / 模型 / 工具 / 记忆
  - [ ] 点 Agent/模型/工具/记忆 → 显示「此页面将在 F2+ milestone 中实现」placeholder
  - [ ] sidebar 底部点 🌐 切换 → UI 切换中英文
  - [ ] sidebar 底部点 🌙/☀ → 切深浅主题
  - [ ] sidebar 底部点 👤 → popover 出现「个人资料 / 退出登录」
  - [ ] 点退出登录 → 跳 `/login`，token 已清
  - [ ] 重新登录 + 刷新页面 → 仍在登录态，无需重登
  - [ ] 错误密码登录 → 显示「用户名或密码错误」错误提示
  - [ ] 未登录直接访问 `/dashboard` → 跳 `/login?redirect=/dashboard`
  - [ ] 登录后跳回原 redirect 路径

- [ ] **typecheck 通过**
```bash
pnpm typecheck
```
Expected: 无错误

- [ ] **F1 完成提交**
```bash
git add -A && git commit -m "chore(saton): F1 done - skeleton with auth + sidebar"
```

---

## F1 完成总结

完成后形成的能力：
- ✅ pnpm 工程独立可启动
- ✅ MSW 拦截所有 `/api/*` + IndexedDB 持久化 + seed 数据
- ✅ 登录态 + 持久化 + 401 自动跳登录
- ✅ MainLayout（5 项 sidebar + 底部头像 popover + i18n + 主题切换）
- ✅ Dashboard hero 页
- ✅ 4 个占位页等待 F2-F8 替换

下一步：F2 资源页（模型 / 记忆 CRUD + JsonSchemaForm 通用组件 + factories store）。

## Self-Review

- [x] 每步含完整代码（无 TODO / TBD）
- [x] 路径精确（src/api/mock/handlers/agentHandler.ts 等）
- [x] 路由命名与 view 文件名一致
- [x] auth store 的 `login/logout/fetchMe` 跟 router guard 中的 `isLoggedIn` 名字一致
- [x] MSW handler URL 与 axios 调用 URL 完全匹配（`/api/auth/login` 等）
- [x] IndexedDB store 名（'models', 'mcpServers', ...）在 db/index.ts、seed.ts、resourceHandler.ts STORE_MAP 中保持一致
- [x] 任务 14 占位页 + 任务 15 MainLayout 中的 router-view 配合正确