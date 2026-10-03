import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { childApi, meApi, orderApi, shippingApi } from '@/api/endpoints'
import type { PaymentMethod, ShippingQuote } from '@/api/types'
import { PageHero } from '@/components/layout/PageHero'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { addressIssues, addressShape, addressToValue, codeOrNull, type AddressValue } from '@/features/address/address'
import { AddressFields } from '@/features/address/AddressFields'
import { MoneySummary } from '@/features/orders/MoneySummary'
import { cartKey, useCart } from '@/features/cart/useCart'
import { childKeys } from '@/features/children/keys'
import { UnboxingVideoReminder } from '@/features/orders/UnboxingVideoReminder'
import { MomoMark } from '@/features/payment/PaymentPanel'
import { ChildBundlePreview } from '@/features/skills/ChildBundlePreview'
import { usePayWithMomo } from '@/features/payment/usePayWithMomo'
import { formatPrice, formatWeightKg } from '@/lib/format'

// khớp CreateOrderRequest ở backend: địa chỉ 3 cấp bắt buộc (trừ phường/xã ở huyện không có cấp xã)
const schema = z
  .object({
    receiverName: z.string().trim().min(1, 'Vui lòng nhập tên người nhận').max(100, 'Tối đa 100 ký tự'),
    receiverPhone: z
      .string()
      .trim()
      .regex(/^\+?[0-9]{9,14}$/, 'Số điện thoại gồm 9–14 chữ số'),
    ...addressShape,
  })
  .superRefine((v, ctx) => addressIssues(v).forEach((issue) => ctx.addIssue({ code: 'custom', ...issue })))
type FormValues = z.infer<typeof schema>

const ADDRESS_KEYS = ['provinceCode', 'districtCode', 'wardCode', 'wardRequired', 'addressDetail'] as const

const PAYMENTS: { value: PaymentMethod; label: string; note: string }[] = [
  { value: 'COD', label: 'Thanh toán khi nhận hàng (COD)', note: 'Trả tiền mặt cho nhân viên giao hàng' },
  { value: 'MOMO', label: 'Ví MoMo', note: 'Chuyển sang trang MoMo để thanh toán ngay sau khi đặt hàng' },
]

