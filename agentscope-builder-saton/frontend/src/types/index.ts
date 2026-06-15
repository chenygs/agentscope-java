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
  name: string
  type: string
  props: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

export interface SkillRepository {
  id: number
  name: string
  type: string
  props: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

export interface SkillMarketplace {
  id: number
  marketplaceId: string
  type: string
  props: Record<string, unknown>
  createdAt: number
  updatedAt: number
}

// ── Agent ──
export interface ToolSpec {
  type: string
  props?: Record<string, unknown>
}

export interface SkillRepoSpec {
  type: string
  props?: Record<string, unknown>
}

export interface WorkspaceSkill {
  name: string
  description: string
  source: string
  installTime: number
}

export interface ChatSendReq {
  message: string
  overrideModelProviderId?: number
  sessionKey?: string
}

export interface MiddlewareSpec {
  type: string
  props?: Record<string, unknown>
}

export interface AgentSummary {
  id: number
  agentId: string
  ownerId: string
  name: string
  description: string
  agentType: 'REACT' | 'HARNESS'
  defaultModelProviderId: number
  maxIters: number
  createdAt: number
  updatedAt: number
}

export interface AgentDetail {
  id: number
  agentId: string
  ownerId: string
  name: string
  description: string
  sysPrompt: string
  agentType: 'REACT' | 'HARNESS'
  defaultModelProviderId: number
  maxIters: number
  toolSpecs: ToolSpec[]
  skillRepositories: SkillRepoSpec[]
  middlewareSpecs: MiddlewareSpec[]
  subagentRefs: string[]
  createdAt: number
  updatedAt: number
}

export interface AgentUpsertReq {
  agentId: string
  name: string
  description?: string
  sysPrompt?: string
  agentType: 'REACT' | 'HARNESS'
  defaultModelProviderId: number
  maxIters?: number
  toolSpecs?: ToolSpec[]
  skillRepositories?: SkillRepoSpec[]
  middlewareSpecs?: MiddlewareSpec[]
  subagentRefs?: string[]
}

export interface AgentShare {
  id: number
  agentDefId: number
  granteeId: string
  tier: 'EDIT' | 'RUN' | 'CLONE'
  createdBy: string
  createdAt: number
}

// ── Session / Chat ──
export interface Session {
  /** 会话标识，对应后端 sessionKey；同 agent 下唯一 */
  sessionKey: string
  /** 最近活跃毫秒时间戳；当前 Redis 后端固定返回 0 */
  lastActiveAt: number
  /** 会话标题，取自首条 user 消息文本（未截断），后端可能返回空串；前端按 UI 宽度自行截断 */
  title: string
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
  secret?: boolean
  properties?: Record<string, JsonSchemaProperty>
  items?: JsonSchemaProperty
  required?: string[]
}

// ── Resource Upsert Requests ──
export interface ModelProviderUpsertReq {
  name: string
  type: string
  props: Record<string, unknown>
}

export interface McpServerUpsertReq {
  name: string
  type: string
  props: Record<string, unknown>
}

export interface SkillRepositoryUpsertReq {
  name: string
  type: string
  props: Record<string, unknown>
}

export interface SkillMarketplaceUpsertReq {
  marketplaceId: string
  type: string
  props: Record<string, unknown>
}

// ── Activity ──
export interface ActivityEntry {
  timestamp: number
  action: string
  detail: string
}

// ── Workspace ──
/** 后端 FileNodeVO: type 是 "file" | "dir",size 单位 byte */
export interface FileNode {
  name: string
  path: string
  type: 'file' | 'dir'
  size: number
}

export interface WorkspaceSummary {
  /** 工作区绝对路径(后端 Path.toString,Windows 下含反斜杠) */
  root: string
  /** 文件总数,递归计 */
  fileCount: number
}

// ── Factory response ──
export interface FactoryTypesResponse {
  [key: string]: ProviderMeta[]
}
