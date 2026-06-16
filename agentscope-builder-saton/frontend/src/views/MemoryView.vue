<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  NAlert,
  NButton,
  NEmpty,
  NH2,
  NInput,
  NSpace,
  NSpin,
  NTabPane,
  NTabs,
  NTag,
  NText,
  NTooltip,
  useDialog,
  useMessage,
  useThemeVars,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as memoryApi from '@/api/memory'
import type { MemoryFile, MemoryKind, MemorySummary } from '@/api/memory'

const { t } = useI18n()
const dialog = useDialog()
const message = useMessage()
const themeVars = useThemeVars()

/** 后端 WorkspaceService.MAX_READ 超限时返回的字符串前缀 */
const TOO_LARGE_PREFIX = '(file too large to display:'

interface KindState {
  content: string
  loaded: boolean
  loading: boolean
  saving: boolean
  dirty: boolean
}

const KINDS: Array<{ key: MemoryKind; tab: string; hint: string }> = [
  { key: 'persona', tab: 'memory.personaTab', hint: 'memory.personaHint' },
  { key: 'long-term', tab: 'memory.longTermTab', hint: 'memory.longTermHint' },
]

// ── 状态 ──
const summary = ref<MemorySummary | null>(null)
const loadingSummary = ref(false)
const activeKind = ref<MemoryKind>('persona')

const states = reactive<Record<MemoryKind, KindState>>({
  persona: { content: '', loaded: false, loading: false, saving: false, dirty: false },
  'long-term': { content: '', loaded: false, loading: false, saving: false, dirty: false },
})

// ── 派生 ──
const currentState = computed(() => states[activeKind.value])
const currentMeta = computed<MemoryFile | null>(() =>
  summary.value?.files.find((f) => f.kind === activeKind.value) ?? null,
)
const currentTooLarge = computed(() => currentState.value.content.startsWith(TOO_LARGE_PREFIX))

// ── 加载 ──
async function loadSummary() {
  loadingSummary.value = true
  try {
    const resp = await memoryApi.getMemorySummary()
    summary.value = resp.data.data
  } catch {
    // interceptor handled
  } finally {
    loadingSummary.value = false
  }
}

async function loadKind(kind: MemoryKind) {
  const s = states[kind]
  s.loading = true
  try {
    const resp = await memoryApi.readMemory(kind)
    s.content = typeof resp.data === 'string' ? resp.data : String(resp.data ?? '')
    s.loaded = true
    s.dirty = false
  } catch {
    // interceptor handled
  } finally {
    s.loading = false
  }
}

async function refresh() {
  await loadSummary()
  // 已加载过的 kind 重新拉一次,未加载过的等切到才拉
  await Promise.all(KINDS.filter((k) => states[k.key].loaded).map((k) => loadKind(k.key)))
}

// 切 Tab 时按需加载
watch(activeKind, (kind) => {
  if (!states[kind].loaded && !states[kind].loading) {
    loadKind(kind)
  }
})

onMounted(async () => {
  await loadSummary()
  await loadKind(activeKind.value)
})

// ── 保存 ──
async function onSave() {
  const kind = activeKind.value
  const s = states[kind]
  s.saving = true
  try {
    await memoryApi.writeMemory(kind, s.content)
    s.dirty = false
    message.success(t('memory.savedOk'))
    await loadSummary()
  } catch {
    // interceptor handled
  } finally {
    s.saving = false
  }
}

// ── 切 Tab 前如果脏则确认 ──
function beforeLeave(toName: string | number, fromName: string | number): boolean {
  const fromKind = fromName as MemoryKind
  if (!states[fromKind]?.dirty) return true
  dialog.warning({
    title: t('memory.unsavedTitle'),
    content: t('memory.unsavedConfirm'),
    positiveText: t('common.discard'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      states[fromKind].dirty = false
      // 用户选择丢弃后重新拉一份原始内容
      await loadKind(fromKind)
      activeKind.value = toName as MemoryKind
    },
  })
  return false
}

// ── 工具 ──
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function formatTime(ms: number | null | undefined): string {
  if (!ms) return '—'
  return new Date(ms).toLocaleString()
}

