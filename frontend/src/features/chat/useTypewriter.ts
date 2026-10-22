import { useEffect, useState } from 'react'

// Tốc độ gõ (ký tự / giây): câu ngắn gõ đều tay, câu dài tăng tốc để luôn xong trong khoảng MAX_SECONDS,
// phụ huynh không phải chờ lâu mới đọc được hết câu trả lời.
const MIN_CPS = 60
const MAX_SECONDS = 6

export function typingSpeed(total: number): number {
  return Math.max(MIN_CPS, total / MAX_SECONDS)
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/**
 * Hiệu ứng gõ chữ kiểu TextType (reactbits.dev): trả số ký tự đang hiện, tăng dần theo requestAnimationFrame
 * tới total. enabled = false (tin nhắn cũ) hoặc máy bật "giảm chuyển động" thì hiện đủ ngay.
 * skip() hiện hết luôn (khi người dùng bấm vào câu trả lời).
 */
export function useTypewriter(total: number, enabled: boolean) {
  const [animate] = useState(() => enabled && !prefersReducedMotion())
  const [shown, setShown] = useState(animate ? 0 : total)
  const [skipped, setSkipped] = useState(false)

  useEffect(() => {
    if (!animate || skipped) return
    const cps = typingSpeed(total)
    let start: number | null = null
    let frame = 0
    const step = (now: number) => {
      start ??= now
      const next = Math.min(total, Math.floor(((now - start) / 1000) * cps))
      setShown(next)
      if (next < total) frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [animate, skipped, total])

  const visible = skipped || !animate ? total : shown
  return { visible, done: visible >= total, animated: animate, skip: () => setSkipped(true) }
}
