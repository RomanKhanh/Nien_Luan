import type { ReactNode } from 'react'
import { Logo } from '@/components/layout/Logo'

const YEAR = new Date().getFullYear()

// bố cục 2 cột của màn đăng nhập / đăng ký (mockup màn 07)
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1.1fr]">
      <aside className="hidden flex-col justify-between bg-secondary p-12 text-white lg:flex">
        <div className="[&_a]:text-white [&_a:hover]:text-white [&_span.text-ink-muted]:text-[#BFC7DA]">
          <Logo />
        </div>
        <div>
          <p className="text-[34px] font-extrabold leading-tight tracking-[-0.02em]">
            Theo dõi lộ trình kỹ năng
            <br />
            <span className="text-accent">cho từng bé trong nhà</span>
          </p>
          <p className="mt-4 max-w-md text-[15px] text-[#BFC7DA]">
            Một tài khoản phụ huynh, nhiều hồ sơ bé. Gợi ý đồ chơi luôn kèm lý do.
          </p>
        </div>
        <p className="text-[13px] text-[#BFC7DA]">© {YEAR} BrainBlocks</p>
      </aside>
      <main className="flex flex-col px-4 py-8 sm:px-8">
        <div className="lg:hidden">
          <Logo />
        </div>
        <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col justify-center py-8">{children}</div>
      </main>
    </div>
  )
}
