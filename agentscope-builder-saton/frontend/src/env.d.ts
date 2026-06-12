/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<{}, {}, any>
  export default component
}

import type { MessageApiInjection } from 'naive-ui/lib/message/src/MessageProvider'
import type { Router } from 'vue-router'

declare global {
  interface Window {
    $message: MessageApiInjection
    $router: Router
    $authStore: {
      logout: () => void
    }
  }
}

export {}
