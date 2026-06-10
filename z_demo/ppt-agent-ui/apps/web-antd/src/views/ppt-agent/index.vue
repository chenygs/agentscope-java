<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Avatar,
  Button,
  message,
  notification,
  Radio,
  Textarea,
} from 'ant-design-vue';

import { AguiClient, type AguiMessage } from '#/api/agui/client';
import {
  type ChatSession,
  type ChatMessage,
  createSessionApi,
  deleteSessionApi,
  fetchMessagesApi,
  fetchSessionsApi,
} from '#/api/chat';

import MarkdownView from './MarkdownView.vue';

import {
  downloadBlob,
  hasSlidesJson,
  parseSlidesJson,
  renderPptx,
} from './renderer';

// ─────────────── 本地消息类型（扩展后端 ChatMessage，含 reasoning/tool 等前端临时角色） ───────────────

interface LocalMessage {
  id: number | string;
  role: string;
  content: string;
}

interface SlideInfo {
  title: string;
  outputFile: string;
  slideCount: number;
  data: any;
}

// ─────────────── State ───────────────

const userStore = useUserStore();
const userId = computed(() => userStore.userInfo?.id ?? 0);

const sessions = ref<ChatSession[]>([]);
const currentSessionId = ref<number | null>(null);
const currentMessages = ref<LocalMessage[]>([]);
const sessionsLoaded = ref(false);

const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value),
);

const inputText = ref('');
const isRunning = ref(false);
const showReasoning = ref(false);
const showTools = ref(true);
const displayMode = ref<'markdown' | 'text'>('markdown');

// 各角色的头像图标和渐变背景
function avatarIcon(role: string): string {
  switch (role) {
    case 'ASSISTANT': { return 'lucide:bot'; }
    case 'REASONING': { return 'lucide:brain'; }
    case 'TOOL': { return 'lucide:wrench'; }
    case 'USER': { return 'lucide:user'; }
    default: { return 'lucide:bot'; }
  }
}

function avatarStyle(role: string): string {
  switch (role) {
    case 'ASSISTANT': { return 'linear-gradient(135deg, #10b981 0%, #06b6d4 100%)'; }
    case 'REASONING': { return 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)'; }
    case 'TOOL': { return 'linear-gradient(135deg, #f59e0b 0%, #ef4444 100%)'; }
    case 'USER': { return 'linear-gradient(135deg, #3b82f6 0%, #6366f1 100%)'; }
    default: { return 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)'; }
  }
}

// 当前流式渲染中的消息引用
const streamingMessageId = ref<string | null>(null);
const streamingReasoningId = ref<string | null>(null);

// 每条消息的下载状态（key=消息id）
const downloadingMap = ref<Record<string, boolean>>({});

// 从消息内容提取 slide 信息
function extractSlideInfo(content: string): SlideInfo | null {
  const data = parseSlidesJson(content);
  if (!data) return null;
  return {
    title: data.title || '未命名',
    outputFile: data.outputFile || 'presentation.pptx',
    slideCount: data.slides.length,
    data,
  };
}

async function downloadFromMessage(msg: LocalMessage) {
  const info = extractSlideInfo(msg.content);
  if (!info) return;
  const msgId = String(msg.id);
  downloadingMap.value[msgId] = true;
  try {
    const blob = await renderPptx(info.data);
    downloadBlob(blob, info.outputFile);
  } catch (e: any) {
    notification.error({
      message: '生成 PPT 失败',
      description: e.message || String(e),
    });
  } finally {
    downloadingMap.value[msgId] = false;
  }
}

const messageListEl = ref<HTMLDivElement | null>(null);

const client = new AguiClient('/agui/run');

// ─────────────── 初始化：从后端加载会话列表 ───────────────

onMounted(async () => {
  try {
    const list = await fetchSessionsApi();
    sessions.value = list;
    if (list.length > 0) {
      await switchToSession(list[0].id);
    }
  } catch {
    // ignore
  } finally {
    sessionsLoaded.value = true;
  }
});

// ─────────────── 会话管理 ───────────────

async function newChat() {
  if (isRunning.value) {
    message.warning('请先停止当前对话');
    return;
  }
  try {
    const session = await createSessionApi('ppt-agent');
    sessions.value.unshift(session);
    await switchToSession(session.id);
  } catch (e: any) {
    notification.error({ message: '创建会话失败', description: e.message || String(e) });
  }
}

async function switchConversation(id: number) {
  if (isRunning.value) {
    message.warning('请先停止当前对话');
    return;
  }
  await switchToSession(id);
}

async function switchToSession(id: number) {
  currentSessionId.value = id;
  try {
    const msgs = await fetchMessagesApi(id);
    currentMessages.value = msgs.map((m) => ({ id: m.id, role: m.role, content: m.content }));
  } catch {
    currentMessages.value = [];
  }
  // 检测最新 assistant 消息是否含 slides.json
  for (let i = currentMessages.value.length - 1; i >= 0; i--) {
    const m = currentMessages.value[i];
    if (m && m.role === 'ASSISTANT') {
      checkSlidesJson(m.content);
      break;
    }
  }
  scrollToBottom();
}

