import client from './client'
import type { ApiResponse } from './client'
import type { ModelProvider, ModelProviderUpsertReq, McpServer } from '@/types'

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
