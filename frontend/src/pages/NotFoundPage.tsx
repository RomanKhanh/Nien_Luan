import { ButtonLink } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/States'

export default function NotFoundPage() {
  return (
    <div className="container-page">
      <EmptyState
        icon="?"
        title="Không tìm thấy trang"
        description="Đường dẫn có thể đã thay đổi hoặc không tồn tại."
        action={<ButtonLink to="/">Về trang chủ</ButtonLink>}
      />
    </div>
  )
}
