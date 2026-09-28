import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { feedbackApi } from '@/api/endpoints'
import type { ComplaintType, Order } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Field, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import { COMPLAINT_ALLOWED, COMPLAINT_TYPE } from '@/lib/labels'

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
  const [itemId, setItemId] = useState<number | null>(null)
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()

  const submit = useMutation({
    mutationFn: () => feedbackApi.createComplaint({ orderId: order.id, orderItemId: itemId, type, content }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-complaints'] })
      toast.success('Đã gửi yêu cầu, BrainBlocks sẽ phản hồi sớm')
      onClose()
    },
    onError: (e) => toast.error(e.message),
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
            submit.mutate()
          }}
        >
          Gửi yêu cầu
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
        <Field label="Sản phẩm liên quan" htmlFor="complaintItem">
          <Select
            id="complaintItem"
            value={itemId ?? ''}
            onChange={(e) => setItemId(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">Cả đơn hàng</option>
            {order.items.map((i) => (
              <option key={i.id} value={i.id}>
                {i.productName}
              </option>
            ))}
          </Select>
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
      </div>
    </Modal>
  )
}
