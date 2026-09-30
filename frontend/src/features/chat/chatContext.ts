import { createContext, useContext } from 'react'

// câu hỏi mở sẵn khi bấm "Hỏi Bin" ở trang khác (trang sản phẩm, hồ sơ bé...)
export interface ChatPrompt {
  text: string
  childProfileId?: number | null
}

export interface ChatWidgetApi {
  open: boolean
  setOpen: (open: boolean) => void
  // mở khung chat, bắt đầu hội thoại mới và gửi luôn câu hỏi (nếu có)
  ask: (prompt?: ChatPrompt) => void
  sessionId: number | null
  setSessionId: (id: number | null) => void
  prompt: (ChatPrompt & { key: number }) | null
  consumePrompt: () => void
}

export const ChatWidgetContext = createContext<ChatWidgetApi | null>(null)

export function useChatWidget(): ChatWidgetApi {
  const ctx = useContext(ChatWidgetContext)
  if (!ctx) throw new Error('useChatWidget must be used inside ChatProvider')
  return ctx
}
