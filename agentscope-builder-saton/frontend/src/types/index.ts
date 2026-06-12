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
