import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { feedbackApi } from '@/api/endpoints'
import { ComplaintStatusBadge, Stars } from '@/components/ui/Badges'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { formatDateTime } from '@/lib/format'
import { COMPLAINT_TYPE } from '@/lib/labels'
import { AccountShell } from './AccountShell'

export default function FeedbackPage() {
  const complaints = useQuery({ queryKey: ['my-complaints'], queryFn: feedbackApi.myComplaints })
  const reviews = useQuery({ queryKey: ['my-reviews'], queryFn: feedbackApi.myReviews })

  return (
    <AccountShell>
      <h1 className="h1">Phản hồi & khiếu nại</h1>
      <p className="mt-1 text-[14px] text-ink-muted">
        Gửi yêu cầu đổi / trả / huỷ hoặc khiếu nại từ trang chi tiết của từng <Link to="/orders">đơn hàng</Link>.
      </p>

      <section className="mt-6">
        <h2 className="h2 mb-3">Yêu cầu đã gửi</h2>
        {complaints.isPending ? (
          <PageLoader />
        ) : complaints.isError ? (
          <ErrorState message={complaints.error.message} onRetry={() => complaints.refetch()} />
        ) : complaints.data.length === 0 ? (
          <div className="card">
            <EmptyState title="Chưa có yêu cầu nào" />
          </div>
        ) : (
          <ul className="space-y-3">
            {complaints.data.map((c) => (
              <li key={c.id} className="card p-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-bold">
                    {COMPLAINT_TYPE[c.type]} ·{' '}
                    <Link to={`/orders/${c.orderId}`} className="font-bold">
                      {c.orderCode}
                    </Link>
                  </p>
                  <ComplaintStatusBadge status={c.status} />
                </div>
                <p className="mt-1 text-[12.5px] text-ink-muted">
                  {formatDateTime(c.createdAt)}
                  {c.productName && ` · ${c.productName}`}
                </p>
                <p className="mt-2 whitespace-pre-line text-[14px] text-ink-2">{c.content}</p>
                {c.response && (
                  <div className="mt-3 rounded-md bg-primary-soft px-4 py-3 text-[14px]">
                    <p className="text-[12.5px] font-semibold text-primary-hover">
                      Phản hồi từ BrainBlocks{c.handledAt && ` · ${formatDateTime(c.handledAt)}`}
                    </p>
                    <p className="mt-1 whitespace-pre-line text-ink-2">{c.response}</p>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mt-8">
        <h2 className="h2 mb-3">Đánh giá của tôi</h2>
        {reviews.data?.length === 0 && <p className="text-[14px] text-ink-muted">Bạn chưa đánh giá sản phẩm nào.</p>}
        <ul className="space-y-3">
          {reviews.data?.map((r) => (
            <li key={r.id} className="card p-5">
              <div className="flex flex-wrap items-center gap-2">
                <Link to={`/products/${r.productId}`} className="font-semibold">
                  {r.productName}
                </Link>
                <Stars value={r.rating} size={13} />
                {!r.visible && (
                  <span className="text-[12px] font-semibold text-danger">Đã bị ẩn bởi quản trị viên</span>
                )}
              </div>
              {r.comment && <p className="mt-1.5 text-[14px] text-ink-2">{r.comment}</p>}
            </li>
          ))}
        </ul>
      </section>
    </AccountShell>
  )
}
