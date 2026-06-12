import client from './client';
// ── Builtin Tools (HarnessAgent 内省) ──
/** GET /api/factories/builtin-tools */
export function listBuiltinTools() {
    return client.get('/api/factories/builtin-tools');
}
// ── Model Providers ──
/** GET /api/models */
export function listModels() {
    return client.get('/api/models');
}
/** GET /api/models/:id */
export function getModel(id) {
    return client.get(`/api/models/${id}`);
}
/** POST /api/models */
export function createModel(req) {
    return client.post('/api/models', req);
}
/** PUT /api/models/:id */
export function updateModel(id, req) {
    return client.put(`/api/models/${id}`, req);
}
/** DELETE /api/models/:id */
export function deleteModel(id) {
    return client.delete(`/api/models/${id}`);
}
// ── MCP Servers ──
/** GET /api/mcp-servers */
export function listMcpServers() {
    return client.get('/api/mcp-servers');
}
/** GET /api/mcp-servers/:id */
export function getMcpServer(id) {
    return client.get(`/api/mcp-servers/${id}`);
}
/** POST /api/mcp-servers */
export function createMcpServer(req) {
    return client.post('/api/mcp-servers', req);
}
/** PUT /api/mcp-servers/:id */
export function updateMcpServer(id, req) {
    return client.put(`/api/mcp-servers/${id}`, req);
}
/** DELETE /api/mcp-servers/:id */
export function deleteMcpServer(id) {
    return client.delete(`/api/mcp-servers/${id}`);
}
// ── Skill Repositories ──
/** GET /api/skill-repositories */
export function listSkillRepos() {
    return client.get('/api/skill-repositories');
}
/** GET /api/skill-repositories/:id */
export function getSkillRepo(id) {
    return client.get(`/api/skill-repositories/${id}`);
}
/** POST /api/skill-repositories */
export function createSkillRepo(req) {
    return client.post('/api/skill-repositories', req);
}
/** PUT /api/skill-repositories/:id */
export function updateSkillRepo(id, req) {
    return client.put(`/api/skill-repositories/${id}`, req);
}
/** DELETE /api/skill-repositories/:id */
export function deleteSkillRepo(id) {
    return client.delete(`/api/skill-repositories/${id}`);
}
// ── Skill Marketplaces ──
/** GET /api/skill-marketplaces */
export function listSkillMarketplaces() {
    return client.get('/api/skill-marketplaces');
}
/** POST /api/skill-marketplaces */
export function createSkillMarketplace(req) {
    return client.post('/api/skill-marketplaces', req);
}
/** PUT /api/skill-marketplaces/:id */
export function updateSkillMarketplace(id, req) {
    return client.put(`/api/skill-marketplaces/${id}`, req);
}
/** DELETE /api/skill-marketplaces/:id */
export function deleteSkillMarketplace(id) {
    return client.delete(`/api/skill-marketplaces/${id}`);
}
