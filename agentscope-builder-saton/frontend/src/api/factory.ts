import client from './client'
import type { ApiResponse } from './client'
import type { ProviderMeta } from '@/types'

/** GET /api/factories/model-types */
export function getModelTypes() {
  return client.get<ApiResponse<ProviderMeta[]>>('/api/factories/model-types')
}

/** GET /api/factories/tool-types */
export function getToolTypes() {
  return client.get<ApiResponse<ProviderMeta[]>>('/api/factories/tool-types')
}

/** GET /api/factories/skill-repo-types */
export function getSkillRepoTypes() {
  return client.get<ApiResponse<ProviderMeta[]>>('/api/factories/skill-repo-types')
}

/** GET /api/factories/middleware-types */
export function getMiddlewareTypes() {
  return client.get<ApiResponse<ProviderMeta[]>>('/api/factories/middleware-types')
}
