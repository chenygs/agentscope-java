<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { NScrollbar, NSpin, NEmpty, NText } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { ChatMessage, ToolCallInfo } from '@/types'
import MessageBubble from './MessageBubble.vue'
import ToolCallCard from './ToolCallCard.vue'

const props = defineProps<{
  messages: ChatMessage[]
  isStreaming: boolean
  streamingText: string
  streamingToolCalls: ToolCallInfo[]
}>()

const { t } = useI18n()
const scrollbarRef = ref<InstanceType<typeof NScrollbar> | null>(null)

// Auto-scroll to bottom when messages or streaming text changes
watch(
  () => [props.messages.length, props.streamingText, props.streamingToolCalls.length],
  async () => {
    await nextTick()
    scrollbarRef.value?.scrollTo({ top: 999999, behavior: 'smooth' })
  },
)
</script>

<template>
  <div class="message-list-wrapper">
    <NScrollbar ref="scrollbarRef" class="message-list-scrollbar">
      <div class="message-list-inner">
        <!-- Empty state -->
        <NEmpty v-if="messages.length === 0 && !isStreaming" description="Send a message to start the conversation" style="margin-top: 80px" />

        <!-- Completed messages -->
        <MessageBubble
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
        />

        <!-- Live streaming bubble -->
        <div v-if="isStreaming" class="message-row assistant">
          <div class="avatar">
            <span class="avatar-icon assistant-avatar">🤖</span>
          </div>
          <div class="bubble assistant">
            <div class="bubble-header">
              <NText strong style="font-size: 13px">Assistant</NText>
              <NSpin :size="12" style="margin-left: 6px" />
            </div>

            <!-- Streaming tool calls -->
            <div v-if="streamingToolCalls.length" class="tool-calls">
              <ToolCallCard
                v-for="tc in streamingToolCalls"
                :key="tc.callId"
                :tool-call="tc"
              />
            </div>

            <!-- Streaming text -->
            <div class="bubble-text" v-if="streamingText">
              <pre class="message-pre">{{ streamingText }}</pre>
            </div>

            <!-- No content yet spinner -->
            <div v-if="!streamingText && !streamingToolCalls.length" class="bubble-text">
              <NText depth="3">Thinking...</NText>
            </div>
          </div>
        </div>
      </div>
    </NScrollbar>
  </div>
</template>

<style scoped>
.message-list-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.message-list-scrollbar {
  height: 100%;
}
.message-list-inner {
  padding: 16px 20px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  max-width: 100%;
}
.message-row.assistant {
  flex-direction: row;
}

.avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-icon {
  font-size: 22px;
}

.bubble {
  display: flex;
  flex-direction: column;
  max-width: 70%;
  min-width: 0;
}

.bubble-header {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}

.bubble-text {
  background-color: var(--n-color-modal);
  border: 1px solid var(--n-border-color);
  border-radius: 12px;
  padding: 10px 14px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.message-pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.tool-calls {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 100%;
}
</style>
