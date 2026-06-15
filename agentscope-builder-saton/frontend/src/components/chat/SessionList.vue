<script setup lang="ts">
import { computed } from 'vue'
import { NButton, NEmpty, NSpin, NScrollbar, useThemeVars } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { Session } from '@/types'

interface Props {
  sessions: Session[]
  activeKey?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), { loading: false })

const emit = defineEmits<{
  (e: 'select', key: string): void
  (e: 'new'): void
  (e: 'delete', key: string): void
}>()

const { t } = useI18n()
const themeVars = useThemeVars()

const items = computed(() => props.sessions)

/**
 * 显示用标题：title 非空就截到 24 字符，否则回落到 sessionKey。
 * 后端给的 title 不截断，截断逻辑全在前端做。
 */
const TITLE_MAX = 24
function displayTitle(s: Session): string {
  const t = (s.title ?? '').trim()
  if (!t) return s.sessionKey
  return t.length > TITLE_MAX ? t.slice(0, TITLE_MAX) + '…' : t
}

/**
 * 通过 useThemeVars 取响应式主题色：暗黑模式下自动切换。
 * 直接写 CSS var(--n-*) 不可靠 —— 这些变量只在某些 Naive UI 组件（NCard 等）上下文里注入，
 * NLayoutSider 里不存在，会落到 fallback 写死颜色，破坏暗黑模式。
 */
const styleVars = computed(() => ({
  '--ss-bg': themeVars.value.cardColor,
  '--ss-border': themeVars.value.dividerColor,
  '--ss-hover': themeVars.value.hoverColor,
  '--ss-active': themeVars.value.primaryColorSuppl,
  '--ss-active-text': themeVars.value.primaryColor,
  '--ss-text': themeVars.value.textColor1,
}))
</script>

<template>
  <div class="session-list" :style="styleVars">
    <div class="session-list__header">
      <span class="session-list__title">{{ t('chat.sessions') }}</span>
      <NButton size="small" type="primary" @click="emit('new')">
        + {{ t('chat.newSession') }}
      </NButton>
    </div>

    <div class="session-list__body">
      <div v-if="loading" class="session-list__center">
        <NSpin size="small" />
      </div>
      <div v-else-if="items.length === 0" class="session-list__center">
        <NEmpty :description="t('chat.sessionsEmpty')" size="small" />
      </div>
      <NScrollbar v-else style="max-height: 100%">
        <div
          v-for="s in items"
          :key="s.sessionKey"
          class="session-list__item"
          :class="{ 'session-list__item--active': s.sessionKey === activeKey }"
          @click="emit('select', s.sessionKey)"
        >
          <span class="session-list__title-text" :title="s.title || s.sessionKey">
            {{ displayTitle(s) }}
          </span>
          <NButton
            quaternary
            circle
            size="tiny"
            class="session-list__delete"
            @click.stop="emit('delete', s.sessionKey)"
          >
            ✕
          </NButton>
        </div>
      </NScrollbar>
    </div>
  </div>
</template>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ss-bg);
  color: var(--ss-text);
}

.session-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ss-border);
  flex-shrink: 0;
}

.session-list__title {
  font-weight: 600;
  font-size: 14px;
}

.session-list__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.session-list__center {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.session-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  cursor: pointer;
  border-left: 2px solid transparent;
  transition: background-color 0.15s;
}

.session-list__item:hover {
  background: var(--ss-hover);
}

.session-list__item--active {
  background: var(--ss-active);
  border-left-color: var(--ss-active-text);
}

.session-list__title-text {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}

.session-list__delete {
  opacity: 0;
  transition: opacity 0.15s;
}

.session-list__item:hover .session-list__delete {
  opacity: 0.6;
}

.session-list__delete:hover {
  opacity: 1 !important;
}
</style>
