<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSpace, NH3, NText, NSelect, NDivider } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useChat } from '@/composables/useChat'
import * as agentApi from '@/api/agent'
import * as modelApi from '@/api/resource'
import type { AgentDetail, ModelProvider } from '@/types'
import MessageList from '@/components/chat/MessageList.vue'
import Composer from '@/components/chat/Composer.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const agentId = computed(() => Number(route.params.id))
const agent = ref<AgentDetail | null>(null)
const models = ref<ModelProvider[]>([])

const {
  messages,
  isStreaming,
  streamingText,
  streamingToolCalls,
  overrideModelId,
  send,
  abort,
  clearMessages,
} = useChat(agentId.value)

// Load agent info + available models
onMounted(async () => {
  try {
    const [agentResp, modelResp] = await Promise.all([
      agentApi.getAgent(agentId.value),
      modelApi.listModels(),
    ])
    agent.value = agentResp.data.data
    models.value = modelResp.data.data ?? []
  } catch {
    // handled by interceptor
  }
})

const modelOptions = computed(() =>
  models.value.map(m => ({ label: m.name, value: m.id })),
)

function goBack() {
  router.push({ name: 'Agents' })
}
</script>

<template>
  <div class="chat-page">
    <!-- Header -->
    <div class="chat-header">
      <NSpace align="center" :wrap="false">
        <NButton quaternary @click="goBack">← {{ t('common.back') }}</NButton>
        <NDivider vertical />
        <NH3 style="margin: 0">{{ agent?.name ?? t('agent.chat') }}</NH3>
        <NText v-if="agent" depth="3" style="font-size: 13px">({{ agent.agentId }})</NText>
      </NSpace>
      <NSpace align="center" :wrap="false">
        <NSelect
          v-model:value="overrideModelId"
          :options="modelOptions"
          :placeholder="t('chat.modelOverride')"
          clearable
          size="small"
          style="width: 180px"
        />
        <NButton size="small" @click="clearMessages" :disabled="isStreaming">
          {{ t('common.refresh') }}
        </NButton>
      </NSpace>
    </div>

    <!-- Messages -->
    <MessageList
      :messages="messages"
      :is-streaming="isStreaming"
      :streaming-text="streamingText"
      :streaming-tool-calls="streamingToolCalls"
      class="chat-messages"
    />

    <!-- Composer -->
    <Composer
      :is-streaming="isStreaming"
      @send="send"
      @abort="abort"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 48px);
  margin: -24px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--n-border-color);
  flex-shrink: 0;
}

.chat-messages {
  flex: 1;
  min-height: 0;
}
</style>
