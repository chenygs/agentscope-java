import client from './client';
/** GET /api/factories/model-types */
export function getModelTypes() {
    return client.get('/api/factories/model-types');
}
/** GET /api/factories/tool-types */
export function getToolTypes() {
    return client.get('/api/factories/tool-types');
}
/** GET /api/factories/skill-repo-types */
export function getSkillRepoTypes() {
    return client.get('/api/factories/skill-repo-types');
}
/** GET /api/factories/middleware-types */
export function getMiddlewareTypes() {
    return client.get('/api/factories/middleware-types');
}
