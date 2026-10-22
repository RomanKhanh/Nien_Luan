import { useEffect, useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { ChatMessage, ChatSuggestedProduct } from '@/api/types'
import { Mascot } from '@/components/decor/Decor'
import { ProductArt } from '@/components/product/ProductArt'
import { SkillBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { useAddToCart } from '@/features/cart/useCart'
import { ageRange, formatDateTime, formatPrice } from '@/lib/format'
import { dominantSkill, topImpacts } from '@/lib/skills'
import { RichText } from './RichText'
import { parseRichText, richTextLength } from './richTextParse'
import { useTypewriter } from './useTypewriter'

// review = trang giám sát của admin: hiện chủ đề, thời gian phản hồi, không có nút thêm vào giỏ
export function MessageBubble({
  message,
  review = false,
  onNavigate,
  typing = false,
  onTyped,
  onTypingProgress,
}: {
  message: ChatMessage
  review?: boolean
  // khung chat nổi: bấm vào sản phẩm thì thu nhỏ khung lại để thấy trang
  onNavigate?: () => void
  // câu trả lời vừa nhận: hiện dần kiểu gõ chữ; xong thì gọi onTyped. Tin nhắn cũ hiện đủ ngay.
  typing?: boolean
  onTyped?: () => void
  // mỗi lần hiện thêm chữ (để khung chat cuộn theo)
  onTypingProgress?: () => void
}) {
  if (message.sender === 'USER') {
    return (
      <div className="flex flex-col items-end">
        <div className="max-w-[85%] whitespace-pre-line rounded-[20px] rounded-br-md bg-primary px-4 py-2.5 text-[14px] text-white shadow-[0_3px_0_#3a22b8]">
          {message.content}
        </div>
        {review && <Meta message={message} />}
      </div>
    )
  }
  return (
    <BotMessage
      message={message}
      review={review}
      onNavigate={onNavigate}
      typing={typing}
      onTyped={onTyped}
      onTypingProgress={onTypingProgress}
    />
  )
}

function BotMessage({
  message,
  review,
  onNavigate,
  typing,
  onTyped,
  onTypingProgress,
}: {
  message: ChatMessage
  review: boolean
  onNavigate?: () => void
  typing: boolean
  onTyped?: () => void
  onTypingProgress?: () => void
}) {
  const total = useMemo(() => richTextLength(parseRichText(message.content)), [message.content])
  const { visible, done, animated, skip } = useTypewriter(total, typing)

  useEffect(() => {
    if (animated) onTypingProgress?.()
  }, [animated, visible, onTypingProgress])
  useEffect(() => {
    if (animated && done) onTyped?.()
  }, [animated, done, onTyped])

  // thẻ sản phẩm và nguồn chỉ hiện sau khi gõ xong, hiện dần cho liền mạch
  const extrasClass = animated ? 'animate-fade-up' : undefined
  return (
    <div className="flex gap-2.5">
      <BotAvatar />
      <div className="min-w-0 flex-1">
        <div
          className={`rounded-[20px] rounded-tl-md border-[1.5px] border-line bg-surface px-4 py-3 text-[14px] leading-relaxed text-ink-2 ${done ? '' : 'cursor-pointer'}`}
          onClick={done ? undefined : skip}
          title={done ? undefined : 'Bấm để hiện toàn bộ câu trả lời'}
        >
          {animated ? (
            <>
              {/* trình đọc màn hình đọc cả câu một lần, không đọc từng chữ đang gõ */}
              <span className="sr-only">{message.content}</span>
              <div aria-hidden>
                <RichText text={message.content} visibleChars={visible} />
              </div>
            </>
          ) : (
            <RichText text={message.content} />
          )}
        </div>
        {done && message.suggestedProducts.length > 0 && (
          <div className={`mt-2 space-y-2 ${extrasClass ?? ''}`}>
            {message.suggestedProducts.map((p) => (
              <SuggestedProduct key={p.productId} product={p} review={review} onNavigate={onNavigate} />
            ))}
          </div>
        )}
        {done && message.sources.length > 0 && (
          <details className={`group mt-2 rounded-2xl bg-sky-soft/60 px-3 py-2 text-[12.5px] ${extrasClass ?? ''}`}>
            <summary className="cursor-pointer list-none font-semibold text-sky-deep marker:hidden">
              📚 Nguồn tham khảo ({message.sources.length})
              <span className="ml-1 inline-block transition group-open:rotate-90" aria-hidden>
                ›
              </span>
            </summary>
            <ul className="mt-2 space-y-2">
              {message.sources.map((s) => (
                <li key={s.documentId}>
                  <p className="font-semibold text-ink-2">{s.documentTitle}</p>
                  <p className="text-ink-muted">“{s.snippet}”</p>
                </li>
              ))}
            </ul>
          </details>
        )}
        {review && <Meta message={message} />}
      </div>
    </div>
  )
}

export function BotAvatar() {
  return (
    <span
      className="grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-sky/40 bg-sky-soft"
      aria-hidden
    >
      <Mascot className="w-7" />
    </span>
  )
}

function Meta({ message }: { message: ChatMessage }) {
  return (
    <p className="mt-1 flex flex-wrap gap-x-2 text-[11.5px] text-ink-faint">
      <span>{formatDateTime(message.createdAt)}</span>
      {message.topic && <span>· {message.topic}</span>}
      {message.sender === 'BOT' && <span>· phản hồi {(message.responseTimeMs / 1000).toFixed(1)}s</span>}
    </p>
  )
}

function SuggestedProduct({
  product,
  review,
  onNavigate,
}: {
  product: ChatSuggestedProduct
  review: boolean
  onNavigate?: () => void
}) {
  const addToCart = useAddToCart()
  const to = `/products/${product.productId}`
  return (
    <article className="flex gap-3 rounded-[18px] border-2 border-line bg-surface p-2.5 transition hover:border-sky/50">
      <Link to={to} onClick={onNavigate} className="w-20 shrink-0 overflow-hidden rounded-xl" aria-label={product.name}>
        <ProductArt url={product.thumbnailUrl} name={product.name} skillCode={dominantSkill(product.skillImpacts)} />
      </Link>
      <div className="min-w-0 flex-1">
        <Link
          to={to}
          onClick={onNavigate}
          className="line-clamp-2 text-[13.5px] font-bold leading-snug text-ink hover:text-primary"
        >
          {product.name}
        </Link>
        <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
          <span className="font-display text-[15px] font-extrabold text-coral">{formatPrice(product.price)}</span>
          <span className="text-[11.5px] text-ink-muted">{ageRange(product.minAge, product.maxAge)}</span>
        </div>
        <div className="mt-1 flex flex-wrap gap-1">
          {topImpacts(product.skillImpacts).map((i) => (
            <SkillBadge key={i.skillId} code={i.skillCode} name={i.skillName} value={i.impactIndex} />
          ))}
        </div>
        {product.reason && (
          <p className="mt-1.5 rounded-xl bg-primary-soft px-2.5 py-1.5 text-[12px] text-ink-2">
            <b className="text-primary-hover">Vì sao: </b>
            {product.reason}
          </p>
        )}
        {!review && (
          <Button
            size="sm"
            className="mt-2 !px-3 !py-1.5 !text-[12px]"
            loading={addToCart.isPending}
            onClick={() => addToCart.mutate({ productId: product.productId, quantity: 1 })}
          >
            + Thêm vào giỏ
          </Button>
        )}
      </div>
    </article>
  )
}
