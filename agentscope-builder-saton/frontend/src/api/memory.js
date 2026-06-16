import client from './client';
/** GET /api/memory — 摘要(各 kind 的存在性 / 大小 / 修改时间)。 */
export function getMemorySummary() {
    return client.get('/api/memory');
}
/** GET /api/memory/{kind} — 读取(text/plain;不存在返回空串)。 */
export function readMemory(kind) {
    return client.get(`/api/memory/${kind}`, { responseType: 'text' });
}
/** PUT /api/memory/{kind} — 全文覆写。 */
export function writeMemory(kind, content) {
    return client.put(`/api/memory/${kind}`, { content });
}
