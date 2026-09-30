import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { feedbackApi } from '@/api/endpoints'
import type { ComplaintType, Order } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select, Textarea } from '@/components/ui/Field'
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
  // orderItemId -> số lượng bị lỗi; không chọn dòng nào = khiếu nại chung cả đơn
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()

  const submit = useMutation({
    mutationFn: () =>
      feedbackApi.createComplaint(order.id, {
        type,
        content,
        items: Object.entries(quantities).map(([orderItemId, quantity]) => ({
          orderItemId: Number(orderItemId),
          quantity,
        })),
      }),
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
      </div>
    </Modal>
  )
}
