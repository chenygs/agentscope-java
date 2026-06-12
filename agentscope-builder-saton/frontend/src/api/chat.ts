import { fetchEventSource } from '@microsoft/fetch-event-source'
import client from './client'
import type { ApiResponse } from './client'
import type { ChatSendReq } from '@/types'

/** POST /api/agents/:id/chat/send (blocking) */
export function sendChat(agentId: number, req: ChatSendReq) {
  return client.post<ApiResponse<{ reply: string }>>(`/api/agents/${agentId}/chat/send`, req)
}

/**
 * SSE event handler type.
 * The backend emits events with names = lowercase AgentEventType (e.g. "text_block_delta").
 */
export interface SSECallbacks {
  onEvent: (event: string, data: string) => void
  onError?: (err: unknown) => void
  onDone?: () => void
}

/** POST /api/agents/:id/chat/stream (SSE) */
export async function streamChat(
  agentId: number,
  req: ChatSendReq,
  signal: AbortSignal,
  callbacks: SSECallbacks,
): Promise<void> {
  await fetchEventSource(`/api/agents/${agentId}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(req),
    signal,
    openWhenHidden: true,

    onmessage(ev) {
      if (ev.event && ev.data) {
        callbacks.onEvent(ev.event, ev.data)
      }
    },

    onerror(err) {
      callbacks.onError?.(err)
      throw err // stop automatic retry
    },

    onclose() {
      callbacks.onDone?.()
    },

    // Prevent fetchEventSource from retrying on HTTP errors
    async onopen(response) {
      if (response.ok) return
      const text = await response.text().catch(() => '')
      throw new Error(`SSE stream failed: ${response.status} ${text}`)
    },
  })
}
