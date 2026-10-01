import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { Complaint, ComplaintStatus } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { ComplaintStatusBadge, OrderStatusBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Field, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { complaintItemsText, formatDateTime } from '@/lib/format'
import { COMPLAINT_STATUS, COMPLAINT_TYPE } from '@/lib/labels'

const TABS: (ComplaintStatus | undefined)[] = [undefined, 'PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED']

export default function ComplaintsPage() {
  const [status, setStatus] = useState<ComplaintStatus | undefined>('PENDING')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Complaint | null>(null)
  const params = { status, page, size: 15 }
  const complaints = useQuery({
    queryKey: ['admin', 'complaints', params],
    queryFn: () => adminApi.complaints(params),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <AdminHeader
        title="Khiếu nại & yêu cầu"
        description="Tiếp nhận phản hồi, yêu cầu đổi / trả / huỷ và trả lời khách."
      />
      <div className="card overflow-hidden">
        <div className="flex gap-1.5 overflow-x-auto border-b border-line p-3">
          {TABS.map((t) => (
            <button
              key={t ?? 'ALL'}
              type="button"
              onClick={() => {
                setStatus(t)
                setPage(0)
              }}
              className={`shrink-0 rounded-full px-3.5 py-1.5 text-[13px] font-semibold ${status === t ? 'bg-ink text-white' : 'text-ink-2 hover:bg-muted'}`}
            >
              {t ? COMPLAINT_STATUS[t].label : 'Tất cả'}
            </button>
          ))}
        </div>
        {complaints.isPending ? (
          <PageLoader />
        ) : complaints.isError ? (
          <ErrorState message={complaints.error.message} onRetry={() => complaints.refetch()} />
        ) : complaints.data.content.length === 0 ? (
          <EmptyState title="Không có yêu cầu nào" />
        ) : (
          <ul className="divide-y divide-line">
            {complaints.data.content.map((c) => (
              <li key={c.id}>
                <button type="button" onClick={() => setSelected(c)} className="block w-full p-4 text-left hover:bg-bg">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-semibold">
                      {COMPLAINT_TYPE[c.type]} · {c.orderCode}
                      {c.items.length > 0 && (
                        <span className="font-normal text-ink-muted"> · {complaintItemsText(c.items)}</span>
                      )}
                    </p>
                    <ComplaintStatusBadge status={c.status} />
                  </div>
                  <p className="mt-1 line-clamp-2 text-[14px] text-ink-2">{c.content}</p>
                  <p className="mt-1 text-[12.5px] text-ink-muted">
                    {c.customerName} · {formatDateTime(c.createdAt)}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
        {complaints.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={complaints.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>
      {selected && <HandleDrawer complaint={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function HandleDrawer({ complaint, onClose }: { complaint: Complaint; onClose: () => void }) {
  const [response, setResponse] = useState(complaint.response ?? '')
  const toast = useToast()
  const queryClient = useQueryClient()
  const closed = complaint.status === 'RESOLVED' || complaint.status === 'REJECTED'
  const handle = useMutation({
    mutationFn: (status: ComplaintStatus) => adminApi.handleComplaint(complaint.id, { status, response }),
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'complaints'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'stats'] })
      toast.success(`Đã cập nhật: ${COMPLAINT_STATUS[c.status].label}`)
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <Modal open onClose={onClose} variant="drawer" title={`${COMPLAINT_TYPE[complaint.type]} · ${complaint.orderCode}`}>
      <div className="space-y-5 text-[14px]">
        <div className="flex flex-wrap items-center gap-2">
          <ComplaintStatusBadge status={complaint.status} />
          <span className="text-ink-muted">Đơn hàng:</span>
          <OrderStatusBadge status={complaint.orderStatus} />
        </div>
        <div>
          <p className="font-semibold">
            {complaint.customerName} <span className="font-normal text-ink-muted">· {complaint.customerEmail}</span>
          </p>
          <p className="text-[12.5px] text-ink-muted">
            Gửi lúc {formatDateTime(complaint.createdAt)}
            {complaint.items.length > 0 && ` · Sản phẩm: ${complaintItemsText(complaint.items)}`}
          </p>
          <p className="mt-3 whitespace-pre-line rounded-md bg-muted px-4 py-3 text-ink-2">{complaint.content}</p>
        </div>
        {complaint.handledByName && (
          <p className="text-[12.5px] text-ink-muted">
            Người xử lý: {complaint.handledByName}
            {complaint.handledAt && ` · đóng lúc ${formatDateTime(complaint.handledAt)}`}
          </p>
        )}
        <Field label="Phản hồi gửi khách" htmlFor="response" hint={
            complaint.status === 'PENDING'
              ? 'Không bắt buộc khi tiếp nhận, bắt buộc khi từ chối.'
              : 'Bắt buộc khi đánh dấu đã giải quyết.'
          }>
          <Textarea
            id="response"
            value={response}
            maxLength={2000}
            disabled={closed}
            onChange={(e) => setResponse(e.target.value)}
          />
        </Field>
        {(complaint.type === 'CANCEL' || complaint.type === 'RETURN' || complaint.type === 'EXCHANGE') && !closed && (
          <p className="rounded-md bg-warning-soft px-3 py-2.5 text-[12.5px] text-[#92400E]">
            Chấp nhận yêu cầu không tự đổi trạng thái đơn. Nếu cần huỷ đơn, hãy cập nhật ở trang Đơn hàng.
          </p>
        )}
        {closed ? (
          <p className="text-ink-muted">Yêu cầu đã đóng.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {complaint.status === 'PENDING' ? (
              <>
                <Button
                  loading={handle.isPending && handle.variables === 'PROCESSING'}
                  disabled={handle.isPending}
                  onClick={() => handle.mutate('PROCESSING')}
                >
                  Tiếp nhận
                </Button>
                <Button
                  variant="danger-outline"
                  loading={handle.isPending && handle.variables === 'REJECTED'}
                  disabled={handle.isPending}
                  onClick={() => handle.mutate('REJECTED')}
                >
                  Từ chối
                </Button>
              </>
            ) : (
              <Button
                loading={handle.isPending && handle.variables === 'RESOLVED'}
                onClick={() => handle.mutate('RESOLVED')}
              >
                Đã giải quyết
              </Button>
            )}
          </div>
        )}
      </div>
    </Modal>
  )
}
