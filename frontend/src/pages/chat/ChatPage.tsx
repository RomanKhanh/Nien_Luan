import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CHAT_MOCK } from '@/api/chat'
import { useAuth } from '@/auth/AuthContext'
import { Bubbles, Mascot, TornEdge } from '@/components/decor/Decor'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { ChatPanel } from '@/features/chat/ChatPanel'
import { SessionList } from '@/features/chat/SessionList'

// trang tư vấn đầy đủ: lịch sử hội thoại bên trái, khung chat bên phải
export default function ChatPage() {
  const { isCustomer } = useAuth()
  const [params, setParams] = useSearchParams()
  const sessionId = params.get('session') ? Number(params.get('session')) : null
  const select = (id: number | null) => setParams(id ? { session: String(id) } : {}, { replace: id !== null })
  const [historyOpen, setHistoryOpen] = useState(false)

  return (
    <>
      <section className="relative overflow-hidden bg-gradient-to-br from-sky to-sky-deep text-white">
        <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
        <Bubbles />
        <div className="container-page relative flex items-center gap-4 pb-12 pt-6">
          <Mascot className="w-16 shrink-0 animate-float sm:w-20" />
          <div className="min-w-0">
            <p className="inline-flex rounded-full bg-sun px-3 py-0.5 text-[12.5px] font-bold uppercase tracking-[0.06em] text-ink">
              Chatbot AI {CHAT_MOCK && '· bản demo'}
            </p>
            <h1 className="sticker-text-sm font-display text-[28px] font-extrabold leading-tight sm:text-[36px]">
              Tư vấn cùng Bin
            </h1>
            <p className="text-[14px] text-white/90">
              Hỏi về lợi ích giáo dục, độ tuổi phù hợp và lộ trình kỹ năng của bé.
            </p>
          </div>
        </div>
        <TornEdge className="absolute inset-x-0 bottom-0" seed={37} />
      </section>

      <div className="container-page grid grid-cols-1 gap-5 py-6 lg:grid-cols-[280px_minmax(0,1fr)]">
        {isCustomer && (
          <aside className="card hidden h-fit overflow-hidden lg:block">
            <div className="flex items-center justify-between gap-2 border-b border-line p-3">
              <p className="pl-1 font-display text-[18px] font-extrabold">Lịch sử tư vấn</p>
              <Button size="sm" onClick={() => select(null)}>
                ＋ Mới
              </Button>
            </div>
            <div className="max-h-[60vh] overflow-y-auto">
              <SessionList
                activeId={sessionId}
                onSelect={select}
                onDeleted={(id) => id === sessionId && select(null)}
              />
            </div>
          </aside>
        )}
        {isCustomer && (
          <div className="flex gap-2 lg:hidden">
            <Button variant="secondary" size="sm" onClick={() => setHistoryOpen(true)}>
              🕘 Lịch sử tư vấn
            </Button>
            <Button size="sm" onClick={() => select(null)}>
              ＋ Hội thoại mới
            </Button>
          </div>
        )}
        <div className="card h-[calc(100vh-10rem)] min-h-[520px] overflow-hidden lg:h-[calc(100vh-12rem)]">
          <ChatPanel sessionId={sessionId} onSessionChange={select} variant="page" />
        </div>
      </div>
      <Modal open={historyOpen} onClose={() => setHistoryOpen(false)} title="Lịch sử tư vấn" variant="drawer">
        <div className="-mx-3 -my-3">
          <SessionList
            activeId={sessionId}
            onSelect={(id) => {
              select(id)
              setHistoryOpen(false)
            }}
            onDeleted={(id) => id === sessionId && select(null)}
          />
        </div>
      </Modal>
    </>
  )
}
