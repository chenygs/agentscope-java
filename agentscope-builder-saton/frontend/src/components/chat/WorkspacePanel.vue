<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NEmpty,
  NInput,
  NScrollbar,
  NSpace,
  NSpin,
  NText,
  NTag,
  NTooltip,
  useDialog,
  useMessage,
  useThemeVars,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as workspaceApi from '@/api/workspace'
import type { FileNode, WorkspaceSummary } from '@/types'

interface Props {
  agentId: number
}
const props = defineProps<Props>()

const { t } = useI18n()
const dialog = useDialog()
const message = useMessage()
const themeVars = useThemeVars()

// ── 摘要 ──
const summary = ref<WorkspaceSummary | null>(null)
const loadingSummary = ref(false)

// ── 文件树状态 ──
/**
 * 路径 → 该路径下的子节点(后端已解析,path 字段是相对 agent root 的完整相对路径)。
 * '' 表示根目录。已加载过的 key 不会重新拉,除非 refresh()。
 */
const childrenByPath = reactive<Record<string, FileNode[]>>({})
const expandedPaths = reactive<Set<string>>(new Set([''])) // 根目录默认展开
const loadingPaths = reactive<Set<string>>(new Set())

// ── 编辑器 ──
const editingPath = ref<string | null>(null)
const editingContent = ref('')
const editingDirty = ref(false)
/** 后端 WorkspaceService.MAX_READ 超限时返回的字符串前缀 */
const TOO_LARGE_PREFIX = '(file too large to display:'
const editingTooLarge = computed(() => editingContent.value.startsWith(TOO_LARGE_PREFIX))
const saving = ref(false)

/**
 * 把 childrenByPath 这个 map 拍成一份扁平、带 depth 的列表用于渲染:
 * 只展开的目录会贡献它的子节点,未展开的目录只贡献自己一行。
 * 这避免了 Vue 3 递归组件的样板,所有缩进直接靠 paddingLeft 算。
 */
const flatRows = computed<Array<{ node: FileNode; depth: number }>>(() => {
  const out: Array<{ node: FileNode; depth: number }> = []
  function walk(parentPath: string, depth: number) {
    const list = childrenByPath[parentPath] ?? []
    for (const node of list) {
      out.push({ node, depth })
      if (node.type === 'dir' && expandedPaths.has(node.path)) {
        walk(node.path, depth + 1)
      }
    }
  }
  walk('', 0)
  return out
})

async function loadDir(path: string) {
  loadingPaths.add(path)
  try {
    const resp = await workspaceApi.listWorkspaceFiles(props.agentId, path || undefined)
    childrenByPath[path] = resp.data.data ?? []
  } catch {
    childrenByPath[path] = []
  } finally {
    loadingPaths.delete(path)
  }
}

async function refreshSummary() {
  loadingSummary.value = true
  try {
    const resp = await workspaceApi.getWorkspaceSummary(props.agentId)
    summary.value = resp.data.data
  } catch {
    // interceptor handled
  } finally {
    loadingSummary.value = false
  }
}

/** 全量刷新:summary + 当前展开过的所有目录(保留展开状态)。 */
async function refresh() {
  const paths = Array.from(expandedPaths)
  if (!paths.includes('')) paths.push('')
  await Promise.all([refreshSummary(), ...paths.map(p => loadDir(p))])
}

onMounted(refresh)
defineExpose({ refresh })