export default function CheckoutPage() {
  const cart = useCart()
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list })
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  // productId -> childProfileId: món này mua cho bé nào (để tự vào hồ sơ kỹ năng khi đơn giao xong)
  const [assignments, setAssignments] = useState<Record<number, number>>({})
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD')
  const payWithMomo = usePayWithMomo()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })
  const errors = form.formState.errors

  useEffect(() => {
    if (me.data && !form.formState.isDirty) {
      // địa chỉ mặc định trong hồ sơ tự điền sẵn, khách vẫn đổi được
      form.reset({
        receiverName: me.data.fullName,
        receiverPhone: me.data.phone ?? '',
        ...addressToValue(me.data.defaultShippingAddress),
      })
    }
  }, [me.data, form])

  // thông báo khi server báo phí ship đã đổi (409): khách xem phí mới rồi bấm đặt hàng lần nữa để xác nhận
  const [feeNotice, setFeeNotice] = useState<string | null>(null)

  const placeOrder = useMutation({
    mutationFn: ({ values, expectedShippingFee }: { values: FormValues; expectedShippingFee?: number }) =>
      orderApi.create({
        expectedShippingFee,
        receiverName: values.receiverName,
        receiverPhone: values.receiverPhone,
        provinceCode: Number(values.provinceCode),
        districtCode: Number(values.districtCode),
        wardCode: codeOrNull(values.wardCode),
        addressDetail: values.addressDetail,
        paymentMethod,
        childAssignments: Object.entries(assignments).map(([productId, childProfileId]) => ({
          productId: Number(productId),
          childProfileId,
        })),
      }),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: cartKey })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      if (order.paymentMethod !== 'MOMO') {
        navigate(`/orders/${order.id}`, { replace: true, state: { placed: true } })
        return
      }
      // đơn đã tạo xong; nếu không lấy được link MoMo thì vẫn vào trang đơn, ở đó có nút thanh toán lại
      payWithMomo.mutate(order.id, {
        onError: (e) => {
          toast.error(e.message)
          navigate(`/orders/${order.id}`, { replace: true, state: { placed: true } })
        },
      })
    },
    onError: (e) => {
      if (e.status === 409) {
        // giỏ vừa đổi so với lúc báo giá: tải lại giỏ + phí ship, chờ khách xác nhận
        setFeeNotice(e.message)
        queryClient.invalidateQueries({ queryKey: cartKey })
        queryClient.invalidateQueries({ queryKey: ['shipping-quote'] })
        return
      }
      toast.error(e.message)
    },
  })

  const address = useWatch({ control: form.control, name: ADDRESS_KEYS })
  const addressValue: AddressValue = {
    provinceCode: address[0] ?? '',
    districtCode: address[1] ?? '',
    wardCode: address[2] ?? '',
    wardRequired: address[3] ?? true,
    addressDetail: address[4] ?? '',
  }
  const changeAddress = (patch: Partial<AddressValue>) => {
    for (const [key, v] of Object.entries(patch) as [keyof AddressValue, string | boolean][]) {
      form.setValue(key, v, { shouldDirty: true, shouldValidate: form.formState.isSubmitted })
    }
  }

  // phí vận chuyển của giỏ hiện tại tới tỉnh đã chọn; giỏ đổi (số lượng) thì báo giá lại
  const provinceCode = Number(addressValue.provinceCode) || 0
  const cartSignature = cart.data?.items.map((i) => `${i.productId}x${i.quantity}`).join(',') ?? ''
  const quote = useQuery({
    queryKey: ['shipping-quote', provinceCode, cartSignature],
    queryFn: () => shippingApi.quote(provinceCode),
    enabled: provinceCode > 0 && cartSignature !== '',
  })

  // bé được gán món -> các món đó, để xem trước hồ sơ kỹ năng của từng bé sau khi nhận đơn
  const assignedChildren = (children.data ?? [])
    .map((child) => ({
      child,
      productIds: Object.entries(assignments)
        .filter(([, childId]) => childId === child.id)
        .map(([productId]) => Number(productId)),
    }))
    .filter((entry) => entry.productIds.length > 0)

  if (cart.isPending || me.isPending) return <PageLoader />
  if (cart.isError) return <ErrorState message={cart.error.message} onRetry={() => cart.refetch()} />
  if (cart.data.items.length === 0 && !placeOrder.isSuccess) return <Navigate to="/cart" replace />

  return (
    <>
      <PageHero crumbs={[{ label: 'Giỏ hàng', to: '/cart' }, { label: 'Thanh toán' }]} title="Thanh toán">
        <CheckoutSteps />
      </PageHero>
      <div className="container-page py-8">
        <form
          noValidate
          onSubmit={form.handleSubmit((values) => {
            setFeeNotice(null)
            // gửi phí khách đang thấy; server tự tính lại, lệch thì trả 409 kèm phí mới
            placeOrder.mutate({ values, expectedShippingFee: provinceCode > 0 ? quote.data?.totalFee : undefined })
          })}
          className="grid gap-6 lg:grid-cols-[1fr_380px]"
        >
          <div className="space-y-6">
            <section className="card p-5 sm:p-6">
              <h2 className="h2 mb-5">Thông tin giao hàng</h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Người nhận" htmlFor="receiverName" required error={errors.receiverName?.message}>
                  <Input
                    id="receiverName"
                    autoComplete="name"
                    invalid={Boolean(errors.receiverName)}
                    {...form.register('receiverName')}
                  />
                </Field>
                <Field label="Số điện thoại" htmlFor="receiverPhone" required error={errors.receiverPhone?.message}>
                  <Input
                    id="receiverPhone"
                    type="tel"
                    autoComplete="tel"
                    invalid={Boolean(errors.receiverPhone)}
                    {...form.register('receiverPhone')}
                  />
                </Field>
              </div>
              <div className="mt-4">
                <AddressFields
                  idPrefix="checkout"
                  value={addressValue}
                  onChange={changeAddress}
                  errors={{
                    provinceCode: errors.provinceCode?.message,
                    districtCode: errors.districtCode?.message,
                    wardCode: errors.wardCode?.message,
                    addressDetail: errors.addressDetail?.message,
                  }}
                />
              </div>
            </section>

            <section className="card p-5 sm:p-6">
              <h2 className="h2">Mua cho bé nào?</h2>
              <p className="mb-4 mt-1 text-[13.5px] text-ink-muted">
                Không bắt buộc. Khi đơn được giao, đồ chơi sẽ tự vào hồ sơ kỹ năng của bé đã chọn.
              </p>
              {children.data?.length === 0 ? (
                <p className="text-[14px] text-ink-muted">
                  Bạn chưa có hồ sơ bé. <Link to="/children">Tạo hồ sơ bé</Link>
                </p>
              ) : (
                <ul className="divide-y divide-line">
                  {cart.data.items.map((item) => (
                    <li key={item.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center">
                      <span className="flex-1 text-[14px] font-semibold">
                        {item.productName} <span className="font-normal text-ink-muted">× {item.quantity}</span>
                      </span>
                      <Select
                        aria-label={`Bé nhận ${item.productName}`}
                        className="sm:!w-56"
                        value={assignments[item.productId] ?? ''}
                        onChange={(e) =>
                          setAssignments((prev) => {
                            const next = { ...prev }
                            if (e.target.value) next[item.productId] = Number(e.target.value)
                            else delete next[item.productId]
                            return next
                          })
                        }
                      >
                        <option value="">Không gắn bé</option>
                        {children.data?.map((c) => (
                          <option key={c.id} value={c.id}>
                            Bé {c.name} ({c.age} tuổi)
                          </option>
                        ))}
                      </Select>
                    </li>
                  ))}
                </ul>
              )}
              {assignedChildren.length > 0 && (
                <div className="mt-4 space-y-3">
                  {assignedChildren.map(({ child, productIds }) => (
                    <ChildBundlePreview key={child.id} child={child} productIds={productIds} />
                  ))}
                </div>
              )}
            </section>

            <section className="card p-5 sm:p-6">
              <h2 className="h2 mb-4">Phương thức thanh toán</h2>
              <div className="space-y-2.5" role="radiogroup">
                {PAYMENTS.map((p) => (
                  <label
                    key={p.value}
                    className={`flex cursor-pointer items-center gap-3 rounded-2xl border-2 px-4 py-3 transition ${
                      paymentMethod === p.value
                        ? 'border-primary bg-primary-soft'
                        : 'border-line hover:border-line-strong'
                    }`}
                  >
                    <input
                      type="radio"
                      name="payment"
                      checked={paymentMethod === p.value}
                      onChange={() => setPaymentMethod(p.value)}
                      className="accent-primary"
                    />
                    {p.value === 'MOMO' ? (
                      <MomoMark className="h-9 w-9 text-[11px]" />
                    ) : (
                      <span
                        className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-sky-soft text-[18px]"
                        aria-hidden
                      >
                        💵
                      </span>
                    )}
                    <span className="flex-1">
                      <span className="block text-[14px] font-semibold">{p.label}</span>
                      <span className="block text-[12.5px] text-ink-muted">{p.note}</span>
                    </span>
                  </label>
                ))}
              </div>
            </section>
          </div>

          <aside className="card h-fit p-5 lg:sticky lg:top-28">
            <p className="font-display text-[21px] font-extrabold">Đơn hàng ({cart.data.items.length} sản phẩm)</p>
            <ul className="mt-4 space-y-2.5 text-[14px]">
              {cart.data.items.map((item) => (
                <li key={item.id} className="flex justify-between gap-3">
                  <span className="text-ink-2">
                    {item.productName} × {item.quantity}
                  </span>
                  <span className="shrink-0">{formatPrice(item.subtotal)}</span>
                </li>
              ))}
            </ul>
            <CheckoutMoney
              className="mt-4 border-t border-line pt-4"
              subtotal={cart.data.totalAmount}
              provinceChosen={provinceCode > 0}
              quote={quote}
            />
            {feeNotice && (
              <p role="alert" className="mt-3 rounded-md bg-warning-soft px-3 py-2.5 text-[13px] text-[#92400E]">
                {feeNotice}
              </p>
            )}
            <div className="mt-5">
              <UnboxingVideoReminder compact />
            </div>
            <Button
              type="submit"
              size="lg"
              block
              className="mt-4"
              loading={placeOrder.isPending || payWithMomo.isPending}
            >
              {paymentMethod === 'MOMO' ? 'Đặt hàng & thanh toán MoMo' : 'Đặt hàng'}
            </Button>
            <p className="mt-3 text-center text-[12.5px] text-ink-muted">
              Bạn có thể huỷ đơn khi đơn chưa được giao đi.
            </p>
          </aside>
        </form>
      </div>
    </>
  )
}

