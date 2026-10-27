/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  // OAuth Client ID cho nút đăng nhập Google; trống thì trang đăng nhập ẩn nút (xem .env.example)
  readonly VITE_GOOGLE_CLIENT_ID?: string
  readonly VITE_FACEBOOK_APP_ID?: string
  readonly VITE_FACEBOOK_API_VERSION?: string
}
import type { ApiError } from '@/api/client'

// mọi lỗi từ useQuery / useMutation đều là ApiError (xem api/client.ts#request)
declare module '@tanstack/react-query' {
  interface Register {
    defaultError: ApiError
  }
}