async function deleteConversation(id: number) {
  try {
    await deleteSessionApi(id);
  } catch {
    // ignore
  }
  sessions.value = sessions.value.filter((s) => s.id !== id);
  if (currentSessionId.value === id) {
    if (sessions.value.length > 0) {
      await switchToSession(sessions.value[0].id);
    } else {
      currentSessionId.value = null;
      currentMessages.value = [];
    }
  }
}

// ─────────────── 消息流 ───────────────

function appendLocalMessage(role: string, content: string): LocalMessage {
  const msg: LocalMessage = {
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content,
  };
  currentMessages.value.push(msg);
  scrollToBottom();
  return msg;
}

function updateLocalMessage(id: number | string, append: string) {
  const m = currentMessages.value.find((x) => x.id === id);
  if (m) {
    m.content += append;
    scrollToBottom();
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListEl.value) {
      messageListEl.value.scrollTop = messageListEl.value.scrollHeight;
    }
  });
}

// ─────────────── slides.json 检测（已弃用，改用消息内嵌按钮） ───────────────

function checkSlidesJson(_text: string) {
  // no-op：下载按钮已嵌入到每条含 ```slides.json 的 assistant 消息中
}

// ─────────────── 发送消息 ───────────────

async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || isRunning.value) return;

  // 没有会话时自动创建
  if (!currentSessionId.value) {
    try {
      const session = await createSessionApi('ppt-agent');
      sessions.value.unshift(session);
      currentSessionId.value = session.id;
    } catch (e: any) {
      notification.error({ message: '创建会话失败', description: e.message || String(e) });
      return;
    }
  }

  inputText.value = '';
  isRunning.value = true;

  // 添加用户消息到本地列表（后端通过 ChatPersistenceMiddleware 自动持久化）
  appendLocalMessage('USER', text);

  const conv = currentSession.value;
  if (!conv) return;

  // 转成 AG-UI 消息格式
  const aguiMessages: AguiMessage[] = currentMessages.value
    .filter((m) => m.role === 'USER' || m.role === 'ASSISTANT')
    .map((m) => ({
      id: String(m.id),
      role: m.role as 'assistant' | 'user',
      content: m.content,
    }));

  let assistantMsgId: null | string = null;
  let reasoningMsgId: null | string = null;
  let assistantContent = '';

  try {
    await client.run(
      {
        threadId: `${userId.value}:${conv.id}`,
        runId: `run-${Date.now()}`,
        messages: aguiMessages,
      },
      {
        onReasoningMessageStart: () => {
          if (showReasoning.value) {
            reasoningMsgId = null;
          }
        },
        onReasoningContent: (delta) => {
          if (!showReasoning.value) return;
          if (!reasoningMsgId) {
            reasoningMsgId = appendLocalMessage('REASONING', delta).id as string;
            streamingReasoningId.value = reasoningMsgId;
          } else {
            updateLocalMessage(reasoningMsgId, delta);
          }
        },
        onReasoningMessageEnd: () => {
          streamingReasoningId.value = null;
        },
        onTextMessageStart: () => {
          assistantMsgId = null;
          assistantContent = '';
        },
        onTextContent: (delta) => {
          assistantContent += delta;
          if (!assistantMsgId) {
            assistantMsgId = appendLocalMessage('ASSISTANT', delta).id as string;
            streamingMessageId.value = assistantMsgId;
          } else {
            updateLocalMessage(assistantMsgId, delta);
          }
        },
        onTextMessageEnd: async () => {
          streamingMessageId.value = null;
          if (assistantContent && currentSessionId.value) {
            // assistant 消息由后端 ChatPersistenceMiddleware 自动持久化
            checkSlidesJson(assistantContent);
          }
        },
        onToolCallStart: (_id, toolName) => {
          if (showTools.value) {
            appendLocalMessage('TOOL', `🔧 调用工具: ${toolName}`);
          }
        },
        onRunFinished: () => {
          // client.run() 会随后 resolve
        },
        onError: (err) => {
          notification.error({ message: '生成出错', description: err });
        },
      },
    );
  } catch (e: any) {
    // 手动停止或异常，后端通过 ChatPersistenceMiddleware 自动持久化
    notification.error({
      message: '请求失败',
      description: e.message || String(e),
    });
  } finally {
    isRunning.value = false;
    streamingMessageId.value = null;
    streamingReasoningId.value = null;
  }
}

function stopGeneration() {
  client.abort();
  isRunning.value = false;
  streamingMessageId.value = null;
  streamingReasoningId.value = null;
}

// 回车发送 (Shift+Enter 换行)
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    sendMessage();
  }
}
</script>

