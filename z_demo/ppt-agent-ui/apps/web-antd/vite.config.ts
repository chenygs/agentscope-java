import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          // 后端 REST API: 前端 /api/auth/login -> 后端 /api/auth/login
          '/api': {
            changeOrigin: true,
            target: 'http://localhost:8889',
            ws: true,
          },
          // AG-UI SSE 流: 前端 /agui/run -> 后端 /agui/run
          '/agui': {
            changeOrigin: true,
            target: 'http://localhost:8889',
            ws: false,
          },
        },
      },
    },
  };
});
