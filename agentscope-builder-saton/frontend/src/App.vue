<script setup lang="ts">
import { onMounted, watch, defineComponent } from 'vue'
import { NConfigProvider, NMessageProvider, NDialogProvider, darkTheme, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useFactoriesStore } from '@/stores/factories'
import router from '@/router'

const ui = useUiStore()
const auth = useAuthStore()
const factories = useFactoriesStore()
const { locale: i18nLocale } = useI18n()

// 同步 ui store locale 到 vue-i18n
watch(() => ui.locale, (v) => { i18nLocale.value = v }, { immediate: true })

// 同步 body 背景色与主题
watch(() => ui.isDark, (dark) => {
  document.body.style.backgroundColor = dark ? '#0a0a0a' : '#fafafa'
  document.body.style.colorScheme = dark ? 'dark' : 'light'
}, { immediate: true })

// 登录后自动加载 factories 元数据
watch(
  () => auth.isLoggedIn,
  (loggedIn) => { if (loggedIn) factories.loadAll() },
  { immediate: true },
)

// 挂载全局工具到 window（供 axios 拦截器使用）
onMounted(() => {
  window.$authStore = auth
  window.$router = router
})

// 内部组件：把 useMessage() 挂到 window.$message
const MessageApiRegister = defineComponent({
  name: 'MessageApiRegister',
  setup() {
    const message = useMessage()
    onMounted(() => { window.$message = message })
    return () => null
  },
})
</script>

<template>
  <NConfigProvider
    :theme="ui.isDark ? darkTheme : null"
    :theme-overrides="{}"
  >
    <NMessageProvider>
      <NDialogProvider>
        <MessageApiRegister />
        <RouterView />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
