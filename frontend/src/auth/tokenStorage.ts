import type { LoginResponse } from '@/api/types'

const KEY = 'brainblocks.auth'

// JWT giữ trong bộ nhớ và lưu thêm localStorage để còn phiên khi tải lại trang.
// Mọi truy cập storage bọc try/catch vì chế độ riêng tư có thể chặn; khi đó phiên chỉ sống trong tab hiện tại.
let current: LoginResponse | null = read()

function read(): LoginResponse | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as LoginResponse) : null
  } catch {
    return null
  }
}

export const tokenStorage = {
  get(): LoginResponse | null {
    return current
  },
  set(value: LoginResponse) {
    current = value
    try {
      localStorage.setItem(KEY, JSON.stringify(value))
    } catch {
      /* bỏ qua */
    }
  },
  clear() {
    current = null
    try {
      localStorage.removeItem(KEY)
    } catch {
      /* bỏ qua */
    }
  },
}
