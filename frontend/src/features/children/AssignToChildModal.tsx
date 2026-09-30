import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { childApi } from '@/api/endpoints'
import type { ApiError } from '@/api/client'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { initials } from '@/lib/format'
import { childKeys, invalidateChildData } from './keys'

// "Gán vào hồ sơ bé": đánh dấu bé đã có đồ chơi này (mua ngoài) để tính vào hồ sơ kỹ năng
export function AssignToChildModal({
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
  const toast = useToast()
  const queryClient = useQueryClient()
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list, enabled: open })
  const assign = useMutation({
    mutationFn: (childId: number) => childApi.assignProduct(childId, productId),
    onSuccess: (_, childId) => {
      const child = children.data?.find((c) => c.id === childId)
      toast.success(`Đã thêm vào hồ sơ của bé ${child?.name ?? ''}`)
      invalidateChildData(queryClient, childId)
      onClose()
    },
    onError: (e: ApiError) => toast.error(e.message),
  })

  return (
    <Modal open={open} onClose={onClose} title="Gán vào hồ sơ bé" size="sm">
      <p className="mb-4 text-[14px] text-ink-muted">
        Chọn bé đã có <b className="text-ink">{productName}</b>. Chỉ số kỹ năng của đồ chơi sẽ được tính vào lộ trình
        của bé.
      </p>
      {children.isPending ? (
        <PageLoader />
      ) : children.data?.length === 0 ? (
        <EmptyState
          title="Chưa có hồ sơ bé nào"
          description="Tạo hồ sơ trước để theo dõi lộ trình kỹ năng."
          action={<ButtonLink to="/children">Tạo hồ sơ bé</ButtonLink>}
        />
      ) : (
        <ul className="space-y-2">
          {children.data?.map((c) => (
            <li key={c.id}>
              <Button
                variant="secondary"
                block
                className="!justify-start"
                loading={assign.isPending && assign.variables === c.id}
                onClick={() => assign.mutate(c.id)}
              >
                <span className="grid h-7 w-7 place-items-center rounded-full bg-primary-soft text-[11px] font-bold text-primary">
                  {initials(c.name)}
                </span>
                Bé {c.name} · {c.age} tuổi
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
