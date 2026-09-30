import { useEffect, useState } from 'react'

const RADIUS = 27
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

// nút "về đầu trang" góc dưới phải: nút kẹo tím có gờ như các nút khác,
// vòng ngoài màu vàng chạy theo tiến độ cuộn trang; chỉ hiện khi đã cuộn xuống
// raised: đẩy lên trên nút chatbot ở cùng góc
export function ScrollToTop({ raised = false }: { raised?: boolean }) {
  const [progress, setProgress] = useState(0)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    let frame = 0
    const update = () => {
      frame = 0
      const max = document.documentElement.scrollHeight - window.innerHeight
      setProgress(max > 0 ? Math.min(1, window.scrollY / max) : 0)
      setVisible(window.scrollY > 400)
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  const toTop = () => {
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
  }

  return (
    <button
      type="button"
      onClick={toTop}
      aria-label="Về đầu trang"
      title="Về đầu trang"
      tabIndex={visible ? 0 : -1}
      aria-hidden={!visible}
      className={`group fixed right-4 z-30 grid h-16 w-16 place-items-center transition duration-300 sm:right-7 ${raised ? 'bottom-24 sm:bottom-[108px]' : 'bottom-5 sm:bottom-7'} ${
        visible ? 'translate-y-0 scale-100 opacity-100' : 'pointer-events-none translate-y-6 scale-75 opacity-0'
      }`}
    >
      {/* vòng tiến độ cuộn */}
      <svg viewBox="0 0 64 64" className="absolute inset-0 h-full w-full -rotate-90" aria-hidden>
        <circle cx="32" cy="32" r={RADIUS} fill="white" stroke="var(--color-sky-soft)" strokeWidth="5" />
        <circle
          cx="32"
          cy="32"
          r={RADIUS}
          fill="none"
          stroke="var(--color-sun)"
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={CIRCUMFERENCE * (1 - progress)}
          className="transition-[stroke-dashoffset] duration-150"
        />
      </svg>
      <span
        className="relative grid h-11 w-11 place-items-center rounded-full bg-primary text-white shadow-[0_4px_0_#3a22b8] transition group-hover:-translate-y-0.5 group-hover:bg-primary-hover group-active:translate-y-[3px] group-active:shadow-none"
        aria-hidden
      >
        <svg
          viewBox="0 0 24 24"
          className="h-5 w-5 transition group-hover:-translate-y-0.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M12 19V5M5 12l7-7 7 7" />
        </svg>
      </span>
      {/* bong bóng nhỏ trang trí, giống bong bóng ở hero */}
      <span className="absolute -left-1 top-1 h-3 w-3 rounded-full bg-skill-creative" aria-hidden />
      <span className="absolute -bottom-0.5 right-0 h-2.5 w-2.5 rounded-full bg-leaf" aria-hidden />
    </button>
  )
}
