import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useUiStore = defineStore('ui', () => {
  // ── sidebar ──
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  watch(sidebarCollapsed, (v) => {
    localStorage.setItem('sidebarCollapsed', String(v))
  })

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // ── locale ──
  const locale = ref(localStorage.getItem('locale') ?? 'zh-CN')

  watch(locale, (v) => {
    localStorage.setItem('locale', v)
  })

  function toggleLocale() {
    locale.value = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  }

  // ── theme ──
  const isDark = ref(localStorage.getItem('theme') !== 'light')

  watch(isDark, (v) => {
    localStorage.setItem('theme', v ? 'dark' : 'light')
  })

  function toggleTheme() {
    isDark.value = !isDark.value
  }

  return {
    sidebarCollapsed,
    toggleSidebar,
    locale,
    toggleLocale,
    isDark,
    toggleTheme,
  }
})
