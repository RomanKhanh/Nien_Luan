import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { feedbackApi } from '@/api/endpoints'
import type { ComplaintType, Order } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import { COMPLAINT_ALLOWED, COMPLAINT_TYPE } from '@/lib/labels'

// đổi / trả hàng bắt buộc kèm video mở hàng (khớp ComplaintService.EVIDENCE_REQUIRED_TYPES);
// khiếu nại chất lượng chỉ có ảnh / video minh họa, không bắt buộc
const UNBOXING_REQUIRED: ComplaintType[] = ['RETURN', 'EXCHANGE']
const ATTACHABLE: ComplaintType[] = ['RETURN', 'EXCHANGE', 'QUALITY']
const MB = 1024 * 1024
const MAX_VIDEO_BYTES = 100 * MB
const MAX_IMAGE_BYTES = 10 * MB
const MAX_TOTAL_BYTES = 200 * MB
const MAX_CONDITION_FILES = 5
const VIDEO_EXT = ['mp4', 'mov', 'webm']
const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'webp']

const extOf = (file: File) => file.name.split('.').pop()?.toLowerCase() ?? ''
const formatSize = (bytes: number) =>
  bytes >= MB ? `${(bytes / MB).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`

// kiểm tra sớm ở trình duyệt để khách khỏi chờ tải lên rồi mới bị từ chối; backend vẫn kiểm tra lại
function checkFile(file: File, allowImages: boolean): string | null {
  if (file.size === 0) return `${file.name}: tệp trống`
  const ext = extOf(file)
  const isVideo = VIDEO_EXT.includes(ext)
  if (!isVideo && !(allowImages && IMAGE_EXT.includes(ext))) {
    return allowImages
      ? `${file.name}: chỉ nhận ảnh JPG, PNG, WebP hoặc video MP4, MOV, WebM`
      : `${file.name}: video mở hàng phải là MP4, MOV hoặc WebM`
  }
  const limit = isVideo ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES
  return file.size > limit ? `${file.name}: tối đa ${limit / MB}MB (tệp này ${formatSize(file.size)})` : null
}

// gửi phản hồi / khiếu nại / yêu cầu đổi - trả - huỷ cho một đơn (mockup màn 13)
// chỉ mount nội dung khi mở để form luôn bắt đầu trống
export function ComplaintModal({ open, onClose, order }: { open: boolean; onClose: () => void; order: Order }) {
  return open ? <ComplaintForm onClose={onClose} order={order} /> : null
}

