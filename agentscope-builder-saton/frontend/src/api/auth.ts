import client from './client'
import type { ApiResponse } from './client'

export interface LoginResponse {
  token: string
  userId: string
  username: string
}

export interface MeResponse {
  userId: string
  username: string
}

/** POST /api/auth/login */
export function login(username: string, password: string) {
  return client.post<ApiResponse<LoginResponse>>('/api/auth/login', { username, password })
}

/** GET /api/auth/me */
export function getMe() {
  return client.get<ApiResponse<MeResponse>>('/api/auth/me')
}
