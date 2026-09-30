import { useEffect, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { paymentApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Mascot } from '@/components/decor/Decor'
import { PageHero } from '@/components/layout/PageHero'
import { Button, ButtonLink, Spinner } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { paymentKey, usePayWithMomo } from '@/features/payment/usePayWithMomo'

// hỏi lại trạng thái mỗi 2 giây, quá thời gian này thì thôi chờ IPN
const WAIT_MS = 20_000
// mã MoMo trả về khi khách tự bấm huỷ trên trang thanh toán
const USER_CANCELLED = '1006'

// MoMo chuyển khách về đây sau khi thanh toán, kèm các tham số có chữ ký trên URL.
// Tham số trên URL chỉ dùng để hiển thị; "đã thanh toán" thật là khi backend nhận IPN từ MoMo,
// nên trang này hỏi lại GET /api/payments/{orderId} thay vì tin URL.
export default function MomoReturnPage() {
  const [search] = useSearchParams()
  const { isCustomer } = useAuth()
  const queryClient = useQueryClient()
  const toast = useToast()
  const pay = usePayWithMomo()

  const params = Object.fromEntries(search)
  // backend đặt mã giao dịch gửi MoMo là "<orderId>-<timestamp>"
  const orderId = Number(params.orderId?.split('-')[0])
  const hasParams = Boolean(params.signature) && Number.isInteger(orderId) && orderId > 0

  const verify = useQuery({
    queryKey: ['momo-return', search.toString()],
    queryFn: () => paymentApi.verifyMomoReturn(params),
    enabled: hasParams,
    staleTime: Infinity,
    retry: 1,
  })
  const momoSaysPaid = verify.data === true

  const [gaveUp, setGaveUp] = useState(false)
  useEffect(() => {
    if (!momoSaysPaid) return
    const timer = window.setTimeout(() => setGaveUp(true), WAIT_MS)
    return () => window.clearTimeout(timer)
  }, [momoSaysPaid])

  const payment = useQuery({
    queryKey: paymentKey(orderId),
    queryFn: () => paymentApi.status(orderId),
    enabled: hasParams && isCustomer && momoSaysPaid,
    refetchInterval: (q) => (q.state.data?.status !== 'SUCCESS' && !gaveUp ? 2000 : false),
  })
  const confirmed = payment.data?.status === 'SUCCESS'
  const stillWaiting = momoSaysPaid && !confirmed && isCustomer && !gaveUp && !payment.isError

  // đã xác nhận: trang đơn hàng cần tải lại (đơn chuyển sang "Đã xác nhận")
  useEffect(() => {
    if (!confirmed) return
    queryClient.invalidateQueries({ queryKey: ['order', orderId] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
  }, [confirmed, orderId, queryClient])

  const orderLink = (
    <ButtonLink to={`/orders/${orderId}`} variant="secondary">
      Xem đơn hàng
    </ButtonLink>
  )

  let body
  if (!hasParams) {
    body = (
      <Result
        tone="neutral"
        title="Không có thông tin thanh toán"
        text="Trang này chỉ dùng khi MoMo chuyển bạn về sau khi thanh toán."
        actions={<ButtonLink to="/orders">Đơn hàng của tôi</ButtonLink>}
      />
    )
  } else if (verify.isPending) {
    body = <Result tone="neutral" title="Đang kiểm tra kết quả thanh toán…" spinner />
  } else if (verify.isError) {
    body = <ErrorState message={verify.error.message} onRetry={() => verify.refetch()} />
  } else if (!momoSaysPaid) {
    const cancelled = params.resultCode === USER_CANCELLED
    body = (
      <Result
        tone="bad"
        title={cancelled ? 'Bạn đã huỷ thanh toán' : 'Thanh toán không thành công'}
        text="Đơn hàng vẫn được giữ và chưa bị trừ tiền. Bạn có thể thanh toán lại bất cứ lúc nào khi đơn chưa huỷ."
        actions={
          <>
            {isCustomer && (
              <Button
                loading={pay.isPending}
                onClick={() => pay.mutate(orderId, { onError: (e) => toast.error(e.message) })}
              >
                Thanh toán lại bằng MoMo
              </Button>
            )}
            {orderLink}
          </>
        }
      />
    )
  } else if (confirmed) {
    body = (
      <Result
        tone="good"
        title="Thanh toán thành công!"
        text="BrainBlocks đã nhận được thanh toán và xác nhận đơn hàng của bạn."
        actions={
          <>
            <ButtonLink to={`/orders/${orderId}`}>Xem đơn hàng</ButtonLink>
            <ButtonLink to="/products" variant="secondary">
              Tiếp tục mua sắm
            </ButtonLink>
          </>
        }
      />
    )
  } else if (stillWaiting) {
    body = (
      <Result
        tone="neutral"
        title="MoMo đã ghi nhận thanh toán"
        text="Đang chờ hệ thống xác nhận giao dịch, thường mất vài giây…"
        spinner
        actions={orderLink}
      />
    )
  } else {
    body = (
      <Result
        tone="neutral"
        title="MoMo đã ghi nhận thanh toán"
        text={
          isCustomer
            ? 'Hệ thống chưa nhận được xác nhận từ MoMo. Trạng thái sẽ tự cập nhật trong trang đơn hàng; nếu sau ít phút vẫn chưa đổi, hãy liên hệ hỗ trợ.'
            : 'Đăng nhập để xem trạng thái đơn hàng.'
        }
        actions={isCustomer ? orderLink : <ButtonLink to="/login">Đăng nhập</ButtonLink>}
      />
    )
  }

  return (
    <>
      <PageHero crumbs={[{ label: 'Kết quả thanh toán' }]} kicker="Ví MoMo" title="Kết quả thanh toán" />
      <div className="container-page py-8">
        <div className="card mx-auto max-w-xl p-6 sm:p-8">{body}</div>
      </div>
    </>
  )
}

const TONES = {
  good: { ring: 'border-[#BFE6CD] bg-success-soft', title: 'text-success', icon: '🎉' },
  bad: { ring: 'border-danger/30 bg-danger-soft', title: 'text-danger', icon: '😕' },
  neutral: { ring: 'border-sky/40 bg-sky-soft', title: 'text-ink', icon: null },
}

function Result({
  tone,
  title,
  text,
  actions,
  spinner,
}: {
  tone: keyof typeof TONES
  title: string
  text?: string
  actions?: ReactNode
  spinner?: boolean
}) {
  const t = TONES[tone]
  return (
    <div className="flex flex-col items-center text-center" role="status">
      <div className={`grid h-24 w-24 place-items-center rounded-full border-4 ${t.ring}`}>
        {t.icon ? (
          <span className="text-[44px]" aria-hidden>
            {t.icon}
          </span>
        ) : (
          <Mascot className="w-14 animate-float" />
        )}
      </div>
      <h2 className={`mt-4 font-display text-[26px] font-extrabold leading-tight ${t.title}`}>{title}</h2>
      {text && <p className="mt-2 max-w-md text-[14.5px] text-ink-2">{text}</p>}
      {spinner && <Spinner className="mt-4 h-6 w-6 text-primary" />}
      {actions && <div className="mt-6 flex flex-wrap justify-center gap-2.5">{actions}</div>}
    </div>
  )
}
