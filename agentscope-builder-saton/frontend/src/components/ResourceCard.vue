<script setup lang="ts">
import { NCard, NButton, NSpace, NText, NTag, NPopconfirm, NDivider } from 'naive-ui'
import { useI18n } from 'vue-i18n'

defineProps<{
  title: string
  type: string
  propsSummary?: Record<string, unknown>
}>()

const emit = defineEmits<{
  edit: []
  delete: []
}>()

const { t } = useI18n()

/** 将 props 对象转成简短摘要行，最多显示 3 个 */
function summaryEntries(props?: Record<string, unknown>): Array<{ key: string; value: string }> {
  if (!props) return []
  return Object.entries(props)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .slice(0, 3)
    .map(([key, value]) => ({
      key,
      value: typeof value === 'string' ? value : JSON.stringify(value),
    }))
}
</script>

<template>
  <NCard size="small" hoverable style="height: 100%">
    <div style="display: flex; justify-content: space-between; align-items: flex-start">
      <div style="flex: 1; min-width: 0">
        <NText strong style="font-size: 15px">{{ title }}</NText>
      </div>
      <NTag :bordered="false" size="small" type="info">{{ type }}</NTag>
    </div>

    <div v-if="summaryEntries(propsSummary).length" style="margin-top: 12px">
      <div
        v-for="entry in summaryEntries(propsSummary)"
        :key="entry.key"
        style="display: flex; gap: 8px; font-size: 13px; line-height: 1.8"
      >
        <NText depth="3" style="min-width: 70px; flex-shrink: 0">{{ entry.key }}:</NText>
        <NText style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
          {{ entry.value }}
        </NText>
      </div>
    </div>

    <NDivider style="margin: 12px 0" />

    <NSpace :size="4" justify="end">
      <NButton text type="primary" size="small" @click="emit('edit')">{{ t('common.edit') }}</NButton>
      <NPopconfirm @positive-click="emit('delete')">
        <template #trigger>
          <NButton text type="error" size="small">{{ t('common.delete') }}</NButton>
        </template>
        {{ t('tools.deleteConfirm') }}
      </NPopconfirm>
    </NSpace>
  </NCard>
</template>
