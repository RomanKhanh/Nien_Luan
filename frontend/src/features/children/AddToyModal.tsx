import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi, childApi } from '@/api/endpoints'
import { SkillBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import { ageRange } from '@/lib/format'
import { topImpacts } from '@/lib/skills'
import { invalidateChildData } from './keys'

// "Tự thêm đồ chơi": bé đã có món này (mua ngoài) -> ChildProduct nguồn MANUAL
export function AddToyModal({
  open,
  onClose,
  childId,
  ownedProductIds,
}: {
  open: boolean
  onClose: () => void
  childId: number
  ownedProductIds: number[]
}) {
  const [keyword, setKeyword] = useState('')
  const toast = useToast()
  const queryClient = useQueryClient()
  const products = useQuery({
    queryKey: ['products', { keyword, size: 8, sort: 'name' }],
    queryFn: () => catalogApi.products({ keyword: keyword || undefined, size: 8, sort: 'name' }),
    enabled: open,
    placeholderData: keepPreviousData,
  })
  const assign = useMutation({
    mutationFn: (productId: number) => childApi.assignProduct(childId, productId),
    onSuccess: () => {
      invalidateChildData(queryClient, childId)
      toast.success('Đã thêm đồ chơi, hồ sơ kỹ năng đã được cập nhật')
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <Modal open={open} onClose={onClose} title="Thêm đồ chơi bé đã có" size="lg">
      <Input
        type="search"
        autoFocus
        placeholder="Tìm theo tên đồ chơi…"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <p className="mt-2 text-[12.5px] text-ink-muted">
        Chỉ đồ chơi có trong danh mục BrainBlocks mới có chỉ số kỹ năng để tính vào hồ sơ.
      </p>
      <ul className="mt-4 divide-y divide-line">
        {products.data?.content.map((p) => {
          const owned = ownedProductIds.includes(p.id)
          return (
            <li key={p.id} className="flex items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{p.name}</p>
                <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[12.5px] text-ink-muted">
                  {ageRange(p.minAge, p.maxAge)}
                  {topImpacts(p.skillImpacts).map((i) => (
                    <SkillBadge key={i.skillId} code={i.skillCode} name={i.skillName} value={i.impactIndex} />
                  ))}
                </div>
              </div>
              <Button
                size="sm"
                variant={owned ? 'secondary' : 'primary'}
                disabled={owned}
                loading={assign.isPending && assign.variables === p.id}
                onClick={() => assign.mutate(p.id)}
              >
                {owned ? 'Đã có' : 'Thêm'}
              </Button>
            </li>
          )
        })}
        {products.data?.content.length === 0 && (
          <li className="py-6 text-center text-ink-muted">Không tìm thấy đồ chơi.</li>
        )}
      </ul>
    </Modal>
  )
}
