import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Bubbles, Mascot, SkillIcon } from '@/components/decor/Decor'
import { Logo } from '@/components/layout/Logo'
import { skillTheme } from '@/lib/skills'

const YEAR = new Date().getFullYear()

// icon 4 nhóm kỹ năng bay quanh linh vật ở cột trái
const FLOATERS = [
  { code: 'LOGIC', pos: 'left-[8%] top-[6%]', delay: '0s' },
  { code: 'CREATIVE', pos: 'right-[6%] top-[14%]', delay: '1.1s' },
  { code: 'PROBLEM_SOLVING', pos: 'left-[4%] bottom-[10%]', delay: '0.5s' },
  { code: 'STEM', pos: 'right-[10%] bottom-[4%]', delay: '1.7s' },
]

// 3 điểm nổi bật dưới form, để trên điện thoại (không có cột trái) vẫn biết tài khoản dùng để làm gì
const PERKS = [
  { code: 'LOGIC', text: 'Hồ sơ kỹ năng cho từng bé' },
  { code: 'CREATIVE', text: 'Chatbot tư vấn theo độ tuổi' },
  { code: 'STEM', text: 'Gợi ý đồ chơi kèm lý do' },
]

// bố cục 2 cột của màn đăng nhập / đăng ký (mockup màn 07), cột trái nền trời xanh như trang chủ
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1.1fr]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-sky to-sky-deep p-12 text-white lg:flex">
        <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
        <Bubbles />
        <div className="relative w-fit rounded-2xl bg-white px-3 py-2 shadow-card">
          <Logo />
        </div>
        <div className="relative">
          <div className="relative mx-auto mb-8 aspect-square w-full max-w-[300px]">
            <Mascot className="absolute left-1/2 top-1/2 w-[58%] -translate-x-1/2 -translate-y-1/2 drop-shadow-[0_12px_18px_rgb(13_27_62/0.25)]" />
            {FLOATERS.map((f) => {
              const t = skillTheme(f.code)
              return (
                <span
                  key={f.code}
                  className={`absolute ${f.pos} grid h-16 w-16 animate-float-slow place-items-center rounded-full border-[5px] bg-white shadow-pop`}
                  style={{ borderColor: t.color, color: t.color, animationDelay: f.delay }}
                  aria-hidden
                >
                  <SkillIcon code={f.code} className="h-7 w-7" />
                </span>
              )
            })}
          </div>
          <p className="sticker-text font-display text-[40px] font-extrabold leading-[1.05]">
            <span className="block text-[#1c5fc6]">Theo dõi lộ trình kỹ năng</span>
            <span className="block text-sun">cho từng bé trong nhà</span>
          </p>
          <p className="mt-4 max-w-md text-[15px] text-white/90">
            Một tài khoản phụ huynh, nhiều hồ sơ bé. Gợi ý đồ chơi luôn kèm lý do.
          </p>
        </div>
        <p className="relative text-[13px] text-white/80">© {YEAR} BrainBlocks</p>
      </aside>
      <main className="relative flex flex-col overflow-hidden px-4 py-6 sm:px-8 sm:py-8">
        <span className="absolute -right-16 -top-16 h-48 w-48 rounded-full bg-sky-soft" aria-hidden />
        <span className="absolute -bottom-10 -left-10 h-32 w-32 rounded-full bg-[#fff2cc]" aria-hidden />
        <div className="relative flex items-center justify-between gap-4">
          <div className="lg:hidden">
            <Logo />
          </div>
          <Link
            to="/"
            className="ml-auto rounded-full px-3 py-1.5 text-[13.5px] font-semibold text-ink-2 transition hover:bg-surface hover:text-ink"
          >
            ← Về trang chủ
          </Link>
        </div>
        <div className="relative mx-auto flex w-full max-w-[440px] flex-1 flex-col justify-center py-8">
          <Mascot className="mx-auto -mb-3 w-20 animate-float lg:hidden" />
          <div className="card relative p-6 shadow-card-hover sm:p-8">{children}</div>
          <ul className="mt-6 grid grid-cols-3 gap-2 text-center sm:gap-3">
            {PERKS.map((p) => {
              const t = skillTheme(p.code)
              return (
                <li key={p.code} className="flex flex-col items-center gap-2">
                  <span
                    className="grid h-10 w-10 place-items-center rounded-full"
                    style={{ background: t.soft, color: t.color }}
                    aria-hidden
                  >
                    <SkillIcon code={p.code} className="h-5 w-5" />
                  </span>
                  <span className="text-balance text-[12.5px] font-medium leading-snug text-ink-2">{p.text}</span>
                </li>
              )
            })}
          </ul>
        </div>
      </main>
    </div>
  )
}