async function toggleDir(node: FileNode) {
  if (expandedPaths.has(node.path)) {
    expandedPaths.delete(node.path)
    return
  }
  expandedPaths.add(node.path)
  if (!(node.path in childrenByPath)) {
    await loadDir(node.path)
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

async function onPickFile(node: FileNode) {
  try {
    const resp = await workspaceApi.readWorkspaceFile(props.agentId, node.path)
    editingPath.value = node.path
    editingContent.value = typeof resp.data === 'string' ? resp.data : String(resp.data ?? '')
    editingDirty.value = false
  } catch {
    // interceptor handled
  }
}

async function onSave() {
  if (!editingPath.value) return
  saving.value = true
  try {
    await workspaceApi.writeWorkspaceFile(props.agentId, editingPath.value, editingContent.value)
    message.success(t('chat.workspaceFileSaved'))
    editingDirty.value = false
    await refresh()
  } catch {
    // interceptor handled
  } finally {
    saving.value = false
  }
}

function onDelete(node: FileNode) {
  dialog.warning({
    title: t('common.delete'),
    content: t('chat.workspaceDeleteConfirm', { path: node.path }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await workspaceApi.deleteWorkspaceFile(props.agentId, node.path)
        message.success(t('chat.workspaceFileDeleted'))
        if (editingPath.value === node.path) {
          editingPath.value = null
          editingContent.value = ''
          editingDirty.value = false
        }
        await refresh()
      } catch {
        // interceptor handled
      }
    },
  })
}

function closeEditor() {
  if (editingDirty.value) {
    dialog.warning({
      title: t('agent.unsavedWarning'),
      positiveText: t('common.discard'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => {
        editingPath.value = null
        editingContent.value = ''
        editingDirty.value = false
      },
    })
  } else {
    editingPath.value = null
    editingContent.value = ''
  }
}

const styleVars = computed(() => ({
  '--ws-border': themeVars.value.dividerColor,
  '--ws-hover': themeVars.value.hoverColor,
  '--ws-text-2': themeVars.value.textColor2,
  '--ws-text-3': themeVars.value.textColor3,
}))

const isRootEmpty = computed(() => '' in childrenByPath && (childrenByPath[''] ?? []).length === 0)
</script>

<template>
  <div class="ws-panel" :style="styleVars">
    <!-- 摘要 -->
    <div class="ws-summary">
      <NSpace vertical :size="4">
        <NSpace align="center" :size="6" :wrap="false">
          <NText :depth="3" style="font-size: 12px">{{ t('chat.workspacePath') }}:</NText>
          <NTooltip placement="bottom-start" trigger="hover">
            <template #trigger>
              <NText class="ws-path">{{ summary?.root ?? '—' }}</NText>
            </template>
            {{ summary?.root ?? '—' }}
          </NTooltip>
        </NSpace>
        <NSpace align="center" :size="6">
          <NText :depth="3" style="font-size: 12px">{{ t('chat.workspaceFileCount') }}:</NText>
          <NTag size="small" type="info" :bordered="false">{{ summary?.fileCount ?? 0 }}</NTag>
          <NButton size="tiny" quaternary @click="refresh" :loading="loadingSummary">
            {{ t('common.refresh') }}
          </NButton>
        </NSpace>
      </NSpace>
    </div>

    <!-- 文件树 -->
    <div class="ws-files">
      <div v-if="loadingSummary && flatRows.length === 0 && !('' in childrenByPath)" class="ws-center">
        <NSpin size="small" />
      </div>
      <div v-else-if="isRootEmpty" class="ws-center">
        <NEmpty :description="t('chat.workspaceEmpty')" size="small" />
      </div>
      <NScrollbar v-else>
        <div
          v-for="row in flatRows"
          :key="row.node.path"
          class="ws-file"
          :class="{ 'ws-file--active': editingPath === row.node.path && row.node.type === 'file' }"
          :style="{ paddingLeft: 12 + row.depth * 14 + 'px' }"
          @click="row.node.type === 'dir' ? toggleDir(row.node) : onPickFile(row.node)"
        >
          <span class="ws-file__caret">
            <template v-if="row.node.type === 'dir'">
              <template v-if="loadingPaths.has(row.node.path)">⏳</template>
              <template v-else-if="expandedPaths.has(row.node.path)">▾</template>
              <template v-else>▸</template>
            </template>
          </span>
          <span class="ws-file__icon">{{ row.node.type === 'dir' ? '📁' : '📄' }}</span>
          <span class="ws-file__name" :title="row.node.path">{{ row.node.name }}</span>
          <span class="ws-file__size">
            <template v-if="row.node.type === 'file'">{{ formatSize(row.node.size) }}</template>
          </span>
          <NButton
            v-if="row.node.type === 'file'"
            quaternary
            size="tiny"
            class="ws-file__del"
            @click.stop="onDelete(row.node)"
          >
            ✕
          </NButton>
        </div>
      </NScrollbar>
    </div>

    <!-- 编辑器 -->
    <div v-if="editingPath" class="ws-editor">
      <div class="ws-editor__header">
        <NText
          strong
          style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
          :title="editingPath"
        >
          {{ editingPath }}
        </NText>
        <NSpace :size="6" :wrap="false">
          <NTag v-if="editingTooLarge" size="small" type="warning" :bordered="false">
            {{ t('chat.workspaceFileTooLarge') }}
          </NTag>
          <NTag v-else-if="editingDirty" size="small" type="warning" :bordered="false">●</NTag>
          <NButton
            size="tiny"
            type="primary"
            :disabled="!editingDirty || editingTooLarge"
            :loading="saving"
            @click="onSave"
          >
            {{ t('common.save') }}
          </NButton>
          <NButton size="tiny" quaternary @click="closeEditor">✕</NButton>
        </NSpace>
      </div>
      <NInput
        v-model:value="editingContent"
        type="textarea"
        :readonly="editingTooLarge"
        @update:value="editingDirty = !editingTooLarge"
        class="ws-editor__textarea"
      />
    </div>
  </div>
</template>

<style scoped>
.ws-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ws-summary {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ws-border);
}

.ws-path {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--ws-text-2);
  display: inline-block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.ws-files {
  flex: 1;
  min-height: 120px;
  overflow: hidden;
  border-bottom: 1px solid var(--ws-border);
}

.ws-center {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ws-file {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px 4px 0;
  cursor: pointer;
  transition: background-color 0.15s;
  user-select: none;
}
.ws-file:hover {
  background: var(--ws-hover);
}
.ws-file--active {
  background: var(--ws-hover);
}

.ws-file__caret {
  width: 12px;
  text-align: center;
  font-size: 10px;
  color: var(--ws-text-3);
  flex-shrink: 0;
}

.ws-file__icon {
  flex-shrink: 0;
  font-size: 13px;
}

.ws-file__name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-left: 2px;
}

.ws-file__size {
  font-size: 11px;
  color: var(--ws-text-3);
  flex-shrink: 0;
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.ws-file__del {
  opacity: 0;
  transition: opacity 0.15s;
}
.ws-file:hover .ws-file__del {
  opacity: 0.6;
}
.ws-file__del:hover {
  opacity: 1 !important;
}

.ws-editor {
  flex: 1.2;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ws-editor__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ws-border);
  flex-shrink: 0;
}
.ws-editor__textarea {
  flex: 1;
  min-height: 0;
}
.ws-editor__textarea :deep(.n-input__textarea-el) {
  font-family: ui-monospace, Menlo, Consolas, monospace !important;
  font-size: 12px;
  height: 100%;
}
.ws-editor__textarea :deep(.n-input-wrapper) {
  height: 100%;
}
</style>
