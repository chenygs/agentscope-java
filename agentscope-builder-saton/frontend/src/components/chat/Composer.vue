<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  disabled?: boolean
  isStreaming?: boolean
}>()

const emit = defineEmits<{
  send: [text: string]
  abort: []
}>()

const { t } = useI18n()
const inputText = ref('')

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  inputText.value = ''
}

function handleKeydown(e: KeyboardEvent) {
  // Enter without Shift = send
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="composer">
    <div class="composer-inner">
      <NInput
        v-model:value="inputText"
        type="textarea"
        :placeholder="t('chat.placeholder')"
        :autosize="{ minRows: 3, maxRows: 8 }"
        :disabled="disabled"
        @keydown="handleKeydown"
        class="composer-input"
      />
      <NButton
        v-if="isStreaming"
        type="error"
        @click="emit('abort')"
        :disabled="disabled"
        class="composer-btn"
      >
        {{ t('chat.stop') }}
      </NButton>
      <NButton
        v-else
        type="primary"
        @click="handleSend"
        :disabled="disabled || !inputText.trim()"
        class="composer-btn"
      >
        {{ t('chat.send') }}
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.composer {
  padding: 16px 20px;
  border-top: 1px solid var(--n-border-color);
  background-color: var(--n-color);
  flex-shrink: 0;
}
.composer-inner {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
.composer-input {
  flex: 1;
  min-width: 0;
}
.composer-btn {
  height: 42px;
  flex-shrink: 0;
}
</style>
