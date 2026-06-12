import client from './client';
/** POST /api/auth/login */
export function login(username, password) {
    return client.post('/api/auth/login', { username, password });
}
/** GET /api/auth/me */
export function getMe() {
    return client.get('/api/auth/me');
}
