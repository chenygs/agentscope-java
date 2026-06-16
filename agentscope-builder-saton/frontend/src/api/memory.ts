import client from './client'
import type { ApiResponse } from './client'

/** 单个记忆文件元信息(对齐后端 MemoryFileVO)。 */
export interface MemoryFile {
  kind: string
  fileName: string
  exists: boolean
  size: number
  modifiedAt: number | null
}

/** GET /api/memory 响应(对齐后端 MemorySummaryVO)。 */
export interface MemorySummary {
  root: string
  files: MemoryFile[]
}

/** 已知 kind slug —— 与后端 MemoryKind 枚举对齐。 */
export type MemoryKind = 'persona' | 'long-term'

/** GET /api/memory — 摘要(各 kind 的存在性 / 大小 / 修改时间)。 */
export function getMemorySummary() {
  return client.get<ApiResponse<MemorySummary>>('/api/memory')
}

/** GET /api/memory/{kind} — 读取(text/plain;不存在返回空串)。 */
export function readMemory(kind: MemoryKind) {
  return client.get<string>(`/api/memory/${kind}`, { responseType: 'text' })
}

/** PUT /api/memory/{kind} — 全文覆写。 */
export function writeMemory(kind: MemoryKind, content: string) {
  return client.put<ApiResponse<void>>(`/api/memory/${kind}`, { content })
}
