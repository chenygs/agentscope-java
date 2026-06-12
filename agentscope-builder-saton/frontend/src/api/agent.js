import client from './client';
// ── Agents ──
/** GET /api/agents */
export function listAgents() {
    return client.get('/api/agents');
}
/** GET /api/agents/:id */
export function getAgent(id) {
    return client.get(`/api/agents/${id}`);
}
/** POST /api/agents */
export function createAgent(req) {
    return client.post('/api/agents', req);
}
/** PUT /api/agents/:id */
export function updateAgent(id, req) {
    return client.put(`/api/agents/${id}`, req);
}
/** DELETE /api/agents/:id */
export function deleteAgent(id) {
    return client.delete(`/api/agents/${id}`);
}
// ── Skills Upload ──
/** POST /api/agents/:agentId/skills/upload (multipart/form-data) */
export function uploadSkill(agentId, skillName, file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('skillName', skillName);
    return client.post(`/api/agents/${agentId}/skills/upload`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
}
/** GET /api/agents/:agentId/skills/workspace */
export function listWorkspaceSkills(agentId) {
    return client.get(`/api/agents/${agentId}/skills/workspace`);
}
/** DELETE /api/agents/:agentId/skills/workspace/:name */
export function deleteWorkspaceSkill(agentId, name) {
    return client.delete(`/api/agents/${agentId}/skills/workspace/${name}`);
}
