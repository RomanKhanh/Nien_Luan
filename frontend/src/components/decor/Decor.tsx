import { useEffect, useRef, type ReactNode } from 'react'

// Các chi tiết trang trí cho giao diện sinh động (tham khảo stemplus.vn):
// mép giấy xé, bong bóng nổi, linh vật robot, icon kỹ năng và hiệu ứng hiện dần khi cuộn.

// mép giấy xé: đường răng cưa ngẫu nhiên nhưng cố định (seed) để không nhảy mỗi lần render
function tornPath(seed: number, teeth = 48) {
  let s = seed
  const rand = () => {
    s = (s * 9301 + 49297) % 233280
    return s / 233280
  }
  const pts = [`M0,40`]
  for (let i = 0; i <= teeth; i++) {
    const x = (i / teeth) * 1440
    const y = 14 + rand() * 18 + (i % 2 === 0 ? 0 : rand() * 8)
    pts.push(`L${x.toFixed(1)},${y.toFixed(1)}`)
  }
  pts.push('L1440,40 Z')
  return pts.join(' ')
}

export function TornEdge({
  color = 'var(--color-bg)',
  flip = false,
  seed = 7,
  className = '',
}: {
  color?: string
  flip?: boolean
  seed?: number
  className?: string
}) {
  return (
    <svg
      viewBox="0 0 1440 40"
      preserveAspectRatio="none"
      aria-hidden
      className={`block h-6 w-full sm:h-10 ${flip ? 'rotate-180' : ''} ${className}`}
    >
      <path d={tornPath(seed)} fill={color} />
    </svg>
  )
}

// bong bóng tròn trôi nhẹ phía sau nội dung
const BUBBLES = [
  { size: 180, top: '-40px', left: '-50px', delay: '0s', opacity: 0.35 },
  { size: 90, top: '18%', left: '38%', delay: '1.5s', opacity: 0.25 },
  { size: 260, top: '-80px', right: '-70px', delay: '0.8s', opacity: 0.3 },
  { size: 60, top: '62%', left: '8%', delay: '2.2s', opacity: 0.3 },
  { size: 120, bottom: '-30px', right: '22%', delay: '3s', opacity: 0.22 },
  { size: 34, top: '30%', right: '8%', delay: '0.4s', opacity: 0.5 },
]

export function Bubbles() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
      {BUBBLES.map(({ size, delay, opacity, ...pos }, i) => (
        <span
          key={i}
          className="absolute animate-float-slow rounded-full bg-white"
          style={{ width: size, height: size, opacity, animationDelay: delay, ...pos }}
        />
      ))}
    </div>
  )
}

