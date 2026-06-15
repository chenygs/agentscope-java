import client from './client'
import type { ApiResponse } from './client'
import type { FileNode, WorkspaceSummary } from '@/types'

/** GET /api/agents/:id/workspace — 获取摘要(路径 + 文件数) */
export function getWorkspaceSummary(agentId: number) {
  return client.get<ApiResponse<WorkspaceSummary>>(`/api/agents/${agentId}/workspace`)
}

/** GET /api/agents/:id/workspace/files — 获取目录下第一层. path 不传或空 = 根目录 */
export function listWorkspaceFiles(agentId: number, path?: string) {
  return client.get<ApiResponse<FileNode[]>>(
    `/api/agents/${agentId}/workspace/files`,
    path ? { params: { path } } : undefined,
  )
}

/** GET /api/agents/:id/workspace/file?path=... — 读取文件内容(文本), xsrf 问题用 params 避免 POST */
export function readWorkspaceFile(agentId: number, filePath: string) {
  return client.get<string>(
    `/api/agents/${agentId}/workspace/file`,
    { params: { path: filePath }, responseType: 'text' },
  )
}

/** PUT /api/agents/:id/workspace/file?path=... — 写入文件 */
export function writeWorkspaceFile(agentId: number, filePath: string, content: string) {
  return client.put<ApiResponse<void>>(
    `/api/agents/${agentId}/workspace/file?path=${encodeURIComponent(filePath)}`,
    { content },
  )
}

/** DELETE /api/agents/:id/workspace/file?path=... — 删除文件 */
export function deleteWorkspaceFile(agentId: number, filePath: string) {
  return client.delete<ApiResponse<boolean>>(
    `/api/agents/${agentId}/workspace/file`,
    { params: { path: filePath } },
  )
}