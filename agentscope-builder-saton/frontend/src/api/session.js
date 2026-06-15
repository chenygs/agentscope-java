import client from './client';
/** GET /api/agents/:id/sessions */
export function listSessions(agentId) {
    return client.get(`/api/agents/${agentId}/sessions`);
}
/** GET /api/agents/:id/sessions/:key/messages — 拉取指定会话的历史消息 */
export function loadHistory(agentId, sessionKey) {
    return client.get(`/api/agents/${agentId}/sessions/${encodeURIComponent(sessionKey)}/messages`);
}
/** POST /api/agents/:id/sessions/:key/reset — 删除/重置会话（后端语义同删除） */
export function resetSession(agentId, sessionKey) {
    return client.post(`/api/agents/${agentId}/sessions/${encodeURIComponent(sessionKey)}/reset`);
}