// linh vật robot "Bin" (vẽ tay bằng SVG, không dùng ảnh bản quyền)
export function Mascot({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 220 240" className={className} role="img" aria-label="Linh vật robot BrainBlocks">
      {/* ăng-ten bóng đèn ý tưởng */}
      <line x1="110" y1="46" x2="110" y2="20" stroke="#0d1b3e" strokeWidth="5" strokeLinecap="round" />
      <circle cx="110" cy="16" r="12" fill="#ffc233" stroke="#0d1b3e" strokeWidth="4" />
      <path d="M92 6 l-8 -6 M128 6 l8 -6 M110 -2 v-6" stroke="#ffc233" strokeWidth="4" strokeLinecap="round" />
      {/* đầu */}
      <rect x="40" y="44" width="140" height="104" rx="40" fill="#5b3df5" stroke="#0d1b3e" strokeWidth="5" />
      <rect x="58" y="62" width="104" height="66" rx="28" fill="#e4f5fc" />
      {/* kính tròn to */}
      <circle cx="88" cy="94" r="20" fill="#fff" stroke="#f2674a" strokeWidth="6" />
      <circle cx="132" cy="94" r="20" fill="#fff" stroke="#f2674a" strokeWidth="6" />
      <line x1="108" y1="94" x2="112" y2="94" stroke="#f2674a" strokeWidth="6" />
      <circle cx="92" cy="97" r="8" fill="#0d1b3e" />
      <circle cx="128" cy="97" r="8" fill="#0d1b3e" />
      <circle cx="95" cy="93" r="3" fill="#fff" />
      <circle cx="131" cy="93" r="3" fill="#fff" />
      <path d="M96 120 q14 10 28 0" fill="none" stroke="#0d1b3e" strokeWidth="5" strokeLinecap="round" />
      {/* tai */}
      <rect x="26" y="80" width="18" height="34" rx="8" fill="#7cc243" stroke="#0d1b3e" strokeWidth="4" />
      <rect x="176" y="80" width="18" height="34" rx="8" fill="#7cc243" stroke="#0d1b3e" strokeWidth="4" />
      {/* thân */}
      <rect x="62" y="150" width="96" height="66" rx="22" fill="#1fa6dd" stroke="#0d1b3e" strokeWidth="5" />
      <rect x="88" y="166" width="44" height="30" rx="8" fill="#fff" />
      <circle cx="102" cy="181" r="5" fill="#e8467c" />
      <circle cx="118" cy="181" r="5" fill="#ffc233" />
      {/* tay cầm khối lập phương */}
      <path d="M62 170 q-26 4 -30 28" fill="none" stroke="#0d1b3e" strokeWidth="6" strokeLinecap="round" />
      <path d="M158 170 q26 -4 30 -30" fill="none" stroke="#0d1b3e" strokeWidth="6" strokeLinecap="round" />
      <rect
        x="176"
        y="118"
        width="28"
        height="28"
        rx="6"
        fill="#ffc233"
        stroke="#0d1b3e"
        strokeWidth="4"
        transform="rotate(12 190 132)"
      />
      <rect
        x="16"
        y="192"
        width="26"
        height="26"
        rx="6"
        fill="#0f9e7a"
        stroke="#0d1b3e"
        strokeWidth="4"
        transform="rotate(-10 29 205)"
      />
      {/* chân */}
      <rect x="78" y="214" width="22" height="20" rx="8" fill="#0d1b3e" />
      <rect x="120" y="214" width="22" height="20" rx="8" fill="#0d1b3e" />
    </svg>
  )
}

// icon nét cho 4 nhóm kỹ năng (nhóm do admin thêm dùng icon khối lập phương)
export function SkillIcon({ code, className = 'h-7 w-7' }: { code: string; className?: string }) {
  const common = {
    className,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }
  switch (code) {
    case 'LOGIC':
      return (
        <svg {...common}>
          <path d="M4 7h4a2 2 0 1 1 4 0h4v4a2 2 0 1 1 0 4v4h-4a2 2 0 1 0-4 0H4v-4a2 2 0 1 0 0-4z" />
        </svg>
      )
    case 'CREATIVE':
      return (
        <svg {...common}>
          <path d="M12 3a9 9 0 1 0 0 18c1.1 0 1.6-.9 1.2-1.8-.5-1.1.2-2.2 1.4-2.2H17a4 4 0 0 0 4-4c0-5.5-4-10-9-10z" />
          <circle cx="7.5" cy="11" r="1.2" />
          <circle cx="10.5" cy="7" r="1.2" />
          <circle cx="15" cy="7.5" r="1.2" />
        </svg>
      )
    case 'PROBLEM_SOLVING':
      return (
        <svg {...common}>
          <path d="M9 18h6M10 21h4" />
          <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.4 1 1.1 1 1.9V16h5v-.2c0-.8.4-1.5 1-1.9A6 6 0 0 0 12 3z" />
        </svg>
      )
    case 'STEM':
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="1.6" />
          <ellipse cx="12" cy="12" rx="9" ry="3.6" />
          <ellipse cx="12" cy="12" rx="9" ry="3.6" transform="rotate(60 12 12)" />
          <ellipse cx="12" cy="12" rx="9" ry="3.6" transform="rotate(120 12 12)" />
        </svg>
      )
    default:
      return (
        <svg {...common}>
          <path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z M12 12l8-4.5 M12 12v9 M12 12L4 7.5" />
        </svg>
      )
  }
}

// hiện dần khi cuộn tới (IntersectionObserver); trình duyệt không hỗ trợ thì hiện luôn
export function Reveal({
  children,
  className = '',
  delay = 0,
}: {
  children: ReactNode
  className?: string
  delay?: number
}) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (!('IntersectionObserver' in window)) {
      el.classList.add('is-visible')
      return
    }
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add('is-visible')
          io.disconnect()
        }
      },
      { rootMargin: '0px 0px -10% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])
  return (
    <div ref={ref} className={`reveal ${className}`} style={delay ? { transitionDelay: `${delay}ms` } : undefined}>
      {children}
    </div>
  )
}
