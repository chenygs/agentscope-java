import client from './client'
import type { ApiResponse } from './client'
import type { AgentDetail, AgentUpsertReq, WorkspaceSkill } from '@/types'

// ── Agents ──

/** GET /api/agents */
export function listAgents() {
  return client.get<ApiResponse<AgentDetail[]>>('/api/agents')
}

/** GET /api/agents/:id */
export function getAgent(id: number) {
  return client.get<ApiResponse<AgentDetail>>(`/api/agents/${id}`)
}

/** POST /api/agents */
export function createAgent(req: AgentUpsertReq) {
  return client.post<ApiResponse<AgentDetail>>('/api/agents', req)
}

/** PUT /api/agents/:id */
export function updateAgent(id: number, req: AgentUpsertReq) {
  return client.put<ApiResponse<AgentDetail>>(`/api/agents/${id}`, req)
}

/** DELETE /api/agents/:id */
export function deleteAgent(id: number) {
  return client.delete<ApiResponse<void>>(`/api/agents/${id}`)
}

// ── Skills Upload ──

/** POST /api/agents/:agentId/skills/upload (multipart/form-data) */
export function uploadSkill(agentId: number, skillName: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('skillName', skillName)
  return client.post<ApiResponse<void>>(
    `/api/agents/${agentId}/skills/upload`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
}

/** GET /api/agents/:agentId/skills/workspace */
export function listWorkspaceSkills(agentId: number) {
  return client.get<ApiResponse<WorkspaceSkill[]>>(`/api/agents/${agentId}/skills/workspace`)
}

/** DELETE /api/agents/:agentId/skills/workspace/:name */
export function deleteWorkspaceSkill(agentId: number, name: string) {
  return client.delete<ApiResponse<void>>(`/api/agents/${agentId}/skills/workspace/${name}`)
}
