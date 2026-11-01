import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { childApi } from '@/api/endpoints'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/Toast'
import { childKeys } from '@/features/children/keys'

export function SaveToWishlist({ productId }: { productId: number }) {
  const [pickedId, setPickedId] = useState<number | null>(null)
  const queryClient = useQueryClient()
  const toast = useToast()

  const children = useQuery({
    queryKey: childKeys.list,
    queryFn: childApi.list,
  })

  // Chưa chọn thì mặc định bé đầu tiên.
  // Nếu bé đã chọn bị xóa, quay lại bé đầu tiên còn tồn tại.
  const childId =
    children.data?.find((child) => child.id === pickedId)?.id
    ?? children.data?.[0]?.id

  const wishlist = useQuery({
    queryKey: childKeys.wishlist(childId ?? 0),
    queryFn: () => childApi.wishlist(childId!),
    enabled: childId !== undefined,
  })

  const saved = wishlist.data?.some(
    (item) => item.productId === productId,
  ) ?? false

  const save = useMutation({
    mutationFn: (id: number) => childApi.addWishlist(id, productId),

    onSuccess: async (_, id) => {
      await queryClient.invalidateQueries({
        queryKey: childKeys.wishlist(id),
      })
      toast.success('Đã lưu sản phẩm vào wishlist của bé')
    },

    onError: (error) => toast.error(error.message),
  })

  if (children.isPending) {
    return <p className="mt-3 text-sm">Đang tải hồ sơ bé…</p>
  }

  if (children.isError) {
    return (
      <div className="mt-3">
        <p role="alert">{children.error.message}</p>
        <Button variant="ghost" onClick={() => children.refetch()}>
          Thử lại
        </Button>
      </div>
    )
  }

  if (children.data.length === 0) {
    return (
      <p className="mt-3 text-sm text-ink-muted">
        <Link to="/children">Tạo hồ sơ bé</Link>
        {' '}để lưu món muốn mua.
      </p>
    )
  }

  return (
    <div className="card mt-4 p-4">
      <p className="font-bold">♡ Lưu món muốn mua cho bé</p>

      <div className="mt-3 flex flex-wrap gap-3">
        <select
          aria-label="Chọn bé để lưu sản phẩm"
          className="min-w-0 flex-1 rounded-xl border border-line bg-surface px-3 py-2"
          value={childId ?? ''}
          disabled={save.isPending}
          onChange={(event) => setPickedId(Number(event.target.value))}
        >
          {children.data.map((child) => (
            <option key={child.id} value={child.id}>
              Bé {child.name} · {child.age} tuổi
            </option>
          ))}
        </select>

        <Button
          variant="secondary"
          loading={save.isPending}
          disabled={
            childId === undefined
            || wishlist.isPending
            || wishlist.isError
            || saved
          }
          onClick={() => {
            if (childId !== undefined) save.mutate(childId)
          }}
        >
          {saved ? '♥ Đã lưu cho bé' : '♡ Lưu vào wishlist'}
        </Button>
      </div>

      {wishlist.isError && (
        <div className="mt-2">
          <p role="alert" className="text-sm text-danger">
            {wishlist.error.message}
          </p>
          <Button variant="ghost" onClick={() => wishlist.refetch()}>
            Thử lại
          </Button>
        </div>
      )}

      <Link
        to={`/children/${childId}`}
        className="mt-3 inline-block text-sm font-semibold"
      >
        Xem hồ sơ và wishlist của bé →
      </Link>
    </div>
  )
}