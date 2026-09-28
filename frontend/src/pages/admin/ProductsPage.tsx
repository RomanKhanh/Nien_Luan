import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { ProductSummary } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { Pill, SkillBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/Field'
import { ConfirmDialog, Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { useCategories } from '@/features/catalog/queries'
import { useDebounced } from '@/lib/useDebounced'
import { ageRange, formatPrice } from '@/lib/format'
import { topImpacts } from '@/lib/skills'
import { ProductFormDrawer } from './ProductFormDrawer'

export default function ProductsPage() {
  const [keyword, setKeyword] = useState('')
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [page, setPage] = useState(0)
  const debounced = useDebounced(keyword, 300)
  const categories = useCategories()
  const params = { keyword: debounced || undefined, categoryId, page, size: 15 }
  const products = useQuery({
    queryKey: ['admin', 'products', params],
    queryFn: () => adminApi.products(params),
    placeholderData: keepPreviousData,
  })
  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [stockTarget, setStockTarget] = useState<ProductSummary | null>(null)
  const [hideTarget, setHideTarget] = useState<ProductSummary | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()

  const hide = useMutation({
    mutationFn: (id: number) => adminApi.hideProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      toast.success('Đã ẩn sản phẩm khỏi cửa hàng')
      setHideTarget(null)
    },
    onError: (e) => toast.error(e.message),
  })

  const openForm = (id: number | null) => {
    setEditingId(id)
    setFormOpen(true)
  }

  return (
    <>
      <AdminHeader
        title="Sản phẩm & kho"
        description="Thêm, sửa, ẩn sản phẩm; cập nhật tồn kho, độ tuổi và chỉ số kỹ năng."
        actions={<Button onClick={() => openForm(null)}>+ Thêm sản phẩm</Button>}
      />
      <div className="card overflow-hidden">
        <div className="flex flex-wrap gap-3 border-b border-line p-4">
          <Input
            type="search"
            placeholder="Tìm theo tên sản phẩm…"
            className="!w-auto flex-1"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
          />
          <Select
            className="!w-auto"
            value={categoryId ?? ''}
            onChange={(e) => {
              setCategoryId(e.target.value ? Number(e.target.value) : undefined)
              setPage(0)
            }}
          >
            <option value="">Tất cả danh mục</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
        </div>
        {products.isPending ? (
          <PageLoader />
        ) : products.isError ? (
          <ErrorState message={products.error.message} onRetry={() => products.refetch()} />
        ) : products.data.content.length === 0 ? (
          <EmptyState title="Không có sản phẩm nào" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-[14px]">
              <thead className="bg-muted/60 text-[12.5px] uppercase tracking-[0.04em] text-ink-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Sản phẩm</th>
                  <th className="px-4 py-3 font-semibold">Giá</th>
                  <th className="px-4 py-3 font-semibold">Tồn kho</th>
                  <th className="px-4 py-3 font-semibold">Kỹ năng</th>
                  <th className="px-4 py-3 font-semibold">Trạng thái</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className={`divide-y divide-line ${products.isPlaceholderData ? 'opacity-60' : ''}`}>
                {products.data.content.map((p) => (
                  <tr key={p.id} className="align-middle">
                    <td className="px-4 py-3">
                      <p className="font-semibold">{p.name}</p>
                      <p className="text-[12.5px] text-ink-muted">
                        {p.categoryName} · {ageRange(p.minAge, p.maxAge)}
                      </p>
                    </td>
                    <td className="px-4 py-3 tabular-nums">{formatPrice(p.price)}</td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => setStockTarget(p)}
                        className={`rounded-md px-2 py-1 font-semibold tabular-nums hover:bg-muted ${
                          p.stockQuantity === 0 ? 'text-danger' : p.stockQuantity <= 5 ? 'text-[#B45309]' : ''
                        }`}
                        title="Cập nhật tồn kho"
                      >
                        {p.stockQuantity} ✎
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {topImpacts(p.skillImpacts, 4).map((i) => (
                          <SkillBadge key={i.skillId} code={i.skillCode} name={i.skillName} value={i.impactIndex} />
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      {p.active ? <Pill tone="green">Đang bán</Pill> : <Pill tone="gray">Đã ẩn</Pill>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1.5">
                        <Button size="sm" variant="secondary" onClick={() => openForm(p.id)}>
                          Sửa
                        </Button>
                        {p.active && (
                          <Button size="sm" variant="danger-outline" onClick={() => setHideTarget(p)}>
                            Ẩn
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {products.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={products.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>

      <ProductFormDrawer open={formOpen} onClose={() => setFormOpen(false)} productId={editingId} />
      <StockModal product={stockTarget} onClose={() => setStockTarget(null)} />
      <ConfirmDialog
        open={Boolean(hideTarget)}
        title="Ẩn sản phẩm khỏi cửa hàng?"
        message={
          <>
            <b>{hideTarget?.name}</b> sẽ không còn hiển thị với khách. Đơn hàng, đánh giá và hồ sơ bé liên quan vẫn được
            giữ; có thể bán lại bằng cách sửa sản phẩm và chọn “Đang bán”.
          </>
        }
        confirmLabel="Ẩn sản phẩm"
        loading={hide.isPending}
        onConfirm={() => hideTarget && hide.mutate(hideTarget.id)}
        onClose={() => setHideTarget(null)}
      />
    </>
  )
}

function StockModal({ product, onClose }: { product: ProductSummary | null; onClose: () => void }) {
  const [value, setValue] = useState('')
  const toast = useToast()
  const queryClient = useQueryClient()
  const [lastId, setLastId] = useState<number | null>(null)
  if (product && product.id !== lastId) {
    setLastId(product.id)
    setValue(String(product.stockQuantity))
  }
  const save = useMutation({
    mutationFn: () => adminApi.updateStock(product!.id, Number(value)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      toast.success('Đã cập nhật tồn kho')
      setLastId(null)
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })
  const invalid = value === '' || !Number.isInteger(Number(value)) || Number(value) < 0
  return (
    <Modal
      open={Boolean(product)}
      onClose={() => {
        setLastId(null)
        onClose()
      }}
      title="Cập nhật tồn kho"
      size="sm"
      footer={
        <Button disabled={invalid} loading={save.isPending} onClick={() => save.mutate()}>
          Lưu
        </Button>
      }
    >
      <p className="mb-4 font-semibold">{product?.name}</p>
      <Field label="Số lượng trong kho" htmlFor="stock" error={invalid ? 'Nhập số nguyên ≥ 0' : undefined}>
        <Input
          id="stock"
          type="number"
          min={0}
          value={value}
          invalid={invalid}
          onChange={(e) => setValue(e.target.value)}
        />
      </Field>
    </Modal>
  )
}
