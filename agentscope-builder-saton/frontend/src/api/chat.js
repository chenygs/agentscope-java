import { fetchEventSource } from '@microsoft/fetch-event-source';
import client from './client';
/** POST /api/agents/:id/chat/send (blocking) */
export function sendChat(agentId, req) {
    return client.post(`/api/agents/${agentId}/chat/send`, req);
}
/** POST /api/agents/:id/chat/stream (SSE) */
export async function streamChat(agentId, req, signal, callbacks) {
    await fetchEventSource(`/api/agents/${agentId}/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(req),
        signal,
        openWhenHidden: true,
        onmessage(ev) {
            if (ev.event && ev.data) {
                callbacks.onEvent(ev.event, ev.data);
            }
        },
        onerror(err) {
            callbacks.onError?.(err);
            throw err; // stop automatic retry
        },
        onclose() {
            callbacks.onDone?.();
        },
        // Prevent fetchEventSource from retrying on HTTP errors
        async onopen(response) {
            if (response.ok)
                return;
            const text = await response.text().catch(() => '');
            throw new Error(`SSE stream failed: ${response.status} ${text}`);
        },
    });
}
