<script setup lang="ts">
import { computed } from 'vue';

import { preferences, usePreferences } from '@vben/preferences';

import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';

interface Props {
  content: string;
}

const props = defineProps<Props>();

const { isDark } = usePreferences();

// md-editor-v3 的 theme 控制整体配色（背景/文字/代码块）
const mdTheme = computed<'dark' | 'light'>(() => (isDark.value ? 'dark' : 'light'));

// 每个组件实例稳定的 editorId（多个 MdPreview 共存时需要）
const editorId = computed(() => `md-${Math.abs(hashCode(props.content.slice(0, 64)))}`);

function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (h << 5) - h + s.charCodeAt(i);
    h |= 0;
  }
  return h;
}

// 静默避免 lint 警告
void preferences;
</script>

<template>
  <MdPreview
    :editor-id="editorId"
    :model-value="props.content || ''"
    :theme="mdTheme"
    :code-foldable="false"
    :show-code-row-number="false"
    preview-theme="github"
    code-theme="atom"
    class="ppt-md-preview"
  />
</template>

<style scoped>
/* ───────────────────────────────────────────────────────────
 * md-editor-v3 用 CSS 变量定义所有颜色，我们重写这些变量为
 * Vben 设计 token，整个组件就会跟主题/气泡颜色融合
 * ─────────────────────────────────────────────────────────── */
:deep(.ppt-md-preview.md-editor),
:deep(.ppt-md-preview.md-editor-dark) {
  --md-theme-bg-color: transparent;
  --md-theme-bg-color-inset: hsl(var(--accent));
  --md-theme-color: hsl(var(--foreground));
  --md-theme-color-reverse: hsl(var(--background));
  --md-theme-color-hover: hsl(var(--accent));
  --md-theme-color-hover-inset: hsl(var(--muted));
  --md-theme-border-color: hsl(var(--border));
  --md-theme-border-color-inset: hsl(var(--border));
  --md-theme-link-color: hsl(var(--primary));
  --md-theme-code-copy-tips-color: hsl(var(--foreground));
  --md-theme-code-copy-tips-bg-color: hsl(var(--popover));
  background: transparent !important;
  background-color: transparent !important;
  color: inherit !important;
}

:deep(.ppt-md-preview .md-editor-preview-wrapper) {
  padding: 0 !important;
}

:deep(.ppt-md-preview .md-editor-preview) {
  background: transparent !important;
  color: inherit !important;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

:deep(.ppt-md-preview) {
  font-size: 14px;
  min-width: 0;
  max-width: 100%;
}

:deep(.ppt-md-preview .md-editor-preview > :first-child) { margin-top: 0; }
:deep(.ppt-md-preview .md-editor-preview > :last-child) { margin-bottom: 0; }

/* ── 代码块：用 Vben token 重新着色 ── */
:deep(.ppt-md-preview pre) {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  background: hsl(var(--accent)) !important;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}

:deep(.ppt-md-preview pre code) {
  background: transparent !important;
  color: hsl(var(--foreground));
  word-break: normal;
  white-space: pre;
}

/* ── 表格滚动 + 跟随主题 ── */
:deep(.ppt-md-preview table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}
:deep(.ppt-md-preview th),
:deep(.ppt-md-preview td) {
  border: 1px solid hsl(var(--border)) !important;
  background: transparent !important;
}
:deep(.ppt-md-preview thead th) {
  background: hsl(var(--muted)) !important;
}

/* ── 行内代码 ── */
:deep(.ppt-md-preview p code),
:deep(.ppt-md-preview li code) {
  padding: 2px 6px;
  background: hsl(var(--accent)) !important;
  border-radius: 4px;
  font-size: 0.9em;
  color: hsl(var(--foreground));
}

/* ── 引用块 ── */
:deep(.ppt-md-preview blockquote) {
  background: transparent !important;
  border-left: 3px solid hsl(var(--border)) !important;
  color: hsl(var(--muted-foreground)) !important;
}
</style>

<!--
  非 scoped 覆盖：md-editor-v3 在 .md-editor 根元素上设置 background-color: var(--md-bk-color)，
  暗黑主题下 --md-bk-color 为 #000，导致气泡背景变黑。
  非 scoped 样式确保优先级足够覆盖库样式。
-->
<style>
.ppt-md-preview.md-editor,
.ppt-md-preview.md-editor-dark {
  background: transparent !important;
  background-color: transparent !important;
}

.ppt-md-preview.md-editor-dark .md-editor-preview {
  background: transparent !important;
  background-color: transparent !important;
}
</style>
