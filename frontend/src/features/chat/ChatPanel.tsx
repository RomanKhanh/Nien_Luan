import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { chatApi, CHAT_MOCK } from '@/api/chat'
import { childApi } from '@/api/endpoints'
import type { ChatSessionDetail } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Mascot } from '@/components/decor/Decor'
import { ButtonLink, Spinner } from '@/components/ui/Button'
import { useToast } from '@/components/ui/Toast'
import { childKeys } from '@/features/children/keys'
import type { ChatPrompt } from './chatContext'
import { BotAvatar, MessageBubble } from './MessageBubble'
import { chatKeys, useChatSession, useChatSuggestions } from './queries'

const MAX_LENGTH = 1000

// Khung hội thoại dùng chung cho khung chat nổi (variant="widget") và trang /chat (variant="page").
// sessionId = null: hội thoại mới, chỉ tạo ChatSession ở backend khi gửi câu hỏi đầu tiên.
export function ChatPanel({
  sessionId,
  onSessionChange,
  prompt,
  onPromptConsumed,
  onNavigate,
  variant = 'widget',
}: {
  sessionId: number | null
  onSessionChange: (id: number) => void
  prompt?: (ChatPrompt & { key: number }) | null
  onPromptConsumed?: () => void
  onNavigate?: () => void
  variant?: 'widget' | 'page'
}) {
  const { session, isCustomer } = useAuth()
  if (!session) return <Gate kind="login" />
  if (!isCustomer) return <Gate kind="admin" />
  return (
    <Conversation
      key={sessionId ?? 'new'}
      sessionId={sessionId}
      onSessionChange={onSessionChange}
      prompt={prompt}
      onPromptConsumed={onPromptConsumed}
      onNavigate={onNavigate}
      variant={variant}
    />
  )
}

