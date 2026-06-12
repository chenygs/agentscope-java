<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NText, NCollapse, NCollapseItem, NCode } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { ToolCallInfo } from '@/types'

const props = defineProps<{ toolCall: ToolCallInfo }>()
const { t } = useI18n()

const statusType = computed(() => {
  switch (props.toolCall.status) {
    case 'done': return 'success'
    case 'error': return 'error'
    case 'running': return 'warning'
    default: return 'default'
  }
})

const statusLabel = computed(() => {
  switch (props.toolCall.status) {
    case 'done': return t('chat.toolResult')
    case 'error': return t('chat.toolError')
    case 'running': return t('chat.toolCall')
    default: return props.toolCall.status
  }
})
</script>

<template>
  <NCard size="small" class="tool-call-card" :bordered="true">
    <div class="tool-call-header">
      <NTag size="small" :type="statusType" round>{{ statusLabel }}</NTag>
      <NText strong style="font-size: 13px; margin-left: 8px">{{ toolCall.toolName }}</NText>
    </div>
    <NCollapse v-if="toolCall.args || toolCall.result" :default-expanded-names="[]" arrow-placement="left">
      <NCollapseItem v-if="toolCall.args" :title="t('agent.propsJson')" name="args">
        <NCode :code="toolCall.args" language="json" word-wrap />
      </NCollapseItem>
      <NCollapseItem v-if="toolCall.result" :title="t('chat.toolResult')" name="result">
        <NCode :code="toolCall.result" language="text" word-wrap />
      </NCollapseItem>
    </NCollapse>
  </NCard>
</template>

<style scoped>
.tool-call-card {
  margin: 4px 0;
}
.tool-call-header {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
</style>
