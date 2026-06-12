import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import MainLayout from '@/layouts/MainLayout.vue';
const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/LoginView.vue'),
        meta: { public: true },
    },
    {
        path: '/',
        component: MainLayout,
        children: [
            {
                path: '',
                redirect: '/dashboard',
            },
            {
                path: 'dashboard',
                name: 'Dashboard',
                component: () => import('@/views/DashboardView.vue'),
            },
            {
                path: 'agents',
                name: 'Agents',
                component: () => import('@/views/AgentListView.vue'),
            },
            {
                path: 'agents/new',
                name: 'AgentCreate',
                component: () => import('@/views/AgentEditView.vue'),
            },
            {
                path: 'agents/:id/edit',
                name: 'AgentEdit',
                component: () => import('@/views/AgentEditView.vue'),
            },
            {
                path: 'agents/:id/chat',
                name: 'AgentChat',
                component: () => import('@/views/ChatView.vue'),
            },
            {
                path: 'models',
                name: 'Models',
                component: () => import('@/views/ModelsView.vue'),
            },
            {
                path: 'tools',
                name: 'Tools',
                component: () => import('@/views/ToolsView.vue'),
            },
            {
                path: 'memory',
                name: 'Memory',
                component: () => import('@/views/MemoryView.vue'),
            },
            {
                path: 'profile',
                name: 'Profile',
                component: () => import('@/views/ProfileView.vue'),
            },
        ],
    },
    {
        path: '/:catchAll(.*)*',
        name: 'NotFound',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { public: true },
    },
];
const router = createRouter({
    history: createWebHistory(),
    routes,
});
// ── 路由守卫（支持 HMR 会话恢复）──
let initialCheckDone = false;
router.beforeEach(async (to) => {
    if (to.meta.public)
        return true;
    const auth = useAuthStore();
    // 已登录 → 放行
    if (auth.isLoggedIn)
        return true;
    // 未登录但 cookie 可能仍有效（HMR / 刷新场景）→ 尝试 /me 恢复
    if (!initialCheckDone) {
        initialCheckDone = true;
        await auth.fetchMe();
        if (auth.isLoggedIn)
            return true;
    }
    return { path: '/login', query: { redirect: to.fullPath } };
});
export default router;
