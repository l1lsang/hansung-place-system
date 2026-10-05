import { useEffect, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { ApiError, onUnauthorized } from '../api/client'
import { AuthContext } from '../hooks/authContext'
import type { User } from '../types/api'

export function AuthProvider({ children }: { children: ReactNode }) {
  const cache = useQueryClient()
  const auth = useQuery({
    queryKey: ['auth'],
    queryFn: async ({ signal }) => {
      try {
        return await authApi.me(signal)
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    retry: false,
    staleTime: 60_000,
  })
  function clearPrivate() {
    cache.removeQueries({
      predicate: (q) =>
        ['reservations', 'reservation', 'admin', 'seat-availability'].includes(
          String(q.queryKey[0]),
        ),
    })
  }
  useEffect(
    () =>
      onUnauthorized(() => {
        cache.setQueryData(['auth'], null)
        cache.removeQueries({
          predicate: (q) =>
            ['reservations', 'reservation', 'admin', 'seat-availability'].includes(
              String(q.queryKey[0]),
            ),
        })
      }),
    [cache],
  )
  return (
    <AuthContext.Provider
      value={{
        user: (auth.data as User | null) ?? null,
        loading: auth.isPending,
        error: auth.error,
        refresh: () => {
          void auth.refetch()
        },
        login: async (id, password) => {
          const user = await authApi.login(id, password)
          clearPrivate()
          cache.setQueryData(['auth'], user)
        },
        logout: async () => {
          await authApi.logout()
          clearPrivate()
          cache.setQueryData(['auth'], null)
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