function ComplaintForm({ onClose, order }: { onClose: () => void; order: Order }) {
  const allowedTypes = (Object.keys(COMPLAINT_TYPE) as ComplaintType[]).filter((t) =>
    COMPLAINT_ALLOWED[t].includes(order.status),
  )
  const [type, setType] = useState<ComplaintType>(allowedTypes[0])
  // orderItemId -> số lượng bị lỗi; không chọn dòng nào = khiếu nại chung cả đơn
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [video, setVideo] = useState<File | null>(null)
  const [conditionFiles, setConditionFiles] = useState<File[]>([])
  const [evidenceError, setEvidenceError] = useState<string | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const needsUnboxing = UNBOXING_REQUIRED.includes(type)
  const canAttach = ATTACHABLE.includes(type)
  const toast = useToast()
  const queryClient = useQueryClient()

  const submit = useMutation({
    mutationFn: () =>
      feedbackApi.createComplaint(
        order.id,
        {
          type,
          content,
          items: Object.entries(quantities).map(([orderItemId, quantity]) => ({
            orderItemId: Number(orderItemId),
            quantity,
          })),
        },
        canAttach ? { unboxingVideo: needsUnboxing ? (video ?? undefined) : undefined, conditionFiles } : undefined,
        setProgress,
      ),
    onSettled: () => setProgress(null),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-complaints'] })
      toast.success('Đã gửi yêu cầu, BrainBlocks sẽ phản hồi sớm')
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })

  const toggleItem = (id: number, checked: boolean) =>
    setQuantities((prev) => {
      const next = { ...prev }
      if (checked) next[id] = 1
      else delete next[id]
      return next
    })

  return (
    <Modal
      open
      onClose={onClose}
      title="Gửi phản hồi / yêu cầu"
      footer={
        <Button
          loading={submit.isPending}
          onClick={() => {
            if (!content.trim()) {
              setError('Vui lòng mô tả vấn đề')
              return
            }
            if (canAttach) {
              if (needsUnboxing && !video) {
                setEvidenceError('Vui lòng đính kèm video mở hàng')
                return
              }
              const sent = needsUnboxing && video ? [video, ...conditionFiles] : conditionFiles
              const total = sent.reduce((sum, f) => sum + f.size, 0)
              if (total > MAX_TOTAL_BYTES) {
                setEvidenceError(`Tổng dung lượng tối đa ${MAX_TOTAL_BYTES / MB}MB mỗi lần gửi`)
                return
              }
            }
            submit.mutate()
          }}
        >
          {progress !== null ? `Đang tải lên ${Math.round(progress * 100)}%` : 'Gửi yêu cầu'}
        </Button>
      }
    >
      <p className="text-[14px] text-ink-muted">
        Đơn <b className="text-ink">{order.orderCode}</b>
      </p>
      <div className="mt-4 space-y-4">
        <Field
          label="Loại yêu cầu"
          htmlFor="complaintType"
          hint={order.status !== 'DELIVERED' ? 'Đổi / trả hàng mở sau khi đơn đã giao.' : undefined}
        >
          <Select id="complaintType" value={type} onChange={(e) => setType(e.target.value as ComplaintType)}>
            {allowedTypes.map((t) => (
              <option key={t} value={t}>
                {COMPLAINT_TYPE[t]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Sản phẩm liên quan" hint="Không chọn sản phẩm nào = phản hồi chung cho cả đơn.">
          <ul className="space-y-2">
            {order.items.map((i) => {
              const checked = i.id in quantities
              return (
                <li key={i.id} className="flex items-center gap-3">
                  <label className="flex min-w-0 flex-1 items-center gap-2 text-[14px]">
                    <input
                      type="checkbox"
                      className="h-4 w-4 shrink-0 accent-primary"
                      checked={checked}
                      onChange={(e) => toggleItem(i.id, e.target.checked)}
                    />
                    <span className="truncate">
                      {i.productName} <span className="text-ink-muted">× {i.quantity}</span>
                    </span>
                  </label>
                  {checked && i.quantity > 1 && (
                    <Input
                      type="number"
                      aria-label={`Số lượng bị lỗi của ${i.productName}`}
                      className="w-20"
                      min={1}
                      max={i.quantity}
                      value={quantities[i.id]}
                      onChange={(e) => {
                        const value = Math.min(i.quantity, Math.max(1, Math.floor(Number(e.target.value)) || 1))
                        setQuantities((prev) => ({ ...prev, [i.id]: value }))
                      }}
                    />
                  )}
                </li>
              )
            })}
          </ul>
        </Field>
        <Field label="Nội dung" htmlFor="complaintContent" required error={error ?? undefined}>
          <Textarea
            id="complaintContent"
            maxLength={2000}
            placeholder="Mô tả vấn đề bạn gặp phải…"
            invalid={Boolean(error)}
            value={content}
            onChange={(e) => {
              setContent(e.target.value)
              setError(null)
            }}
          />
        </Field>
        {(type === 'RETURN' || type === 'EXCHANGE') && (
          <p className="rounded-xl bg-sky-soft px-3 py-2.5 text-[12.5px] text-ink-2">
            📦 Sau khi yêu cầu được duyệt, bạn có <b>7 ngày</b> để gửi hàng về BrainBlocks. Quá hạn thì yêu cầu tự đóng
            và không được đổi / trả nữa.
          </p>
        )}
        {canAttach && (
          <EvidenceFields
            requireUnboxing={needsUnboxing}
            video={video}
            conditionFiles={conditionFiles}
            error={evidenceError}
            progress={progress}
            onVideo={(file) => {
              const problem = file && checkFile(file, false)
              setEvidenceError(problem || null)
              setVideo(problem ? null : file)
            }}
            onAddConditions={(files) => {
              const problems = files.map((f) => checkFile(f, true)).filter(Boolean)
              const ok = files.filter((f) => !checkFile(f, true))
              const next = [...conditionFiles, ...ok].slice(0, MAX_CONDITION_FILES)
              setEvidenceError(
                problems[0] ??
                  (conditionFiles.length + ok.length > MAX_CONDITION_FILES
                    ? `Tối đa ${MAX_CONDITION_FILES} ảnh / video`
                    : null),
              )
              setConditionFiles(next)
            }}
            onRemoveCondition={(index) => setConditionFiles((prev) => prev.filter((_, i) => i !== index))}
          />
        )}
      </div>
    </Modal>
  )
}

// đổi / trả: video mở hàng (bắt buộc) + ảnh / video tình trạng (tuỳ chọn);
// khiếu nại chất lượng: chỉ ảnh / video minh họa (tuỳ chọn)
function EvidenceFields({
  requireUnboxing,
  video,
  conditionFiles,
  error,
  progress,
  onVideo,
  onAddConditions,
  onRemoveCondition,
}: {
  requireUnboxing: boolean
  video: File | null
  conditionFiles: File[]
  error: string | null
  progress: number | null
  onVideo: (file: File | null) => void
  onAddConditions: (files: File[]) => void
  onRemoveCondition: (index: number) => void
}) {
  return (
    <div className="space-y-4 rounded-2xl border-2 border-dashed border-line p-4">
      {requireUnboxing && (
        <div>
          <p className="text-[14px] font-bold">
            Video mở hàng <span className="text-danger">*</span>
          </p>
          <p className="mt-0.5 text-[12.5px] text-ink-muted">
            Quay liên tục từ lúc hộp còn nguyên tem đến khi lấy sản phẩm ra. Video giúp xác định hàng hỏng do vận
            chuyển, giao nhầm hay lý do khác. MP4, MOV hoặc WebM, tối đa 100MB.
          </p>
          <label className="mt-2 flex cursor-pointer items-center gap-3 rounded-xl bg-muted px-3 py-2.5 text-[13.5px] hover:bg-sky-soft">
            <span aria-hidden>🎥</span>
            <span className="min-w-0 flex-1 truncate">
              {video ? `${video.name} · ${formatSize(video.size)}` : 'Chọn video mở hàng…'}
            </span>
            <span className="font-semibold text-primary">{video ? 'Đổi' : 'Chọn tệp'}</span>
            <input
              type="file"
              className="sr-only"
              accept="video/mp4,video/quicktime,video/webm,.mp4,.mov,.webm"
              onChange={(e) => {
                onVideo(e.target.files?.[0] ?? null)
                e.target.value = ''
              }}
            />
          </label>
        </div>
      )}

      <div>
        <p className="text-[14px] font-bold">
          {requireUnboxing ? 'Ảnh / video tình trạng sản phẩm' : 'Ảnh / video minh họa'}
        </p>
        <p className="mt-0.5 text-[12.5px] text-ink-muted">
          {requireUnboxing ? '' : 'Ảnh hoặc video cho thấy lỗi của sản phẩm giúp BrainBlocks xử lý nhanh hơn. '}
          Không bắt buộc, tối đa {MAX_CONDITION_FILES} tệp. Ảnh tối đa 10MB, video tối đa 100MB.
        </p>
        {conditionFiles.length > 0 && (
          <ul className="mt-2 space-y-1">
            {conditionFiles.map((f, i) => (
              <li key={`${f.name}-${i}`} className="flex items-center gap-2 text-[13px]">
                <span aria-hidden>{VIDEO_EXT.includes(extOf(f)) ? '🎞️' : '🖼️'}</span>
                <span className="min-w-0 flex-1 truncate">
                  {f.name} <span className="text-ink-muted">· {formatSize(f.size)}</span>
                </span>
                <button
                  type="button"
                  className="font-semibold text-danger"
                  aria-label={`Bỏ ${f.name}`}
                  onClick={() => onRemoveCondition(i)}
                >
                  Bỏ
                </button>
              </li>
            ))}
          </ul>
        )}
        {conditionFiles.length < MAX_CONDITION_FILES && (
          <label className="mt-2 inline-flex cursor-pointer items-center gap-2 rounded-full border-2 border-line px-3 py-1.5 text-[13px] font-semibold hover:border-sky">
            + Thêm ảnh / video
            <input
              type="file"
              multiple
              className="sr-only"
              accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm,.mov"
              onChange={(e) => {
                onAddConditions(Array.from(e.target.files ?? []))
                e.target.value = ''
              }}
            />
          </label>
        )}
      </div>

      <p className="text-[12px] text-ink-muted">
        Ảnh / video chỉ BrainBlocks và bạn xem được, lưu đến 30 ngày sau khi yêu cầu được xử lý xong rồi tự xóa.
      </p>

      {error && (
        <p role="alert" className="text-[13px] font-semibold text-danger">
          {error}
        </p>
      )}
      {progress !== null && (
        <div className="h-2 overflow-hidden rounded-full bg-muted" aria-label="Tiến độ tải lên">
          <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${progress * 100}%` }} />
        </div>
      )}
    </div>
  )
}
