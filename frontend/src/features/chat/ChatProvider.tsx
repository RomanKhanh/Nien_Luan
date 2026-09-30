import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '@/auth/AuthContext'
import { ChatWidgetContext, type ChatPrompt, type ChatWidgetApi } from './chatContext'

// giữ trạng thái khung chat nổi (mở/đóng, hội thoại đang xem) xuyên suốt các trang
export function ChatProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [sessionId, setSessionId] = useState<number | null>(null)
  const [prompt, setPrompt] = useState<(ChatPrompt & { key: number }) | null>(null)
  const nextKey = useRef(1)

  // đổi tài khoản / đăng xuất: bỏ hội thoại đang mở của người trước
  const { session } = useAuth()
  const userId = session?.userId ?? null
  const [owner, setOwner] = useState(userId)
  if (owner !== userId) {
    setOwner(userId)
    setSessionId(null)
    setPrompt(null)
  }

  const ask = useCallback((p?: ChatPrompt) => {
    setOpen(true)
    if (p) {
      setSessionId(null)
      setPrompt({ ...p, key: nextKey.current++ })
    }
  }, [])
  const consumePrompt = useCallback(() => setPrompt(null), [])

  const api = useMemo<ChatWidgetApi>(
    () => ({ open, setOpen, ask, sessionId, setSessionId, prompt, consumePrompt }),
    [open, ask, sessionId, prompt, consumePrompt],
  )
  return <ChatWidgetContext.Provider value={api}>{children}</ChatWidgetContext.Provider>
}