const styleVars = computed(() => ({
  '--mem-border': themeVars.value.dividerColor,
  '--mem-text-2': themeVars.value.textColor2,
  '--mem-text-3': themeVars.value.textColor3,
}))
</script>

<template>
  <div class="mem-view" :style="styleVars">
    <div class="mem-head">
      <NH2 style="margin: 0">{{ t('memory.title') }}</NH2>
      <NText :depth="3" style="font-size: 13px">{{ t('memory.subtitle') }}</NText>
    </div>

    <!-- Summary 信息条 -->
    <div class="mem-summary">
      <NSpace align="center" :size="12" :wrap="false">
        <NText :depth="3" style="font-size: 12px">{{ t('memory.rootPath') }}:</NText>
        <NTooltip placement="bottom-start" trigger="hover">
          <template #trigger>
            <NText class="mem-path">{{ summary?.root ?? '—' }}</NText>
          </template>
          {{ summary?.root ?? '—' }}
        </NTooltip>
        <NButton size="tiny" quaternary :loading="loadingSummary" @click="refresh">
          {{ t('common.refresh') }}
        </NButton>
      </NSpace>
    </div>

    <!-- 双 Tab 编辑器 -->
    <NTabs
      v-model:value="activeKind"
      type="line"
      animated
      :on-before-leave="beforeLeave"
      class="mem-tabs"
    >
      <NTabPane v-for="k in KINDS" :key="k.key" :name="k.key" :tab="t(k.tab)">
        <div class="mem-pane">
          <!-- Hint 行 -->
          <NAlert :show-icon="false" type="default" class="mem-hint">
            <NSpace align="center" :wrap="false" :size="8">
              <NText :depth="2" style="flex: 1; font-size: 13px">{{ t(k.hint) }}</NText>
              <template v-if="currentMeta">
                <NTag v-if="!currentMeta.exists" size="small" :bordered="false">
                  {{ t('memory.notExists') }}
                </NTag>
                <template v-else>
                  <NTag size="small" type="info" :bordered="false">
                    {{ t('memory.fileSize') }}: {{ formatSize(currentMeta.size) }}
                  </NTag>
                  <NTag size="small" :bordered="false">
                    {{ t('memory.lastModified') }}: {{ formatTime(currentMeta.modifiedAt) }}
                  </NTag>
                </template>
              </template>
            </NSpace>
          </NAlert>

          <!-- 编辑器 -->
          <div v-if="currentState.loading && !currentState.loaded" class="mem-loading">
            <NSpin size="small" />
          </div>
          <template v-else>
            <NInput
              v-model:value="currentState.content"
              type="textarea"
              :placeholder="t('memory.placeholder')"
              :readonly="currentTooLarge"
              :rows="20"
              class="mem-textarea"
              @update:value="currentState.dirty = !currentTooLarge"
            />
            <div class="mem-actions">
              <NTag v-if="currentTooLarge" size="small" type="warning" :bordered="false">
                {{ t('memory.fileTooLarge') }}
              </NTag>
              <NTag v-else-if="currentState.dirty" size="small" type="warning" :bordered="false"
                >●</NTag
              >
              <div style="flex: 1"></div>
              <NButton
                type="primary"
                :disabled="!currentState.dirty || currentTooLarge"
                :loading="currentState.saving"
                @click="onSave"
              >
                {{ t('common.save') }}
              </NButton>
            </div>
          </template>
        </div>
      </NTabPane>
    </NTabs>

    <NEmpty v-if="!summary && !loadingSummary" style="margin-top: 60px" />
  </div>
</template>

<style scoped>
.mem-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.mem-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mem-summary {
  padding: 10px 14px;
  border: 1px solid var(--mem-border);
  border-radius: 6px;
}

.mem-path {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--mem-text-2);
  display: inline-block;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.mem-tabs {
  flex: 1;
  min-height: 0;
}

.mem-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mem-hint {
  font-size: 13px;
}

.mem-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.mem-textarea :deep(.n-input__textarea-el) {
  font-family: ui-monospace, Menlo, Consolas, monospace !important;
  font-size: 13px;
  line-height: 1.6;
}

.mem-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
