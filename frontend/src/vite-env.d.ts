/// <reference types="vite/client" />
import type { ApiError } from '@/api/client'

// mọi lỗi từ useQuery / useMutation đều là ApiError (xem api/client.ts#request)
declare module '@tanstack/react-query' {
  interface Register {
    defaultError: ApiError
  }
}
