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

const FOOTER_LINKS = [
  { to: '/policies#terms', label: 'Điều khoản' },
  { to: '/policies#privacy', label: 'Chính sách bảo mật' },
  { to: '/policies#support', label: 'Hỗ trợ' },
]

// Bố cục màn đăng nhập / đăng ký / quên & đặt lại mật khẩu (mockup màn 07).
// Từ lg: 2 cột, cột trái nền trời xanh như trang chủ (~40-43% bề ngang), cột phải chỉ có form.
// Dưới lg: 1 cột, bỏ hero, chỉ giữ logo + linh vật nhỏ + form.
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen bg-surface lg:grid-cols-[2fr_3fr] xl:grid-cols-[43fr_57fr]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-sky to-sky-deep p-10 text-white lg:flex xl:p-12">
        <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
        <Bubbles />
        <div className="relative w-fit rounded-2xl bg-white px-3 py-2 shadow-card">
          <Logo />
        </div>
        <div className="relative py-6">
          <div className="relative mx-auto mb-6 aspect-square w-full max-w-[230px] xl:mb-8 xl:max-w-[280px] 2xl:max-w-[320px] short:mb-4 short:max-w-[230px]">
            <Mascot className="absolute left-1/2 top-1/2 w-[58%] -translate-x-1/2 -translate-y-1/2 drop-shadow-[0_12px_18px_rgb(13_27_62/0.25)]" />
            {FLOATERS.map((f) => {
              const t = skillTheme(f.code)
              return (
                <span
                  key={f.code}
                  className={`absolute ${f.pos} grid h-12 w-12 animate-float-slow place-items-center rounded-full border-4 bg-white shadow-pop xl:h-16 xl:w-16 xl:border-[5px] short:h-12 short:w-12 short:border-4`}
                  style={{ borderColor: t.color, color: t.color, animationDelay: f.delay }}
                  aria-hidden
                >
                  <SkillIcon code={f.code} className="h-5 w-5 xl:h-7 xl:w-7 short:h-5 short:w-5" />
                </span>
              )
            })}
          </div>
          <p className="sticker-text sticker-text-thin font-display text-[24px] font-extrabold leading-[1.1] xl:text-[32px] 2xl:text-[36px]">
            <span className="block text-[#1c5fc6]">Theo dõi lộ trình kỹ năng</span>
            <span className="block text-sun">cho từng bé trong nhà</span>
          </p>
          <p className="mt-4 max-w-md text-[14px] text-white/90 xl:text-[15px]">
            Một tài khoản phụ huynh, nhiều hồ sơ bé. Gợi ý đồ chơi luôn kèm lý do.
          </p>
        </div>
        <p className="relative text-[13px] text-white/80">© {YEAR} BrainBlocks</p>
      </aside>

      <main className="auth-form relative flex min-w-0 flex-col overflow-hidden px-5 pb-5 pt-5 sm:px-8 sm:pt-8 short:pt-5">
        <span className="absolute -right-20 -top-20 hidden h-48 w-48 rounded-full bg-sky-soft lg:block" aria-hidden />
        <div className="relative mx-auto flex w-full max-w-[440px] min-[2200px]:ml-[12vw] items-center justify-between gap-4">
          <div className="lg:hidden">
            <Logo />
          </div>
          <Link
            to="/"
            className="-ml-2 rounded-full px-2 py-1.5 text-[13.5px] font-semibold text-ink-2 transition hover:bg-muted hover:text-ink"
          >
            <span aria-hidden>← </span>Quay lại cửa hàng
          </Link>
        </div>

        {/* pb lớn hơn pt để form nằm hơi cao hơn chính giữa; màn siêu rộng (>=2200px) thì dịch cả cột form
            (link quay lại, form, footer) sang trái thay vì căn giữa cột phải, để form không trôi quá xa */}
        <div className="relative mx-auto flex w-full max-w-[440px] min-[2200px]:ml-[12vw] flex-1 flex-col justify-center pb-[6vh] pt-8 short:pt-5">
          <Mascot className="mb-3 w-14 animate-float lg:hidden" />
          {children}
        </div>

        <footer className="relative mx-auto w-full max-w-[440px] min-[2200px]:ml-[12vw] text-center text-[12.5px] text-ink-muted">
          <p className="text-balance">Tài khoản BrainBlocks dành cho phụ huynh hoặc người giám hộ.</p>
          <nav aria-label="Thông tin pháp lý" className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1">
            {FOOTER_LINKS.map((l) => (
              <Link key={l.to} to={l.to} className="text-ink-muted hover:text-primary">
                {l.label}
              </Link>
            ))}
          </nav>
        </footer>
      </main>
    </div>
  )
}