function Conversation({
  sessionId,
  onSessionChange,
  prompt,
  onPromptConsumed,
  onNavigate,
  variant,
}: {
  sessionId: number | null
  onSessionChange: (id: number) => void
  prompt?: (ChatPrompt & { key: number }) | null
  onPromptConsumed?: () => void
  onNavigate?: () => void
  variant: 'widget' | 'page'
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list })
  const detail = useChatSession(sessionId)
  // hội thoại mới: mặc định tư vấn cho bé đầu tiên (undefined = chưa chọn gì)
  const [draftChild, setDraftChild] = useState<number | null | undefined>(undefined)
  const selectedChild =
    sessionId !== null
      ? (detail.data?.childProfileId ?? null)
      : draftChild === undefined
        ? (children.data?.[0]?.id ?? null)
        : draftChild
  const [text, setText] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [failed, setFailed] = useState<{ text: string; message: string } | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const messages = detail.data?.messages ?? []
  const suggestions = useChatSuggestions(selectedChild, messages.length === 0 && !pending)

  const send = useMutation({
    mutationFn: async ({ content, child }: { content: string; child: number | null }) => {
      let id = sessionId
      if (id === null) {
        const created = await chatApi.createSession({ childProfileId: child })
        id = created.id
        queryClient.setQueryData(chatKeys.session(id), created)
      }
      const reply = await chatApi.sendMessage(id, content)
      return { id, reply }
    },
    onMutate: ({ content }) => {
      setPending(content)
      setFailed(null)
    },
    onSuccess: ({ id, reply }) => {
      queryClient.setQueryData<ChatSessionDetail>(chatKeys.session(id), (old) =>
        old
          ? {
              ...old,
              title: old.title ?? reply.userMessage.content,
              messages: [...old.messages, reply.userMessage, reply.botMessage],
              messageCount: old.messageCount + 2,
              lastMessageAt: reply.botMessage.createdAt,
            }
          : old,
      )
      queryClient.invalidateQueries({ queryKey: chatKeys.sessions })
      setPending(null)
      if (id !== sessionId) onSessionChange(id)
    },
    onError: (e, { content }) => {
      setPending(null)
      setFailed({ text: content, message: e.message })
    },
  })

  // hội thoại mới đang được tạo từ câu hỏi gửi sẵn: hiện đúng bé của câu hỏi đó
  const childId = sessionId === null && send.isPending && send.variables ? send.variables.child : selectedChild

  const changeChild = useMutation({
    mutationFn: (child: number | null) => chatApi.updateSession(sessionId!, { childProfileId: child }),
    onSuccess: (s) => {
      queryClient.setQueryData<ChatSessionDetail>(chatKeys.session(s.id), (old) => (old ? { ...old, ...s } : old))
      queryClient.invalidateQueries({ queryKey: chatKeys.sessions })
    },
    onError: (e) => toast.error(e.message),
  })

  const submit = (content: string) => {
    const value = content.trim()
    if (!value || send.isPending) return
    setText('')
    send.mutate({ content: value, child: childId })
  }

  // câu hỏi gửi từ trang khác ("Hỏi Bin về sản phẩm này"): gửi 1 lần, StrictMode chạy effect 2 lần nên nhớ key
  const handledPrompt = useRef<number | null>(null)
  useEffect(() => {
    if (!prompt || sessionId !== null || handledPrompt.current === prompt.key) return
    handledPrompt.current = prompt.key
    const child = prompt.childProfileId !== undefined ? prompt.childProfileId : childId
    send.mutate({ content: prompt.text, child })
    onPromptConsumed?.()
  }, [prompt, sessionId, childId, send, onPromptConsumed])

  // luôn cuộn xuống tin mới nhất
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages.length, pending, failed])

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // isComposing: đang gõ dấu tiếng Việt (Telex/VNI) thì Enter chưa phải là gửi
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit(text)
    }
  }

  const empty = messages.length === 0 && !pending && !failed

  return (
    <div className="flex h-full min-h-0 flex-col">
      {/* đang tư vấn cho bé nào: chatbot dùng tuổi + hồ sơ kỹ năng của bé làm ngữ cảnh */}
      <div className="flex items-center gap-2 border-b border-line bg-surface px-4 py-2.5 text-[13px]">
        <span className="shrink-0 font-semibold text-ink-muted">Tư vấn cho:</span>
        <select
          aria-label="Chọn hồ sơ bé để tư vấn"
          value={childId ?? ''}
          disabled={changeChild.isPending || send.isPending}
          onChange={(e) => {
            const v = e.target.value ? Number(e.target.value) : null
            if (sessionId === null) setDraftChild(v)
            else changeChild.mutate(v)
          }}
          className="min-w-0 flex-1 cursor-pointer truncate rounded-full border-2 border-line bg-sky-soft/60 px-3 py-1 font-bold text-ink outline-none focus:border-sky"
        >
          <option value="">Không chọn bé (tư vấn chung)</option>
          {children.data?.map((c) => (
            <option key={c.id} value={c.id}>
              Bé {c.name} · {c.age} tuổi
            </option>
          ))}
        </select>
        {children.data?.length === 0 && (
          <ButtonLink to="/children" size="sm" variant="ghost" className="!px-2" onClick={onNavigate}>
            + Tạo hồ sơ
          </ButtonLink>
        )}
      </div>

      <div
        ref={scrollRef}
        className="min-h-0 flex-1 space-y-4 overflow-y-auto bg-[#f7fbfd] px-4 py-4"
        aria-live="polite"
        aria-busy={send.isPending}
      >
        {detail.isPending && sessionId !== null ? (
          <div className="flex justify-center py-10 text-primary">
            <Spinner className="h-6 w-6" />
          </div>
        ) : detail.isError ? (
          <p className="rounded-2xl bg-danger-soft p-3 text-[13px] text-danger">{detail.error.message}</p>
        ) : empty ? (
          <Welcome
            suggestions={suggestions.data ?? []}
            loading={suggestions.isPending}
            onPick={submit}
            wide={variant === 'page'}
          />
        ) : (
          <>
            {messages.map((m) => (
              <MessageBubble key={m.id} message={m} onNavigate={onNavigate} />
            ))}
            {pending && (
              <>
                <div className="flex justify-end">
                  <div className="max-w-[85%] whitespace-pre-line rounded-[20px] rounded-br-md bg-primary px-4 py-2.5 text-[14px] text-white opacity-80">
                    {pending}
                  </div>
                </div>
                <Typing />
              </>
            )}
            {failed && (
              <div className="rounded-2xl border-2 border-danger/30 bg-danger-soft p-3 text-[13px]">
                <p className="font-bold text-danger">Bin chưa trả lời được: {failed.message}</p>
                <p className="mt-1 line-clamp-2 text-ink-2">“{failed.text}”</p>
                <button
                  type="button"
                  className="mt-2 font-bold text-primary hover:underline"
                  onClick={() => send.mutate({ content: failed.text, child: childId })}
                >
                  ↻ Gửi lại
                </button>
              </div>
            )}
          </>
        )}
      </div>

      <form
        className="border-t border-line bg-surface px-3 pb-2 pt-3"
        onSubmit={(e) => {
          e.preventDefault()
          submit(text)
        }}
      >
        <div className="flex items-end gap-2 rounded-[22px] border-2 border-line bg-surface p-1.5 pl-4 transition focus-within:border-sky focus-within:shadow-[0_0_0_4px_var(--color-sky-soft)]">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
            onKeyDown={onKeyDown}
            rows={1}
            placeholder="Hỏi Bin về đồ chơi cho bé…"
            aria-label="Nội dung câu hỏi"
            className="max-h-32 min-h-[36px] flex-1 resize-none bg-transparent py-1.5 text-[14px] outline-none [field-sizing:content] placeholder:text-ink-faint"
          />
          <button
            type="submit"
            disabled={!text.trim() || send.isPending}
            aria-label="Gửi câu hỏi"
            className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-primary text-white shadow-[0_3px_0_#3a22b8] transition hover:-translate-y-px hover:bg-primary-hover active:translate-y-[2px] active:shadow-none disabled:translate-y-0 disabled:bg-line-strong disabled:shadow-none"
          >
            {send.isPending ? (
              <Spinner className="h-4 w-4" />
            ) : (
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden>
                <path d="M3.4 20.4 21 12 3.4 3.6 3.3 10l12.6 2-12.6 2z" />
              </svg>
            )}
          </button>
        </div>
        <p className="mt-1.5 flex justify-between gap-2 px-1 text-[11px] text-ink-faint">
          <span>Gợi ý chỉ mang tính tham khảo, không đánh giá năng lực của trẻ.</span>
          {text.length > MAX_LENGTH * 0.8 && (
            <span className="shrink-0 tabular-nums">
              {text.length}/{MAX_LENGTH}
            </span>
          )}
        </p>
      </form>
    </div>
  )
}

