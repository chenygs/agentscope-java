<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NSpace,
  NH3,
  NText,
  NSelect,
  NDivider,
  NLayout,
  NLayoutSider,
  NLayoutContent,
  useDialog,
  useMessage,
  useThemeVars,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useChat } from '@/composables/useChat'
import * as agentApi from '@/api/agent'
import * as modelApi from '@/api/resource'
import * as sessionApi from '@/api/session'
import type { AgentDetail, ModelProvider, Session } from '@/types'
import MessageList from '@/components/chat/MessageList.vue'
import Composer from '@/components/chat/Composer.vue'
import SessionList from '@/components/chat/SessionList.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const themeVars = useThemeVars()

const agentId = computed(() => Number(route.params.id))
const agent = ref<AgentDetail | null>(null)
const models = ref<ModelProvider[]>([])
const sessions = ref<Session[]>([])
const sessionsLoading = ref(false)

const {
  messages,
  isStreaming,
  isLoadingHistory,
  streamingText,
  streamingToolCalls,
  overrideModelId,
  sessionKey,
  send,
  abort,
  clearMessages,
  loadSession,
  newSession,
} = useChat(agentId.value)

// 拉取一次会话列表
async function refreshSessions() {
  sessionsLoading.value = true
  try {
    const resp = await sessionApi.listSessions(agentId.value)
    sessions.value = resp.data.data ?? []
  } catch {
    // interceptor 已处理
  } finally {
    sessionsLoading.value = false
  }
}

// 切换到一个已存在会话：拉历史并切 sessionKey
async function onSelect(key: string) {
  if (key === sessionKey.value) return
  await loadSession(key)
}

// 新建会话：sessionKey 切到新生成的，messages 清空。
// 真正落库要等用户发第一条消息（HarnessAgent 自动建 session）。
function onNew() {
  newSession()
}

// 删除会话：先确认，删完后刷新列表；如果删的是当前选中的，转去新建一个。
function onDelete(key: string) {
  dialog.warning({
    title: t('chat.deleteSession'),
    content: t('chat.deleteSessionConfirm', { key }),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await sessionApi.resetSession(agentId.value, key)
        message.success(t('chat.deleteSessionOk'))
        if (key === sessionKey.value) {
          newSession()
        }
        await refreshSessions()
      } catch {
        // interceptor 已处理
      }
    },
  })
}

// 进入页面：拉 agent 信息 + 模型列表 + 会话列表，并自动选中第一个或新建一个
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
  await refreshSessions()
  // 默认行为：有会话就选第一个，没有就新建一个临时 key
  if (sessions.value.length > 0) {
    await loadSession(sessions.value[0].sessionKey)
  } else {
    newSession()
  }
})

const modelOptions = computed(() =>
  models.value.map(m => ({ label: m.name, value: m.id })),
)

function goBack() {
  router.push({ name: 'Agents' })
}

// 用户发送一条消息：先做乐观更新（侧栏立刻出现/更新这条会话），再走真正的 stream。
// stream 结束后静默 refresh 一次，与服务器最终一致。
async function onSend(text: string) {
  const key = sessionKey.value
  if (key) {
    const idx = sessions.value.findIndex(s => s.sessionKey === key)
    if (idx === -1) {
      // 新会话：插到顶部，标题用本条消息内容
      sessions.value = [
        { sessionKey: key, lastActiveAt: Date.now(), title: text },
        ...sessions.value,
      ]
    } else if (!sessions.value[idx].title) {
      // 已存在但 title 为空（罕见兜底）：补上
      sessions.value[idx] = { ...sessions.value[idx], title: text }
    }
  }

  await send(text)
  await refreshSessions()
}
</script>

<template>
  <div class="chat-page">
    <NLayout has-sider class="chat-layout">
      <!-- 左侧会话列表 -->
      <NLayoutSider
        :width="240"
        :native-scrollbar="false"
        :collapsed-width="0"
        show-trigger="bar"
        bordered
      >
        <SessionList
          :sessions="sessions"
          :active-key="sessionKey"
          :loading="sessionsLoading"
          @select="onSelect"
          @new="onNew"
          @delete="onDelete"
        />
      </NLayoutSider>

      <!-- 右侧聊天区 -->
      <NLayoutContent class="chat-main">
        <!-- Header -->
        <div class="chat-header">
          <NSpace align="center" :wrap="false">
            <NButton quaternary @click="goBack">← {{ t('common.back') }}</NButton>
            <NDivider vertical />
            <NH3 style="margin: 0">{{ agent?.name ?? t('agent.chat') }}</NH3>
            <NText v-if="agent" depth="3" style="font-size: 13px">({{ agent.agentId }})</NText>
            <NText v-if="sessionKey" depth="3" style="font-size: 12px">· {{ sessionKey }}</NText>
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
        <div v-if="isLoadingHistory" class="chat-loading" :style="{ color: themeVars.textColor3 }">
          {{ t('chat.loadingHistory') }}
        </div>
        <MessageList
          v-else
          :messages="messages"
          :is-streaming="isStreaming"
          :streaming-text="streamingText"
          :streaming-tool-calls="streamingToolCalls"
          class="chat-messages"
        />

        <!-- Composer -->
        <Composer
          :is-streaming="isStreaming"
          @send="onSend"
          @abort="abort"
        />
      </NLayoutContent>
    </NLayout>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 48px);
  margin: -24px;
  overflow: hidden;
}

.chat-layout {
  height: 100%;
}

.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
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

.chat-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
</style>
