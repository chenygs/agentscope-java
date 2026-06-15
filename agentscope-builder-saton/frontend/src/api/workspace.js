import client from './client';
/** GET /api/agents/:id/workspace — 获取摘要(路径 + 文件数) */
export function getWorkspaceSummary(agentId) {
    return client.get(`/api/agents/${agentId}/workspace`);
}
/** GET /api/agents/:id/workspace/files — 获取目录下第一层. path 不传或空 = 根目录 */
export function listWorkspaceFiles(agentId, path) {
    return client.get(`/api/agents/${agentId}/workspace/files`, path ? { params: { path } } : undefined);
}
/** GET /api/agents/:id/workspace/file?path=... — 读取文件内容(文本), xsrf 问题用 params 避免 POST */
export function readWorkspaceFile(agentId, filePath) {
    return client.get(`/api/agents/${agentId}/workspace/file`, { params: { path: filePath }, responseType: 'text' });
}
/** PUT /api/agents/:id/workspace/file?path=... — 写入文件 */
export function writeWorkspaceFile(agentId, filePath, content) {
    return client.put(`/api/agents/${agentId}/workspace/file?path=${encodeURIComponent(filePath)}`, { content });
}
/** DELETE /api/agents/:id/workspace/file?path=... — 删除文件 */
export function deleteWorkspaceFile(agentId, filePath) {
    return client.delete(`/api/agents/${agentId}/workspace/file`, { params: { path: filePath } });
}