function Welcome({
  suggestions,
  loading,
  onPick,
  wide,
}: {
  suggestions: string[]
  loading: boolean
  onPick: (q: string) => void
  wide: boolean
}) {
  return (
    <div className="flex flex-col items-center py-3 text-center">
      <div className="relative">
        <span className="absolute -right-3 top-2 h-4 w-4 rounded-full bg-sun" aria-hidden />
        <span className="absolute -left-2 bottom-3 h-3 w-3 rounded-full bg-skill-creative" aria-hidden />
        <Mascot className="w-24 animate-float" />
      </div>
      <p className="mt-2 font-display text-[22px] font-extrabold leading-tight text-ink">Chào phụ huynh! Mình là Bin</p>
      <p className="mt-1 max-w-sm text-[13.5px] text-ink-muted">
        Hỏi mình về lợi ích của đồ chơi, độ tuổi phù hợp, hoặc nên mua gì tiếp để bé phát triển kỹ năng.
      </p>
      {CHAT_MOCK && (
        <p className="mt-2 rounded-full bg-sun/25 px-3 py-1 text-[11.5px] font-bold text-[#8a5a00]">
          Bản demo · câu trả lời được giả lập, sản phẩm gợi ý là dữ liệu thật
        </p>
      )}
      <div className={`mt-4 grid w-full gap-2 text-left ${wide ? 'sm:grid-cols-2' : ''}`}>
        {loading
          ? Array.from({ length: 3 }, (_, i) => <div key={i} className="h-11 animate-pulse rounded-2xl bg-muted" />)
          : suggestions.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => onPick(s)}
                className="rounded-2xl border-2 border-line bg-surface px-3.5 py-2.5 text-[13px] font-semibold text-ink-2 transition hover:-translate-y-0.5 hover:border-sky hover:text-ink"
              >
                💬 {s}
              </button>
            ))}
      </div>
    </div>
  )
}

function Typing() {
  return (
    <div className="flex items-center gap-2.5" role="status" aria-label="Bin đang soạn câu trả lời">
      <BotAvatar />
      <div className="flex gap-1 rounded-[20px] rounded-tl-md border-[1.5px] border-line bg-surface px-4 py-3.5">
        {[0, 150, 300].map((d) => (
          <span
            key={d}
            className="h-2 w-2 animate-bounce rounded-full bg-sky"
            style={{ animationDelay: `${d}ms` }}
            aria-hidden
          />
        ))}
      </div>
    </div>
  )
}

function Gate({ kind }: { kind: 'login' | 'admin' }) {
  return (
    <div className="flex h-full flex-col items-center justify-center bg-[#f7fbfd] px-6 py-10 text-center">
      <Mascot className="w-24 animate-float" />
      <p className="mt-3 font-display text-[21px] font-extrabold leading-tight">
        {kind === 'login' ? 'Đăng nhập để trò chuyện với Bin' : 'Chatbot dành cho khách hàng'}
      </p>
      <p className="mt-1.5 max-w-xs text-[13.5px] text-ink-muted">
        {kind === 'login'
          ? 'Bin dùng độ tuổi và hồ sơ kỹ năng của bé để gợi ý đồ chơi phù hợp, nên cần tài khoản phụ huynh.'
          : 'Tài khoản quản trị xem lịch sử hội thoại và cấu hình chatbot trong trang quản trị.'}
      </p>
      <ButtonLink
        to={kind === 'login' ? '/login' : '/admin/chatbot'}
        state={kind === 'login' ? { from: window.location.pathname } : undefined}
        className="mt-5"
      >
        {kind === 'login' ? 'Đăng nhập' : 'Mở quản lý chatbot'}
      </ButtonLink>
    </div>
  )
}