// Tạm tính / Phí vận chuyển / Tổng thanh toán, theo báo giá của tỉnh đã chọn
function CheckoutMoney({
  subtotal,
  provinceChosen,
  quote,
  className,
}: {
  subtotal: number
  provinceChosen: boolean
  quote: { data?: ShippingQuote; isError: boolean }
  className?: string
}) {
  const fee = provinceChosen ? quote.data?.totalFee : undefined
  const shippingValue = !provinceChosen ? (
    <span className="text-ink-muted">Chọn địa chỉ để tính</span>
  ) : quote.isError ? (
    <span className="text-danger">Chưa tính được</span>
  ) : fee === undefined ? (
    <span className="text-ink-muted">Đang tính…</span>
  ) : (
    formatPrice(fee)
  )
  return (
    <div aria-live="polite" className={className}>
      <MoneySummary
        subtotal={subtotal}
        shippingValue={shippingValue}
        total={subtotal + (fee ?? 0)}
        totalNote={
          fee === undefined || !quote.data
            ? 'Chưa gồm phí vận chuyển'
            : `Khối lượng tính phí ${formatWeightKg(quote.data.chargeableWeightGrams)}`
        }
      />
    </div>
  )
}

// 3 bước mua hàng, bước hiện tại là "Thanh toán"
function CheckoutSteps() {
  const steps = ['Giỏ hàng', 'Thanh toán', 'Hoàn tất']
  return (
    <ol className="mt-6 flex max-w-md items-center gap-2" aria-label="Các bước đặt hàng">
      {steps.map((s, i) => (
        <li key={s} className="flex flex-1 items-center gap-2 last:flex-none">
          <span
            className={`grid h-9 w-9 shrink-0 place-items-center rounded-full border-[3px] font-display text-[16px] font-extrabold ${
              i === 0
                ? 'border-white bg-leaf text-white'
                : i === 1
                  ? 'border-white bg-sun text-ink'
                  : 'border-white/60 text-white/80'
            }`}
            aria-current={i === 1 ? 'step' : undefined}
          >
            {i === 0 ? '✓' : i + 1}
          </span>
          <span className="text-[13.5px] font-bold">{s}</span>
          {i < steps.length - 1 && (
            <span className="h-0 flex-1 border-t-[3px] border-dashed border-white/50" aria-hidden />
          )}
        </li>
      ))}
    </ol>
  )
}
