import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '@/api/endpoints'
import { Button } from '@/components/ui/Button'
import { Field, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'

// backend chỉ nhận đánh giá cho sản phẩm trong đơn đã giao, mỗi sản phẩm một lần
export function WriteReviewModal({
  open,
  onClose,
  productId,
  productName,
}: {
  open: boolean
  onClose: () => void
  productId: number
  productName: string
}) {
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const toast = useToast()
  const queryClient = useQueryClient()
  const submit = useMutation({
    mutationFn: () => catalogApi.createReview(productId, { rating, comment }),
    onSuccess: () => {
      toast.success('Cảm ơn bạn đã đánh giá!')
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] })
      queryClient.invalidateQueries({ queryKey: ['product', productId] })
      queryClient.invalidateQueries({ queryKey: ['my-reviews'] })
      setComment('')
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })
  const labels = ['', 'Rất tệ', 'Chưa tốt', 'Bình thường', 'Tốt', 'Rất tốt']

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Đánh giá sản phẩm"
      footer={
        <Button loading={submit.isPending} onClick={() => submit.mutate()}>
          Gửi đánh giá
        </Button>
      }
    >
      <p className="font-semibold">{productName}</p>
      <div className="mt-4 flex items-center gap-1" role="radiogroup" aria-label="Số sao">
        {[1, 2, 3, 4, 5].map((s) => (
          <button
            type="button"
            key={s}
            role="radio"
            aria-checked={rating === s}
            aria-label={`${s} sao`}
            onClick={() => setRating(s)}
            className={`text-[32px] leading-none ${s <= rating ? 'text-accent' : 'text-line-strong'}`}
          >
            ★
          </button>
        ))}
        <span className="ml-2 text-[14px] font-semibold text-ink-2">{labels[rating]}</span>
      </div>
      <Field label="Nhận xét" className="mt-4" hint="Chia sẻ trải nghiệm của bé với đồ chơi (không bắt buộc).">
        <Textarea value={comment} maxLength={2000} onChange={(e) => setComment(e.target.value)} />
      </Field>
    </Modal>
  )
}
