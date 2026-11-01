import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { childApi } from '@/api/endpoints'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/Toast'
import { useAddToCart } from '@/features/cart/useCart'
import { childKeys } from '@/features/children/keys'
import { formatPrice } from '@/lib/format'

export function ChildWishlist({ childId }: { childId: number }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const addToCart = useAddToCart()

  const wishlist = useQuery({
    queryKey: childKeys.wishlist(childId),
    queryFn: () => childApi.wishlist(childId),
  })

  const remove = useMutation({
    mutationFn: (productId: number) =>
      childApi.removeWishlist(childId, productId),

    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: childKeys.wishlist(childId),
      })
      toast.success('Đã xóa khỏi wishlist')
    },

    onError: (error) => toast.error(error.message),
  })

  return (
    <section className="card p-5 sm:p-6">
      <h2 className="h2">♡ Món muốn mua</h2>

      <p className="mt-1 text-sm text-ink-muted">
        Những món lưu cho bé, chưa được tính vào hồ sơ kỹ năng.
      </p>

      {wishlist.isPending ? (
        <p className="mt-4">Đang tải wishlist…</p>
      ) : wishlist.isError ? (
        <div className="mt-4">
          <p role="alert" className="text-danger">
            {wishlist.error.message}
          </p>
          <Button variant="ghost" onClick={() => wishlist.refetch()}>
            Thử lại
          </Button>
        </div>
      ) : wishlist.data.length === 0 ? (
        <p className="mt-4 text-sm text-ink-muted">
          Chưa có món nào.{' '}
          <Link to="/products">Khám phá sản phẩm →</Link>
        </p>
      ) : (
        <ul className="mt-4 divide-y divide-line">
          {wishlist.data.map((item) => (
            <li
              key={item.id}
              className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center"
            >
              <div className="min-w-0 flex-1">
                {item.active ? (
                  <Link
                    to={`/products/${item.productId}`}
                    className="font-semibold text-ink hover:text-primary"
                  >
                    {item.productName}
                  </Link>
                ) : (
                  <p className="font-semibold">{item.productName}</p>
                )}

                <p className="mt-1 font-bold text-coral">
                  {formatPrice(item.price)}
                </p>

                <p className="mt-1 text-sm text-ink-muted">
                  {item.minAge}–{item.maxAge} tuổi
                  {' · '}
                  {!item.active
                    ? 'Ngừng bán'
                    : item.inStock
                      ? 'Còn hàng'
                      : 'Tạm hết hàng'}
                </p>

                <div className="mt-1 text-sm">
                  {!item.ageAppropriate && (
                    <p className="text-ink-muted">
                      Ngoài độ tuổi khuyến nghị cho bé.
                    </p>
                  )}

                  {item.alreadyOwned && (
                    <p className="text-success">
                      Bé đã có sản phẩm này.
                    </p>
                  )}
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  disabled={!item.active || !item.inStock || addToCart.isPending}
                  loading={
                    addToCart.isPending
                    && addToCart.variables?.productId === item.productId
                  }
                  onClick={() =>
                    addToCart.mutate({
                      productId: item.productId,
                      quantity: 1,
                    })
                  }
                >
                  Thêm vào giỏ
                </Button>

                <Button
                  size="sm"
                  variant="danger-outline"
                  disabled={remove.isPending}
                  loading={
                    remove.isPending
                    && remove.variables === item.productId
                  }
                  onClick={() => remove.mutate(item.productId)}
                >
                  Xóa
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}