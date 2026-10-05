import { request, resetCsrf } from './client'
import type { User } from '../types/api'
export const authApi = {
  me: (signal?: AbortSignal) => request<User>('/api/auth/me', { signal, anonymous: true }),
  async login(studentId: string, password: string) {
    const result = await request<{ user: User }>('/api/auth/login', {
      method: 'POST',
      body: { studentId, password },
      anonymous: true,
    })
    resetCsrf()
    return result.user
  },
  async logout() {
    await request<void>('/api/auth/logout', { method: 'POST' })
    resetCsrf()
  },
}
