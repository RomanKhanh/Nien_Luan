import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { CHAT_MOCK } from '@/api/chat'
import { useAuth } from '@/auth/AuthContext'
import { Bubbles, Mascot } from '@/components/decor/Decor'
import { ChatPanel } from './ChatPanel'
import { useChatWidget } from './chatContext'
import { SessionList } from './SessionList'

const HINT_KEY = 'brainblocks.chatHintSeen'

// Khung chat nổi góc dưới phải: nút linh vật Bin + khung hội thoại (desktop) / toàn màn hình (mobile)
export function ChatWidget() {
  const { open, setOpen, sessionId, setSessionId, prompt, consumePrompt } = useChatWidget()
  const { isAdmin } = useAuth()
  const location = useLocation()
  const [view, setView] = useState<'chat' | 'history'>('chat')
  const [hint, setHint] = useState(() => {
    try {
      return !sessionStorage.getItem(HINT_KEY)
    } catch {
      return false
    }
  })

  // có câu hỏi gửi từ trang khác thì quay về màn hội thoại
  const [promptKey, setPromptKey] = useState(prompt?.key)
  if (prompt?.key !== promptKey) {
    setPromptKey(prompt?.key)
    if (prompt) setView('chat')
  }

  // trang /chat đã có khung đầy đủ; admin không dùng chatbot tư vấn
  const hidden = location.pathname.startsWith('/chat') || isAdmin

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, setOpen])

  // mobile: khung chat phủ kín màn hình, khoá cuộn trang phía sau
  useEffect(() => {
    if (!open || hidden || !window.matchMedia('(max-width: 639px)').matches) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [open, hidden])

  // lời mời tự ẩn sau 8 giây để không che nội dung trang
  useEffect(() => {
    if (!hint || hidden) return
    const timer = window.setTimeout(() => {
      setHint(false)
      try {
        sessionStorage.setItem(HINT_KEY, '1')
      } catch {
        /* bỏ qua */
      }
    }, 8000)
    return () => window.clearTimeout(timer)
  }, [hint, hidden])

  if (hidden) return null

  const dismissHint = () => {
    setHint(false)
    try {
      sessionStorage.setItem(HINT_KEY, '1')
    } catch {
      /* bỏ qua */
    }
  }
  const toggle = () => {
    dismissHint()
    setOpen(!open)
  }

  return (
    <>
      {open && (
        <section
          role="dialog"
          aria-label="Trò chuyện với Bin - trợ lý tư vấn"
          className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-surface sm:inset-auto sm:bottom-28 sm:right-7 sm:h-[min(640px,calc(100vh-13rem))] sm:w-[400px] sm:rounded-[26px] sm:border-2 sm:border-line sm:shadow-pop"
        >
          <header className="relative overflow-hidden bg-gradient-to-br from-sky to-sky-deep px-4 pb-4 pt-3.5 text-white">
            <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
            <div className="opacity-60">
              <Bubbles />
            </div>
            <div className="relative flex items-center gap-3">
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full border-[3px] border-white bg-sky-soft shadow-card">
                <Mascot className="w-9" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-display text-[20px] font-extrabold leading-none">Trợ lý Bin</p>
                <p className="mt-1 flex items-center gap-1.5 text-[12px] text-white/90">
                  <span className="h-2 w-2 shrink-0 rounded-full bg-leaf" aria-hidden />
                  <span className="truncate">{CHAT_MOCK ? 'Bản demo' : 'Trực tuyến'}</span>
                </p>
              </div>
              <HeaderButton
                label={view === 'history' ? 'Quay lại hội thoại' : 'Lịch sử hội thoại'}
                onClick={() => setView(view === 'history' ? 'chat' : 'history')}
              >
                {view === 'history' ? '💬' : '🕘'}
              </HeaderButton>
              <HeaderButton
                label="Hội thoại mới"
                onClick={() => {
                  setSessionId(null)
                  setView('chat')
                }}
              >
                ＋
              </HeaderButton>
              <Link
                to={sessionId ? `/chat?session=${sessionId}` : '/chat'}
                onClick={() => setOpen(false)}
                aria-label="Mở toàn màn hình"
                title="Mở toàn màn hình"
                className="hidden h-9 w-9 place-items-center rounded-full bg-white/20 text-[15px] text-white transition hover:bg-white/30 hover:text-white sm:grid"
              >
                ⤢
              </Link>
              <HeaderButton label="Đóng" onClick={() => setOpen(false)}>
                ✕
              </HeaderButton>
            </div>
          </header>
          <div className="min-h-0 flex-1">
            {view === 'history' ? (
              <div className="h-full overflow-y-auto">
                <SessionList
                  activeId={sessionId}
                  onSelect={(id) => {
                    setSessionId(id)
                    setView('chat')
                  }}
                  onDeleted={(id) => id === sessionId && setSessionId(null)}
                />
              </div>
            ) : (
              <ChatPanel
                sessionId={sessionId}
                onSessionChange={setSessionId}
                prompt={prompt}
                onPromptConsumed={consumePrompt}
                // mobile: bấm sang trang sản phẩm thì đóng khung để thấy trang
                onNavigate={() => window.matchMedia('(max-width: 639px)').matches && setOpen(false)}
              />
            )}
          </div>
        </section>
      )}

      {/* lời mời nhỏ cạnh nút, hiện 1 lần mỗi phiên trình duyệt */}
      {hint && !open && (
        <div className="fixed bottom-7 right-24 z-40 hidden max-w-[220px] animate-float-slow rounded-2xl rounded-br-sm border-2 border-line bg-surface px-3.5 py-2.5 text-[13px] font-semibold text-ink-2 shadow-pop sm:block">
          Cần tư vấn đồ chơi cho bé? <b className="text-primary">Hỏi Bin nhé!</b>
          <button
            type="button"
            onClick={dismissHint}
            aria-label="Ẩn lời nhắc"
            className="absolute -right-2 -top-2 grid h-6 w-6 place-items-center rounded-full bg-muted text-[11px] text-ink-muted hover:bg-line"
          >
            ✕
          </button>
        </div>
      )}

      <button
        type="button"
        onClick={toggle}
        aria-label={open ? 'Đóng khung chat' : 'Mở chat tư vấn với Bin'}
        aria-expanded={open}
        className={`group fixed bottom-5 right-4 z-40 h-16 w-16 rounded-full border-4 border-white bg-primary shadow-[0_5px_0_#3a22b8,0_12px_28px_rgb(13_27_62/0.3)] transition hover:-translate-y-0.5 active:translate-y-[3px] active:shadow-none sm:bottom-7 sm:right-7 ${
          open ? 'max-sm:hidden' : ''
        }`}
      >
        {open ? (
          <span className="text-[22px] font-bold text-white" aria-hidden>
            ✕
          </span>
        ) : (
          <>
            <Mascot className="absolute left-1/2 top-1/2 w-11 -translate-x-1/2 -translate-y-[45%] transition group-hover:animate-wiggle" />
            <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full border-2 border-white bg-sun px-1 text-[10px] font-extrabold text-ink">
              AI
            </span>
          </>
        )}
      </button>
    </>
  )
}

function HeaderButton({ label, onClick, children }: { label: string; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-white/20 text-[15px] font-bold text-white transition hover:bg-white/30"
    >
      {children}
    </button>
  )
}
