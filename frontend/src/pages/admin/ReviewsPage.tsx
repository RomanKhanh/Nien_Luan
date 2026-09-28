import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi } from '@/api/endpoints'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { Pill, Stars } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { formatDateTime } from '@/lib/format'

const FILTERS: { value: boolean | undefined; label: string }[] = [
  { value: undefined, label: 'Tất cả' },
  { value: true, label: 'Đang hiển thị' },
  { value: false, label: 'Đã ẩn' },
]

export default function ReviewsPage() {
  const [visible, setVisible] = useState<boolean | undefined>()
  const [page, setPage] = useState(0)
  const params = { visible, page, size: 15 }
  const reviews = useQuery({
    queryKey: ['admin', 'reviews', params],
    queryFn: () => adminApi.reviews(params),
    placeholderData: keepPreviousData,
  })
  const toast = useToast()
  const queryClient = useQueryClient()
  const setVisibility = useMutation({
    mutationFn: ({ id, value }: { id: number; value: boolean }) => adminApi.setReviewVisibility(id, value),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] })
      queryClient.invalidateQueries({ queryKey: ['reviews', r.productId] })
      queryClient.invalidateQueries({ queryKey: ['product', r.productId] })
      toast.success(r.visible ? 'Đã hiện lại đánh giá' : 'Đã ẩn đánh giá')
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <AdminHeader title="Đánh giá" description="Kiểm duyệt nhận xét: ẩn nội dung không phù hợp (có thể hiện lại)." />
      <div className="card overflow-hidden">
        <div className="flex gap-1.5 border-b border-line p-3">
          {FILTERS.map((f) => (
            <button
              key={f.label}
              type="button"
              onClick={() => {
                setVisible(f.value)
                setPage(0)
              }}
              className={`rounded-full px-3.5 py-1.5 text-[13px] font-semibold ${visible === f.value ? 'bg-ink text-white' : 'text-ink-2 hover:bg-muted'}`}
            >
              {f.label}
            </button>
          ))}
        </div>
        {reviews.isPending ? (
          <PageLoader />
        ) : reviews.isError ? (
          <ErrorState message={reviews.error.message} onRetry={() => reviews.refetch()} />
        ) : reviews.data.content.length === 0 ? (
          <EmptyState title="Chưa có đánh giá" />
        ) : (
          <ul className={`divide-y divide-line ${reviews.isPlaceholderData ? 'opacity-60' : ''}`}>
            {reviews.data.content.map((r) => (
              <li key={r.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-[14px]">
                    <b>{r.customerName}</b>
                    <Stars value={r.rating} size={13} />
                    {!r.visible && <Pill tone="gray">Đã ẩn</Pill>}
                    <span className="text-[12.5px] text-ink-faint">{formatDateTime(r.createdAt)}</span>
                  </div>
                  <Link to={`/products/${r.productId}`} className="text-[13px]">
                    {r.productName}
                  </Link>
                  <p className="mt-1 text-[14px] text-ink-2">
                    {r.comment ?? <i className="text-ink-faint">Không có nhận xét</i>}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant={r.visible ? 'danger-outline' : 'secondary'}
                  loading={setVisibility.isPending && setVisibility.variables?.id === r.id}
                  onClick={() => setVisibility.mutate({ id: r.id, value: !r.visible })}
                >
                  {r.visible ? 'Ẩn' : 'Hiện lại'}
                </Button>
              </li>
            ))}
          </ul>
        )}
        {reviews.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={reviews.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>
    </>
  )
}
