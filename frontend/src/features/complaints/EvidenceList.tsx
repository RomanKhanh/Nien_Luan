import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi, feedbackApi } from '@/api/endpoints'
import type { Complaint, ComplaintAttachment } from '@/api/types'
import { Skeleton } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { formatDate } from '@/lib/format'

const MB = 1024 * 1024
const formatSize = (bytes: number) =>
  bytes >= MB ? `${(bytes / MB).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`

// loại yêu cầu bắt buộc có video mở hàng (khớp backend); khiếu nại chất lượng không bắt buộc
const EVIDENCE_REQUIRED = ['RETURN', 'EXCHANGE']

/**
 * Bằng chứng khách đính kèm: video mở hàng, ảnh / video tình trạng sản phẩm.
 * Chỉ tải file khi bấm "Xem" (video có thể lớn); file riêng tư nên tải kèm token rồi phát qua object URL.
 */
export function EvidenceList({
  complaint: initial,
  audience,
}: {
  complaint: Complaint
  audience: 'customer' | 'admin'
}) {
  const [openId, setOpenId] = useState<number | null>(null)
  // drawer admin giữ bản complaint lúc mở; sau khi bật / tắt giữ lại thì dùng bản server trả về
  const [updated, setUpdated] = useState<Complaint | null>(null)
  const complaint = updated?.id === initial.id ? updated : initial
  const hasVideo = complaint.attachments.some((a) => a.kind === 'UNBOXING_VIDEO')
  const hasFiles = complaint.attachments.some((a) => !a.purgedAt)

  if (complaint.attachments.length === 0) {
    // yêu cầu gửi trước khi có quy định video mở hàng
    return audience === 'admin' && EVIDENCE_REQUIRED.includes(complaint.type) ? (
      <p className="rounded-md bg-warning-soft px-3 py-2 text-[12.5px] text-[#92400E]">
        Yêu cầu này không có video mở hàng (gửi trước khi áp dụng quy định).
      </p>
    ) : null
  }

  return (
    <div>
      <p className="text-[13px] font-semibold text-ink-2">
        Bằng chứng đính kèm
        {audience === 'admin' && !hasVideo && EVIDENCE_REQUIRED.includes(complaint.type) && (
          <span className="ml-2 text-danger">· thiếu video mở hàng</span>
        )}
      </p>
      <ul className="mt-1.5 space-y-1.5">
        {complaint.attachments.map((a) => (
          <li key={a.id} className="rounded-xl border border-line bg-surface">
            <div className="flex items-center gap-2 px-3 py-2 text-[13px]">
              <span aria-hidden>
                {a.kind === 'UNBOXING_VIDEO' ? '🎥' : a.contentType.startsWith('video/') ? '🎞️' : '🖼️'}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold">
                  {a.kind === 'UNBOXING_VIDEO'
                    ? 'Video mở hàng'
                    : complaint.type === 'QUALITY'
                      ? 'Ảnh / video minh họa'
                      : 'Tình trạng sản phẩm'}
                </span>
                <span className="block truncate text-[12px] text-ink-muted">
                  {a.originalName} · {formatSize(a.sizeBytes)}
                </span>
              </span>
              {a.purgedAt ? (
                <span className="shrink-0 text-[12px] text-ink-faint">Đã xóa tự động {formatDate(a.purgedAt)}</span>
              ) : (
                <button
                  type="button"
                  className="shrink-0 font-semibold text-primary"
                  aria-expanded={openId === a.id}
                  onClick={() => setOpenId(openId === a.id ? null : a.id)}
                >
                  {openId === a.id ? 'Ẩn' : 'Xem'}
                </button>
              )}
            </div>
            {openId === a.id && !a.purgedAt && <EvidencePlayer complaintId={complaint.id} attachment={a} />}
          </li>
        ))}
      </ul>
      <RetentionNote complaint={complaint} audience={audience} hasFiles={hasFiles} onUpdated={setUpdated} />
    </div>
  )
}

// thời hạn lưu bằng chứng (30 ngày sau khi yêu cầu đóng) và nút giữ lại cho admin
function RetentionNote({
  complaint,
  audience,
  hasFiles,
  onUpdated,
}: {
  complaint: Complaint
  audience: 'customer' | 'admin'
  hasFiles: boolean
  onUpdated: (c: Complaint) => void
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const hold = useMutation({
    mutationFn: (next: boolean) => adminApi.setEvidenceHold(complaint.id, next),
    onSuccess: (c) => {
      onUpdated(c)
      queryClient.invalidateQueries({ queryKey: ['admin', 'complaints'] })
      toast.success(c.evidenceHold ? 'Đã giữ lại bằng chứng' : 'Đã bỏ giữ lại bằng chứng')
    },
    onError: (e) => toast.error(e.message),
  })
  if (!hasFiles) {
    return <p className="mt-1.5 text-[12px] text-ink-muted">Tệp đã được xóa sau thời hạn lưu, chỉ còn thông tin.</p>
  }
  return (
    <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] text-ink-muted">
      <span>
        {complaint.evidenceHold
          ? audience === 'admin'
            ? '📌 Đang giữ lại bằng chứng, không tự xóa.'
            : 'Bằng chứng đang được lưu giữ để xử lý.'
          : complaint.evidencePurgeAt
            ? `Ảnh / video sẽ tự xóa vào ${formatDate(complaint.evidencePurgeAt)} (30 ngày sau khi yêu cầu được xử lý xong).`
            : 'Ảnh / video được lưu đến 30 ngày sau khi yêu cầu được xử lý xong.'}
      </span>
      {audience === 'admin' && (
        <button
          type="button"
          className="font-semibold text-primary disabled:opacity-50"
          disabled={hold.isPending}
          onClick={() => hold.mutate(!complaint.evidenceHold)}
        >
          {complaint.evidenceHold ? 'Bỏ giữ lại' : '📌 Giữ lại bằng chứng'}
        </button>
      )}
    </div>
  )
}

function EvidencePlayer({ complaintId, attachment }: { complaintId: number; attachment: ComplaintAttachment }) {
  const file = useQuery({
    queryKey: ['complaint-attachment', complaintId, attachment.id],
    queryFn: () => feedbackApi.complaintAttachment(complaintId, attachment.id),
    staleTime: Infinity,
    gcTime: 5 * 60_000,
  })
  const url = useMemo(() => (file.data ? URL.createObjectURL(file.data) : null), [file.data])
  useEffect(() => () => void (url && URL.revokeObjectURL(url)), [url])

  if (file.isPending) return <Skeleton className="mx-3 mb-3 h-40" />
  if (file.isError || !url) {
    return <p className="px-3 pb-3 text-[12.5px] text-danger">Không tải được tệp: {file.error?.message}</p>
  }
  return (
    <div className="px-3 pb-3">
      {attachment.contentType.startsWith('video/') ? (
        <video src={url} controls playsInline className="max-h-[360px] w-full rounded-lg bg-ink" />
      ) : (
        <img src={url} alt={attachment.originalName} className="max-h-[360px] w-full rounded-lg object-contain" />
      )}
      <a href={url} download={attachment.originalName} className="mt-1.5 inline-block text-[12.5px] font-semibold">
        Tải xuống
      </a>
    </div>
  )
}
