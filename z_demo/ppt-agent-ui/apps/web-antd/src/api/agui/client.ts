/**
 * AG-UI 协议客户端 (TypeScript 版本)
 *
 * 对接 ppt-agent 后端的 /agui/run SSE 端点。
 *
 * 协议参考: https://docs.ag-ui.com/
 */

import { useAccessStore } from '@vben/stores';

export interface AguiMessage {
  id: string;
  role: 'assistant' | 'system' | 'user';
  content: string;
}

export interface AguiRunInput {
  threadId: string;
  runId: string;
  messages: AguiMessage[];
  tools?: any[];
  context?: any;
  state?: any;
  forwardedProps?: any;
}

export interface AguiCallbacks {
  onRunStarted?: (threadId: string, runId: string) => void;
  onRunFinished?: (threadId: string, runId: string) => void;
  onTextMessageStart?: (messageId: string, role: string) => void;
  onTextContent?: (delta: string, messageId: string) => void;
  onTextMessageEnd?: (messageId: string) => void;
  onReasoningMessageStart?: (messageId: string, role: string) => void;
  onReasoningContent?: (delta: string, messageId: string) => void;
  onReasoningMessageEnd?: (messageId: string) => void;
  onToolCallStart?: (toolCallId: string, toolName: string) => void;
  onToolCallArgs?: (toolCallId: string, delta: string) => void;
  onToolCallEnd?: (toolCallId: string) => void;
  onStateSnapshot?: (snapshot: any) => void;
  onStateDelta?: (delta: any) => void;
  onRawEvent?: (rawEvent: any) => void;
  onError?: (error: string) => void;
}

export class AguiClient {
  private endpoint: string;
  private abortController: AbortController | null = null;
  private running = false;

  constructor(endpoint = '/agui/run') {
    this.endpoint = endpoint;
  }

  isRunning(): boolean {
    return this.running;
  }

  abort(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.running = false;
  }

  async run(input: AguiRunInput, callbacks: AguiCallbacks): Promise<void> {
    this.abort();
    this.abortController = new AbortController();
    this.running = true;

    const accessStore = useAccessStore();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    };
    if (accessStore.accessToken) {
      headers.Authorization = `Bearer ${accessStore.accessToken}`;
    }

    try {
      const response = await fetch(this.endpoint, {
        method: 'POST',
        headers,
        body: JSON.stringify(input),
        signal: this.abortController.signal,
      });

      if (!response.ok) {
        const errorText = await response.text();
        callbacks.onError?.(`HTTP ${response.status}: ${errorText}`);
        return;
      }

      if (!response.body) {
        callbacks.onError?.('Response body is empty');
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // 分隔事件：标准 \n\n 或 Windows 风格 \r\n\r\n
        let separator: number;
        while (true) {
          const lf = buffer.indexOf('\n\n');
          const crlf = buffer.indexOf('\r\n\r\n');
          if (lf === -1 && crlf === -1) break;
          if (lf === -1) {
            separator = crlf;
            const eventStr = buffer.slice(0, separator);
            buffer = buffer.slice(separator + 4);
            this.handleEventBlock(eventStr, callbacks);
          } else if (crlf === -1 || lf < crlf) {
            separator = lf;
            const eventStr = buffer.slice(0, separator);
            buffer = buffer.slice(separator + 2);
            this.handleEventBlock(eventStr, callbacks);
          } else {
            separator = crlf;
            const eventStr = buffer.slice(0, separator);
            buffer = buffer.slice(separator + 4);
            this.handleEventBlock(eventStr, callbacks);
          }
        }
      }
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        callbacks.onError?.(error.message || String(error));
      }
    } finally {
      this.running = false;
      this.abortController = null;
    }
  }

  private handleEventBlock(block: string, callbacks: AguiCallbacks): void {
    // SSE 事件块：data: {...}\ndata: {...}
    const lines = block.split(/\r?\n/);
    let dataStr = '';
    for (const line of lines) {
      if (line.startsWith('data:')) {
        dataStr += line.slice(5).trim();
      }
    }
    if (!dataStr) return;

    try {
      const event = JSON.parse(dataStr);
      this.dispatchEvent(event, callbacks);
    } catch (e) {
      console.warn('Failed to parse SSE event:', dataStr, e);
    }
  }

  private dispatchEvent(event: any, callbacks: AguiCallbacks): void {
    callbacks.onRawEvent?.(event);

    switch (event.type) {
      case 'RUN_STARTED': {
        callbacks.onRunStarted?.(event.threadId, event.runId);
        break;
      }
      case 'RUN_FINISHED': {
        callbacks.onRunFinished?.(event.threadId, event.runId);
        // 后端发完 RUN_FINISHED 不会关 HTTP 连接（持久 SSE 流），
        // 主动 abort 让 reader.read() 返回 done=true，client.run() 才能正常完成
        this.abort();
        break;
      }
      case 'TEXT_MESSAGE_START': {
        callbacks.onTextMessageStart?.(event.messageId, event.role);
        break;
      }
      case 'TEXT_MESSAGE_CONTENT': {
        callbacks.onTextContent?.(event.delta, event.messageId);
        break;
      }
      case 'TEXT_MESSAGE_END': {
        callbacks.onTextMessageEnd?.(event.messageId);
        break;
      }
      case 'REASONING_MESSAGE_START': {
        callbacks.onReasoningMessageStart?.(event.messageId, event.role);
        break;
      }
      case 'REASONING_MESSAGE_CONTENT': {
        callbacks.onReasoningContent?.(event.delta, event.messageId);
        break;
      }
      case 'REASONING_MESSAGE_END': {
        callbacks.onReasoningMessageEnd?.(event.messageId);
        break;
      }
      case 'TOOL_CALL_START': {
        callbacks.onToolCallStart?.(event.toolCallId, event.toolCallName);
        break;
      }
      case 'TOOL_CALL_ARGS': {
        callbacks.onToolCallArgs?.(event.toolCallId, event.delta);
        break;
      }
      case 'TOOL_CALL_END': {
        callbacks.onToolCallEnd?.(event.toolCallId);
        break;
      }
      case 'STATE_SNAPSHOT': {
        callbacks.onStateSnapshot?.(event.snapshot);
        break;
      }
      case 'STATE_DELTA': {
        callbacks.onStateDelta?.(event.delta);
        break;
      }
      case 'RAW': {
        if (event.rawEvent?.error) {
          callbacks.onError?.(event.rawEvent.error);
        }
        break;
      }
    }
  }
}
