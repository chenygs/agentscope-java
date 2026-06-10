import { requestClient } from '#/api/request';

export interface ChatSession {
  id: number;
  userId: number;
  agentId: string;
  title: string;
  isPinned: boolean;
  pinTime: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  sessionId: number;
  role: string;
  content: string;
  createdAt: string;
}

/** 获取当前用户的会话列表 */
export function fetchSessionsApi() {
  return requestClient.get<ChatSession[]>('/chat/sessions');
}

/** 创建新会话 */
export function createSessionApi(agentId = 'ppt-agent') {
  return requestClient.post<ChatSession>('/chat/sessions', { agentId });
}

/** 删除会话 */
export function deleteSessionApi(id: number) {
  return requestClient.delete<void>(`/chat/sessions/${id}`);
}

/** 更新会话标题 */
export function updateSessionTitleApi(id: number, title: string) {
  return requestClient.put<ChatSession>(`/chat/sessions/${id}/title`, { title });
}

/** 获取会话消息列表 */
export function fetchMessagesApi(sessionId: number) {
  return requestClient.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`);
}

/** 添加消息（autoTitle=true 时自动用首条用户消息设为标题） */
export function addMessageApi(sessionId: number, role: string, content: string, autoTitle = false) {
  return requestClient.post<ChatMessage>(`/chat/sessions/${sessionId}/messages`, {
    role,
    content,
    autoTitle,
  });
}