<template>
  <Page
    auto-content-height
    description="基于 AgentScope HarnessAgent + AG-UI 协议的智能 PPT 生成助手"
    title="PPT 智能体"
  >
    <div class="flex h-full overflow-hidden rounded-lg border border-border bg-card">
      <!-- ─────────── 左侧：会话列表 ─────────── -->
      <div class="flex w-64 flex-col border-r border-border bg-muted/30">
        <div class="border-b border-border p-3">
          <Button block type="primary" @click="newChat">+ 新对话</Button>
        </div>
        <div class="flex-1 overflow-y-auto p-2">
          <div
            v-for="conv in sessions"
            :key="conv.id"
            class="group mb-1 cursor-pointer rounded-md p-2 hover:bg-accent"
            :class="{
              'bg-accent text-accent-foreground':
                conv.id === currentSessionId,
            }"
            @click="switchConversation(conv.id)"
          >
            <div class="flex items-center justify-between">
              <span class="truncate text-sm">{{ conv.title }}</span>
              <Button
                v-if="sessions.length > 1"
                type="text"
                size="small"
                class="opacity-0 group-hover:opacity-100"
                @click.stop="deleteConversation(conv.id)"
              >
                ×
              </Button>
            </div>
            <div class="text-xs text-muted-foreground">
              {{ new Date(conv.createdAt).toLocaleDateString() }}
            </div>
          </div>
        </div>
      </div>

      <!-- ─────────── 中间：消息区 ─────────── -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <!-- 顶部工具栏 -->
        <div
          class="flex items-center justify-between border-b border-border px-4 py-2"
        >
          <div class="text-sm font-medium">
            {{ currentSession?.title || '新对话' }}
          </div>
          <div class="flex items-center gap-3 text-xs">
            <label class="flex items-center gap-1">
              <input v-model="showReasoning" type="checkbox" />
              思考过程
            </label>
            <label class="flex items-center gap-1">
              <input v-model="showTools" type="checkbox" />
              工具调用
            </label>
            <Radio.Group
              v-model:value="displayMode"
              :button-style="'solid'"
              size="small"
            >
              <Radio.Button value="markdown">Markdown</Radio.Button>
              <Radio.Button value="text">纯文本</Radio.Button>
            </Radio.Group>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messageListEl" class="flex-1 space-y-3 overflow-y-auto p-4">
          <div
            v-if="currentMessages.length === 0"
            class="flex h-full items-center justify-center text-muted-foreground"
          >
            <div class="text-center">
              <div class="mb-2 text-2xl">📊</div>
              <div>告诉我你想做的 PPT 主题，我来生成</div>
              <div class="mt-1 text-xs">
                示例：做一份《2026 AI 趋势》的演示文稿，科技风
              </div>
            </div>
          </div>

          <div
            v-for="msg in currentMessages"
            :key="msg.id"
            class="flex gap-3"
            :class="{ 'flex-row-reverse': msg.role === 'USER' }"
          >
            <Avatar
              :size="36"
              :style="{
                background: avatarStyle(msg.role),
                flexShrink: 0,
                boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }"
            >
              <IconifyIcon
                :icon="avatarIcon(msg.role)"
                style="color: white; font-size: 18px"
              />
            </Avatar>
            <div
              class="min-w-0 max-w-[75%] overflow-hidden break-words rounded-lg px-3 py-2 text-sm"
              :class="{
                'bg-primary text-primary-foreground whitespace-pre-wrap':
                  msg.role === 'USER',
                'bg-muted': msg.role === 'ASSISTANT',
                'bg-indigo-50 text-indigo-900 dark:bg-indigo-900/30 dark:text-indigo-200 italic whitespace-pre-wrap':
                  msg.role === 'REASONING',
                'bg-amber-50 text-amber-900 dark:bg-amber-900/30 dark:text-amber-200 whitespace-pre-wrap':
                  msg.role === 'TOOL',
              }"
            >
              <div class="flex flex-col gap-2">
                <MarkdownView
                  v-if="msg.role === 'ASSISTANT' && displayMode === 'markdown'"
                  :content="msg.content"
                />
                <template v-else>{{ msg.content }}</template>
                <!-- 含 ```slides.json 的消息显示下载按钮 -->
                <div
                  v-if="msg.role === 'ASSISTANT' && hasSlidesJson(msg.content)"
                  class="mt-2 flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 dark:border-emerald-800 dark:bg-emerald-900/20"
                >
                  <span class="text-lg">📊</span>
                  <span class="flex-1 text-xs text-muted-foreground">
                    {{ extractSlideInfo(msg.content)?.title || 'PPT' }} ·
                    {{ extractSlideInfo(msg.content)?.slideCount || 0 }} 页
                  </span>
                  <Button
                    :loading="downloadingMap[String(msg.id)]"
                    size="small"
                    type="primary"
                    @click="downloadFromMessage(msg)"
                  >
                    ↓ 下载
                  </Button>
                </div>
                <span
                  v-if="
                    msg.id === streamingMessageId ||
                    msg.id === streamingReasoningId
                  "
                  class="ml-1 inline-block animate-pulse"
                  >▍</span
                >
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="border-t border-border p-3">
          <Textarea
            v-model:value="inputText"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            :disabled="isRunning"
            placeholder="描述你想做的 PPT，回车发送，Shift+回车换行"
            @keydown="handleKeydown"
          />
          <div class="mt-2 flex justify-end gap-2">
            <Button v-if="isRunning" danger @click="stopGeneration">
              停止
            </Button>
            <Button
              v-else
              type="primary"
              :disabled="!inputText.trim()"
              @click="sendMessage"
            >
              发送
            </Button>
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
