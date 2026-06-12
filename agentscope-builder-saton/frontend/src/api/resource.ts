import client from './client'
import type { ApiResponse } from './client'
import type {
  ModelProvider,
  ModelProviderUpsertReq,
  McpServer,
  McpServerUpsertReq,
  SkillRepository,
  SkillRepositoryUpsertReq,
  SkillMarketplace,
  SkillMarketplaceUpsertReq,
  ProviderMeta,
} from '@/types'

// ── Builtin Tools (HarnessAgent 内省) ──

/** GET /api/factories/builtin-tools */
export function listBuiltinTools() {
  return client.get<ApiResponse<ProviderMeta[]>>('/api/factories/builtin-tools')
}

// ── Model Providers ──

/** GET /api/models */
export function listModels() {
  return client.get<ApiResponse<ModelProvider[]>>('/api/models')
}

/** GET /api/models/:id */
export function getModel(id: number) {
  return client.get<ApiResponse<ModelProvider>>(`/api/models/${id}`)
}

/** POST /api/models */
export function createModel(req: ModelProviderUpsertReq) {
  return client.post<ApiResponse<ModelProvider>>('/api/models', req)
}

/** PUT /api/models/:id */
export function updateModel(id: number, req: ModelProviderUpsertReq) {
  return client.put<ApiResponse<ModelProvider>>(`/api/models/${id}`, req)
}

/** DELETE /api/models/:id */
export function deleteModel(id: number) {
  return client.delete<ApiResponse<void>>(`/api/models/${id}`)
}

// ── MCP Servers ──

/** GET /api/mcp-servers */
export function listMcpServers() {
  return client.get<ApiResponse<McpServer[]>>('/api/mcp-servers')
}

/** GET /api/mcp-servers/:id */
export function getMcpServer(id: number) {
  return client.get<ApiResponse<McpServer>>(`/api/mcp-servers/${id}`)
}

/** POST /api/mcp-servers */
export function createMcpServer(req: McpServerUpsertReq) {
  return client.post<ApiResponse<McpServer>>('/api/mcp-servers', req)
}

/** PUT /api/mcp-servers/:id */
export function updateMcpServer(id: number, req: McpServerUpsertReq) {
  return client.put<ApiResponse<McpServer>>(`/api/mcp-servers/${id}`, req)
}

/** DELETE /api/mcp-servers/:id */
export function deleteMcpServer(id: number) {
  return client.delete<ApiResponse<void>>(`/api/mcp-servers/${id}`)
}

// ── Skill Repositories ──

/** GET /api/skill-repositories */
export function listSkillRepos() {
  return client.get<ApiResponse<SkillRepository[]>>('/api/skill-repositories')
}

/** GET /api/skill-repositories/:id */
export function getSkillRepo(id: number) {
  return client.get<ApiResponse<SkillRepository>>(`/api/skill-repositories/${id}`)
}

/** POST /api/skill-repositories */
export function createSkillRepo(req: SkillRepositoryUpsertReq) {
  return client.post<ApiResponse<SkillRepository>>('/api/skill-repositories', req)
}

/** PUT /api/skill-repositories/:id */
export function updateSkillRepo(id: number, req: SkillRepositoryUpsertReq) {
  return client.put<ApiResponse<SkillRepository>>(`/api/skill-repositories/${id}`, req)
}

/** DELETE /api/skill-repositories/:id */
export function deleteSkillRepo(id: number) {
  return client.delete<ApiResponse<void>>(`/api/skill-repositories/${id}`)
}

// ── Skill Marketplaces ──

/** GET /api/skill-marketplaces */
export function listSkillMarketplaces() {
  return client.get<ApiResponse<SkillMarketplace[]>>('/api/skill-marketplaces')
}

/** POST /api/skill-marketplaces */
export function createSkillMarketplace(req: SkillMarketplaceUpsertReq) {
  return client.post<ApiResponse<SkillMarketplace>>('/api/skill-marketplaces', req)
}

/** PUT /api/skill-marketplaces/:id */
export function updateSkillMarketplace(id: number, req: SkillMarketplaceUpsertReq) {
  return client.put<ApiResponse<SkillMarketplace>>(`/api/skill-marketplaces/${id}`, req)
}

/** DELETE /api/skill-marketplaces/:id */
export function deleteSkillMarketplace(id: number) {
  return client.delete<ApiResponse<void>>(`/api/skill-marketplaces/${id}`)
}
