import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { cartApi } from '@/api/endpoints'
import type { CartItem } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { PageHero } from '@/components/layout/PageHero'
import { ProductArt } from '@/components/product/ProductArt'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Modal'
import { QuantityStepper } from '@/components/ui/QuantityStepper'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { cartKey, useCart } from '@/features/cart/useCart'
import { formatPrice } from '@/lib/format'

export default function CartPage() {
  const { session, isCustomer } = useAuth()
  const cart = useCart()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [confirmClear, setConfirmClear] = useState(false)

  const update = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) => cartApi.update(itemId, quantity),
    onSuccess: (data) => queryClient.setQueryData(cartKey, data),
    onError: (e) => toast.error(e.message),
  })
  const remove = useMutation({
    mutationFn: cartApi.remove,
    onSuccess: (data) => queryClient.setQueryData(cartKey, data),
    onError: (e) => toast.error(e.message),
  })
  const clear = useMutation({
    mutationFn: cartApi.clear,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cartKey })
      setConfirmClear(false)
    },
    onError: (e) => toast.error(e.message),
  })

  if (!session) {
    return (
      <div className="container-page">
        <EmptyState
          title="Đăng nhập để xem giỏ hàng"
          description="Giỏ hàng được lưu theo tài khoản, đăng nhập trên thiết bị nào cũng thấy."
          action={
            <ButtonLink to="/login" state={{ from: '/cart' }}>
              Đăng nhập
            </ButtonLink>
          }
        />
      </div>
    )
  }
  if (!isCustomer) {
    return (
      <div className="container-page">
        <EmptyState
          title="Tài khoản quản trị không dùng giỏ hàng"
          action={<ButtonLink to="/admin">Về trang quản trị</ButtonLink>}
        />
      </div>
    )
  }
  if (cart.isPending) return <PageLoader />
  if (cart.isError) return <ErrorState message={cart.error.message} onRetry={() => cart.refetch()} />

  const items = cart.data.items
  // có món ngừng bán / vượt tồn kho thì backend sẽ từ chối đặt hàng: chặn sớm ở đây
  const blocking = items.filter((i) => !i.active || i.quantity > i.stockQuantity)
  const count = items.reduce((s, i) => s + i.quantity, 0)

  return (
    <>
      <PageHero
        crumbs={[{ label: 'Giỏ hàng' }]}
        kicker={`${count} sản phẩm`}
        title="Giỏ hàng của bạn"
        subtitle="Kiểm tra số lượng rồi tiến hành thanh toán — có thể gán từng món cho hồ sơ bé ở bước tiếp theo."
      />
      <div className="container-page py-8">
        {items.length === 0 ? (
          <div className="card">
            <EmptyState
              icon="🛒"
              title="Giỏ hàng đang trống"
              description="Xem gợi ý đồ chơi theo lộ trình kỹ năng của bé hoặc khám phá toàn bộ sản phẩm."
              action={
                <div className="flex flex-wrap justify-center gap-2.5">
                  <ButtonLink to="/children">Xem gợi ý cho bé</ButtonLink>
                  <ButtonLink to="/products" variant="secondary">
                    Khám phá sản phẩm
                  </ButtonLink>
                </div>
              }
            />
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
            <div className="card divide-y divide-line">
              {items.map((item) => (
                <CartRow
                  key={item.id}
                  item={item}
                  busy={
                    (update.isPending && update.variables?.itemId === item.id) ||
                    (remove.isPending && remove.variables === item.id)
                  }
                  onQuantity={(quantity) => update.mutate({ itemId: item.id, quantity })}
                  onRemove={() => remove.mutate(item.id)}
                />
              ))}
              <div className="flex justify-end px-5 py-3">
                <button
                  type="button"
                  className="text-[13px] font-semibold text-ink-muted hover:text-danger"
                  onClick={() => setConfirmClear(true)}
                >
                  Xoá toàn bộ giỏ hàng
                </button>
              </div>
            </div>
            <aside className="card h-fit overflow-hidden p-0 lg:sticky lg:top-28">
              <div className="flex h-1.5" aria-hidden>
                <span className="flex-1 bg-skill-logic" />
                <span className="flex-1 bg-skill-creative" />
                <span className="flex-1 bg-skill-solve" />
                <span className="flex-1 bg-skill-stem" />
              </div>
              <div className="p-5">
                <p className="font-display text-[21px] font-extrabold">Tóm tắt đơn hàng</p>
                <dl className="mt-4 space-y-2 text-[14px]">
                  <div className="flex justify-between">
                    <dt className="text-ink-muted">Tạm tính ({count} sản phẩm)</dt>
                    <dd>{formatPrice(cart.data.totalAmount)}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-ink-muted">Phí vận chuyển</dt>
                    <dd>Tính khi giao hàng</dd>
                  </div>
                  <div className="flex items-baseline justify-between border-t-2 border-dashed border-line pt-3 text-[16px] font-bold">
                    <dt>Tổng cộng</dt>
                    <dd className="font-display text-[26px] font-extrabold leading-none text-coral">
                      {formatPrice(cart.data.totalAmount)}
                    </dd>
                  </div>
                </dl>
                {blocking.length > 0 && (
                  <p role="alert" className="mt-4 rounded-md bg-danger-soft px-3 py-2.5 text-[13px] text-danger">
                    Có {blocking.length} sản phẩm ngừng bán hoặc vượt số lượng tồn kho. Vui lòng điều chỉnh trước khi
                    thanh toán.
                  </p>
                )}
                {blocking.length > 0 ? (
                  <Button size="lg" block className="mt-5" disabled>
                    Tiến hành thanh toán
                  </Button>
                ) : (
                  <ButtonLink to="/checkout" size="lg" block className="mt-5">
                    Tiến hành thanh toán
                  </ButtonLink>
                )}
                <Link to="/products" className="mt-3 block text-center text-[14px] font-semibold">
                  Tiếp tục mua sắm
                </Link>
              </div>
            </aside>
          </div>
        )}
        <ConfirmDialog
          open={confirmClear}
          title="Xoá toàn bộ giỏ hàng?"
          message="Tất cả sản phẩm trong giỏ sẽ bị xoá."
          confirmLabel="Xoá giỏ hàng"
          loading={clear.isPending}
          onConfirm={() => clear.mutate()}
          onClose={() => setConfirmClear(false)}
        />
      </div>
    </>
  )
}

