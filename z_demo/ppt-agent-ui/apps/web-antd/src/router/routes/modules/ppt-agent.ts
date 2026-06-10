import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:presentation',
      order: 0,
      title: 'PPT 智能体',
    },
    name: 'PptAgent',
    path: '/ppt-agent',
    children: [
      {
        name: 'PptAgentIndex',
        path: '/ppt-agent/index',
        component: () => import('#/views/ppt-agent/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:presentation',
          title: 'PPT 智能体',
        },
      },
    ],
  },
];

export default routes;