import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/endpoints'
import type { LoginResponse, Role } from '@/api/types'
import { tokenStorage } from './tokenStorage'

interface AuthState {
  session: LoginResponse | null
  role: Role | null
  isCustomer: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<LoginResponse>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<LoginResponse | null>(() => tokenStorage.get())
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    tokenStorage.clear()
    setSession(null)
    // dữ liệu của tài khoản cũ (giỏ, hồ sơ bé...) không được lộ sang phiên sau
    queryClient.clear()
  }, [queryClient])

  // interceptor axios phát sự kiện này khi server trả 401 cho request có token
  useEffect(() => {
    const onExpired = () => {
      setSession(null)
      queryClient.clear()
    }
    window.addEventListener('auth:expired', onExpired)
    return () => window.removeEventListener('auth:expired', onExpired)
  }, [queryClient])

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await authApi.login(email, password)
      queryClient.clear()
      tokenStorage.set(res)
      setSession(res)
      return res
    },
    [queryClient],
  )

  const value = useMemo<AuthState>(
    () => ({
      session,
      role: session?.role ?? null,
      isCustomer: session?.role === 'CUSTOMER',
      isAdmin: session?.role === 'ADMIN',
      login,
      logout,
    }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
