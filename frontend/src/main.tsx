import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ApiError } from '@/api/client'
import { AuthProvider } from '@/auth/AuthContext'
import { ToastProvider } from '@/components/ui/Toast'
import { ChatProvider } from '@/features/chat/ChatProvider'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // lỗi 4xx (không có quyền, không tìm thấy...) thử lại cũng vô ích
      retry: (count, error) => {
        const status = (error as ApiError).status ?? 0
        return count < 2 && (status === 0 || status >= 500)
      },
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastProvider>
          <AuthProvider>
            <ChatProvider>
              <App />
            </ChatProvider>
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
