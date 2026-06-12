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
