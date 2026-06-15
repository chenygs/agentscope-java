import client from './client'
import type { ApiResponse } from './client'
import type { ChatMessage, Session } from '@/types'

/** GET /api/agents/:id/sessions */
export function listSessions(agentId: number) {
  return client.get<ApiResponse<Session[]>>(`/api/agents/${agentId}/sessions`)
}

/** GET /api/agents/:id/sessions/:key/messages — 拉取指定会话的历史消息 */
export function loadHistory(agentId: number, sessionKey: string) {
  return client.get<ApiResponse<ChatMessage[]>>(
    `/api/agents/${agentId}/sessions/${encodeURIComponent(sessionKey)}/messages`,
  )
}

/** POST /api/agents/:id/sessions/:key/reset — 删除/重置会话（后端语义同删除） */
export function resetSession(agentId: number, sessionKey: string) {
  return client.post<ApiResponse<{ removed: boolean }>>(
    `/api/agents/${agentId}/sessions/${encodeURIComponent(sessionKey)}/reset`,
  )
}
