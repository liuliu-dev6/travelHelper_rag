import request, { type ApiResult } from '@/utils/request'

export interface UserInfo {
  id: number
  username: string
  email: string
  role: string
  createdAt: string
}

export const getCurrentUser = (): Promise<ApiResult<UserInfo>> =>
  request.get('/api/auth/me') as unknown as Promise<ApiResult<UserInfo>>

export const login = (data: { account: string; password: string }): Promise<ApiResult<UserInfo>> =>
  request.post('/api/auth/login', data) as unknown as Promise<ApiResult<UserInfo>>

export const register = (data: { username: string; email: string; password: string }): Promise<ApiResult<UserInfo>> =>
  request.post('/api/auth/register', data) as unknown as Promise<ApiResult<UserInfo>>

export const logout = (): Promise<ApiResult<void>> =>
  request.post('/api/auth/logout') as unknown as Promise<ApiResult<void>>

export const changePassword = (data: { currentPassword: string; newPassword: string }): Promise<ApiResult<void>> =>
  request.put('/api/auth/password', data) as unknown as Promise<ApiResult<void>>