function CartRow({
  item,
  busy,
  onQuantity,
  onRemove,
}: {
  item: CartItem
  busy: boolean
  onQuantity: (q: number) => void
  onRemove: () => void
}) {
  const overStock = item.active && item.quantity > item.stockQuantity
  return (
    <div className={`flex gap-4 p-4 sm:p-5 ${busy ? 'opacity-60' : ''}`}>
      <Link
        to={`/products/${item.productId}`}
        className="w-20 shrink-0 overflow-hidden rounded-2xl border-2 border-line transition hover:-rotate-3 hover:border-sky sm:w-24"
      >
        <ProductArt url={item.thumbnailUrl} name={item.productName} />
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex justify-between gap-3">
          <Link to={`/products/${item.productId}`} className="font-semibold text-ink hover:text-primary">
            {item.productName}
          </Link>
          <p className="shrink-0 font-display text-[18px] font-extrabold text-coral">{formatPrice(item.subtotal)}</p>
        </div>
        <p className="text-[13px] text-ink-muted">{formatPrice(item.price)} / sản phẩm</p>
        {!item.active && <p className="mt-1 text-[13px] font-semibold text-danger">Sản phẩm đã ngừng bán</p>}
        {overStock && (
          <p className="mt-1 text-[13px] font-semibold text-danger">
            {item.stockQuantity === 0 ? 'Đã hết hàng' : `Chỉ còn ${item.stockQuantity} sản phẩm`}
          </p>
        )}
        {item.active && !overStock && item.stockQuantity <= 5 && (
          <p className="mt-1 text-[13px] font-semibold text-[#B45309]">Sắp hết hàng: còn {item.stockQuantity}</p>
        )}
        <div className="mt-3 flex items-center justify-between">
          <QuantityStepper
            size="sm"
            value={item.quantity}
            max={Math.max(1, Math.min(99, item.stockQuantity))}
            disabled={busy || !item.active}
            onChange={onQuantity}
          />
          <button
            type="button"
            className="text-[13px] font-semibold text-ink-muted hover:text-danger"
            onClick={onRemove}
          >
            Xoá
          </button>
        </div>
      </div>
    </div>
  )
}
