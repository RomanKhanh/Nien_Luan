import { Mascot } from '@/components/decor/Decor'
import { ButtonLink } from '@/components/ui/Button'

export default function NotFoundPage() {
  return (
    <div className="container-page flex flex-col items-center py-16 text-center">
      <div className="relative">
        <p className="sticker-text font-display text-[120px] font-extrabold leading-none text-sky sm:text-[160px]">
          4<span className="text-sun">0</span>4
        </p>
        <Mascot className="absolute -right-10 -top-6 w-20 rotate-12 animate-float sm:-right-16 sm:w-28" />
      </div>
      <h1 className="h1 mt-4">Ối! Không tìm thấy trang</h1>
      <p className="mt-2 max-w-sm text-[14.5px] text-ink-muted">
        Đường dẫn có thể đã thay đổi hoặc không tồn tại. Cùng quay về khu vui chơi nhé!
      </p>
      <ButtonLink to="/" size="lg" className="mt-6">
        Về trang chủ
      </ButtonLink>
    </div>
  )
}
