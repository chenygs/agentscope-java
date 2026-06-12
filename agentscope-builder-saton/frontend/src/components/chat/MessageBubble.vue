<script setup lang="ts">
import { NText, NTag } from 'naive-ui'
import type { ChatMessage } from '@/types'
import ToolCallCard from './ToolCallCard.vue'

defineProps<{ message: ChatMessage }>()
</script>

<template>
  <div class="message-row" :class="message.role">
    <!-- Avatar -->
    <div class="avatar">
      <span v-if="message.role === 'user'" class="avatar-icon user-avatar">👤</span>
      <span v-else class="avatar-icon assistant-avatar">🤖</span>
    </div>

    <!-- Bubble -->
    <div class="bubble" :class="message.role">
      <div class="bubble-header">
        <NTag size="small" :type="message.role === 'user' ? 'info' : 'success'" round>
          {{ message.role === 'user' ? 'You' : 'Assistant' }}
        </NTag>
        <NText depth="3" style="font-size: 11px; margin-left: 6px">
          {{ new Date(message.timestamp).toLocaleTimeString() }}
        </NText>
      </div>

      <!-- Message text -->
      <div class="bubble-text" v-if="message.text">
        <pre class="message-pre">{{ message.text }}</pre>
      </div>

      <!-- Tool calls -->
      <div v-if="message.toolCalls?.length" class="tool-calls">
        <ToolCallCard
          v-for="tc in message.toolCalls"
          :key="tc.callId"
          :tool-call="tc"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  max-width: 100%;
}
.message-row.user {
  flex-direction: row-reverse;
}
.message-row.user .bubble {
  align-items: flex-end;
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
.message-row.user .bubble-text {
  background-color: var(--n-color-target);
  border-color: var(--n-color-info);
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
