import axios from 'axios';
const client = axios.create({
    baseURL: '',
    timeout: 30_000,
    withCredentials: true, // sa-token cookie
    headers: { 'Content-Type': 'application/json' },
});
// ── 响应拦截器 ──
client.interceptors.response.use((response) => {
    const body = response.data;
    // SSE / text / 文件等非 JSON 响应直接放行
    if (!body || typeof body.code !== 'number')
        return response;
    // 业务错误（code !== 200）
    if (body.code !== 200) {
        const msg = body.msg || '请求失败';
        window.$message?.error(msg);
        return Promise.reject(new Error(msg));
    }
    // 成功：直接返回整个 response（调用方自行取 .data.data）
    return response;
}, (error) => {
    if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        if (status === 401) {
            // 清除登录态，跳登录页
            window.$authStore?.logout();
            window.$router?.push({ path: '/login', query: { redirect: window.$router?.currentRoute.value.fullPath } });
            return Promise.reject(error);
        }
        if (status === 403) {
            window.$message?.error('无权限');
            return Promise.reject(error);
        }
        // 尝试读后端 error body 里的 msg
        const serverMsg = error.response?.data?.msg;
        if (serverMsg) {
            window.$message?.error(serverMsg);
        }
        else if (status && status >= 500) {
            window.$message?.error('服务器错误');
        }
        else if (error.code === 'ECONNABORTED') {
            window.$message?.error('请求超时');
        }
    }
    return Promise.reject(error);
});
export default client;
