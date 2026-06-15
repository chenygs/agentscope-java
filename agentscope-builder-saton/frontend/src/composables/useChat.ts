import { ref, shallowRef } from 'vue'
import { streamChat } from '@/api/chat'
import { loadHistory } from '@/api/session'
import type { ChatMessage, ToolCallInfo, ChatSendReq } from '@/types'

let _msgCounter = 0
function nextId() { return `msg_${++_msgCounter}_${Date.now()}` }

/** 生成新会话 key —— 短随机串足够区分本人下的会话。 */
function genSessionKey() {
  return 's_' + Math.random().toString(36).slice(2, 10)
}

/**
 * useChat composable — manages SSE streaming state for one agent conversation.
 */
export function useChat(agentId: number) {
  const messages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const streamingToolCalls = ref<ToolCallInfo[]>([])
  const overrideModelId = ref<number | undefined>()
  const sessionKey = ref<string | undefined>()
  /** 是否正在加载历史消息（切换会话时显示 loading）。 */
  const isLoadingHistory = ref(false)

  let ctrl: AbortController | null = null
  // Current assistant message being built (not yet in messages array until finalized)
  let currentAssistant: ChatMessage | null = null

  function startTurn() {
    isStreaming.value = true
    streamingText.value = ''
    streamingToolCalls.value = []
    currentAssistant = {
      id: nextId(),
      role: 'assistant',
      text: '',
      timestamp: Date.now(),
      toolCalls: [],
    }
  }

  function finalizeTurn(error?: string) {
    if (currentAssistant) {
      if (error) {
        currentAssistant.text += `\n\n[Error: ${error}]`
      } else {
        currentAssistant.text = streamingText.value
      }
      currentAssistant.toolCalls = [...streamingToolCalls.value]
      messages.value.push({ ...currentAssistant })
      currentAssistant = null
    }
    isStreaming.value = false
    streamingText.value = ''
    streamingToolCalls.value = []
  }

  // ── Current tool call being built (for delta accumulation) ──
  let activeToolCallId: string | null = null
  let toolInputBuf = ''

  // ── Current tool result being built ──
  let activeResultToolCallId: string | null = null
  let resultBuf = ''

  function handleEvent(event: string, rawData: string) {
    let data: Record<string, unknown>
    try { data = JSON.parse(rawData) } catch { return }

    switch (event) {
      // ── Text streaming ──
      case 'text_block_delta': {
        const delta = (data.delta as string) ?? ''
        streamingText.value += delta
        break
      }
      case 'text_block_start':
      case 'text_block_end':
        // no-op, delta does the work
        break

      // ── Thinking (treat like text but prefixed) ──
      case 'thinking_block_delta': {
        const delta = (data.delta as string) ?? ''
        // Optionally show thinking, for now we skip
        break
      }

      // ── Tool call input streaming ──
      case 'tool_call_start': {
        const callId = (data.toolCallId as string) ?? nextId()
        const toolName = (data.toolCallName as string) ?? 'unknown'
        activeToolCallId = callId
        toolInputBuf = ''
        streamingToolCalls.value.push({
          callId,
          toolName,
          status: 'running',
          args: '',
          result: '',
        })
        break
      }
      case 'tool_call_delta': {
        const delta = (data.delta as string) ?? ''
        toolInputBuf += delta
        // Update the active tool call's args
        const tc = streamingToolCalls.value.find(t => t.callId === activeToolCallId)
        if (tc) tc.args = toolInputBuf
        break
      }
      case 'tool_call_end': {
        const tc = streamingToolCalls.value.find(t => t.callId === activeToolCallId)
        if (tc) tc.status = 'running' // still running until result comes
        activeToolCallId = null
        toolInputBuf = ''
        break
      }

      // ── Tool result streaming ──
      case 'tool_result_start': {
        activeResultToolCallId = (data.toolCallId as string) ?? activeToolCallId
        resultBuf = ''
        break
      }
      case 'tool_result_text_delta': {
        const delta = (data.delta as string) ?? ''
        resultBuf += delta
        const tc = streamingToolCalls.value.find(t => t.callId === activeResultToolCallId)
        if (tc) tc.result = resultBuf
        break
      }
      case 'tool_result_end': {
        const tc = streamingToolCalls.value.find(t => t.callId === activeResultToolCallId)
        if (tc) {
          tc.status = 'done'
          tc.result = resultBuf
        }
        activeResultToolCallId = null
        resultBuf = ''
        break
      }

      // ── Agent lifecycle ──
      case 'agent_end':
      case 'agent_result':
        finalizeTurn()
        break

      case 'exceed_max_iters':
        finalizeTurn('Exceeded max iterations')
        break

      default:
        // Ignore unknown events (model_call_start, hint_block, etc.)
        break
    }
  }

  async function send(text: string) {
    if (!text.trim() || isStreaming.value) return

    // Append user message
    messages.value.push({
      id: nextId(),
      role: 'user',
      text: text.trim(),
      timestamp: Date.now(),
      toolCalls: [],
    })

    startTurn()

    ctrl = new AbortController()
    const req: ChatSendReq = {
      message: text.trim(),
      overrideModelProviderId: overrideModelId.value,
      sessionKey: sessionKey.value,
    }

    try {
      await streamChat(agentId, req, ctrl.signal, {
        onEvent: handleEvent,
        onError: (err) => finalizeTurn(String(err)),
        onDone: () => {
          // If stream closed without agent_end event, finalize
          if (isStreaming.value) finalizeTurn()
        },
      })
    } catch {
      // streamChat throws on HTTP error or abort
      if (isStreaming.value) finalizeTurn('Connection failed')
    }
  }

  function abort() {
    ctrl?.abort()
    ctrl = null
    if (isStreaming.value) finalizeTurn('Aborted')
  }

  function clearMessages() {
    messages.value = []
  }

  /**
   * 切换到指定 sessionKey 并拉取历史消息覆盖当前 messages。
   * 不传 key 时只清空（用于"开始新会话"前的 UI 状态）。
   */
  async function loadSession(key: string) {
    if (isStreaming.value) abort()
    sessionKey.value = key
    isLoadingHistory.value = true
    try {
      const resp = await loadHistory(agentId, key)
      messages.value = resp.data.data ?? []
    } catch {
      // 拉取失败时清空，避免残留上一会话内容
      messages.value = []
    } finally {
      isLoadingHistory.value = false
    }
  }

  /**
   * 开新会话：生成新 key、清空消息。第一次发消息时后端会自动创建对应 session。
   * 返回新 key，调用方可立即把它选中。
   */
  function newSession(): string {
    if (isStreaming.value) abort()
    const key = genSessionKey()
    sessionKey.value = key
    messages.value = []
    return key
  }

  return {
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
  }
}
