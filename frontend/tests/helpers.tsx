import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { http } from '@/api/client'
import type { LoginResponse } from '@/api/types'
import { AuthProvider } from '@/auth/AuthContext'
import { tokenStorage } from '@/auth/tokenStorage'
import { ToastProvider } from '@/components/ui/Toast'

// Giả lập backend ở tầng adapter của axios: đi qua interceptor + request() thật, chỉ thay phần gửi mạng.
export interface Call {
  method: string
  url: string
  body: unknown
  params: Record<string, unknown>
  headers: Record<string, unknown>
}
type Reply = { status?: number; data?: unknown; message?: string }
type Handler = (call: Call) => Reply | Promise<Reply>

export function mockApi(routes: Record<string, Handler | Reply>) {
  const calls: Call[] = []
  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const method = (config.method ?? 'get').toUpperCase()
    const url = config.url ?? ''
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    const call: Call = { method, url, body, params: { ...config.params }, headers: { ...config.headers } }
    calls.push(call)
    const key = `${method} ${url}`
    const route = routes[key]
    const reply: Reply = route
      ? typeof route === 'function'
        ? await route(call)
        : route
      : { status: 404, message: `No mock for ${key}` }
    const status = reply.status ?? 200
    const response: AxiosResponse = {
      data: { success: status < 400, message: reply.message ?? 'ok', data: reply.data ?? null },
      status,
      statusText: String(status),
      headers: {},
      config,
    }
    if (status >= 400) {
      const { AxiosError } = await import('axios')
      throw new AxiosError(reply.message, String(status), config, undefined, response)
    }
    return response
  }
  http.defaults.adapter = adapter
  return calls
}

export function loginAs(role: 'CUSTOMER' | 'ADMIN' = 'CUSTOMER', userId = 1): LoginResponse {
  const session: LoginResponse = { token: `token-${role}-${userId}`, role, userId }
  tokenStorage.set(session)
  return session
}

export function logoutStorage() {
  tokenStorage.clear()
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

// bọc component giống main.tsx: react-query + router + toast + auth
export function renderWithApp(
  ui: ReactNode,
  { route = '/', path = '*', extraRoutes }: { route?: string; path?: string; extraRoutes?: ReactNode } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <ToastProvider>
          <AuthProvider>
            <Routes>
              <Route path={path} element={ui} />
              {extraRoutes}
            </Routes>
            <LocationProbe />
          </AuthProvider>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...result, queryClient }
}
