import type { Complaint } from '@/api/types'
import { formatDateTime } from '@/lib/format'
import { useNow } from '@/lib/useNow'

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * Hạn gửi hàng về của yêu cầu trả hàng đã duyệt (7 ngày kể từ khi duyệt), hoặc nhãn
 * "tự từ chối do khách trễ hạn" khi hệ thống đã đóng yêu cầu. Không áp dụng thì không hiện gì.
 */
export function ReturnDeadline({ complaint, audience }: { complaint: Complaint; audience: 'customer' | 'admin' }) {
  const now = useNow()
  if (complaint.returnExpired) {
    return (
      <p className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-danger-soft px-3 py-1 text-[12.5px] font-semibold text-danger">
        <span aria-hidden>⏰</span>
        Tự từ chối: {audience === 'customer' ? 'bạn chưa gửi hàng về' : 'khách chưa gửi hàng về'} đúng hạn
        {complaint.returnDeadline && ` (hạn ${formatDateTime(complaint.returnDeadline)})`}
      </p>
    )
  }
  if (complaint.status !== 'PROCESSING' || !complaint.returnDeadline) return null

  const msLeft = new Date(complaint.returnDeadline).getTime() - now
  if (msLeft <= 0) {
    return (
      <p className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-danger-soft px-3 py-1 text-[12.5px] font-semibold text-danger">
        <span aria-hidden>⏰</span>
        Đã quá hạn gửi hàng về ({formatDateTime(complaint.returnDeadline)}), yêu cầu sẽ tự bị từ chối
      </p>
    )
  }
  const daysLeft = Math.ceil(msLeft / DAY_MS)
  return (
    <p
      className={`mt-2 inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12.5px] font-semibold ${
        daysLeft <= 2 ? 'bg-[#fff7e0] text-[#8a5a00]' : 'bg-sky-soft text-sky-deep'
      }`}
    >
      <span aria-hidden>📦</span>
      {audience === 'customer' ? 'Gửi hàng về trước' : 'Hạn khách gửi hàng về'}{' '}
      {formatDateTime(complaint.returnDeadline)}
      {' · '}còn {daysLeft} ngày
    </p>
  )
}
