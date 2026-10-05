import { createContext, useContext } from 'react'
import type { User } from '../types/api'
export interface AuthValue {
  user: User | null
  loading: boolean
  error: unknown
  refresh: () => void
  login: (id: string, password: string) => Promise<void>
  logout: () => Promise<void>
}
export const AuthContext = createContext<AuthValue | null>(null)
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider is required')
  return value
}
