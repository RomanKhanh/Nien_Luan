import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { ProductDetail, ProductRequest } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { useCategories, useSkills } from '@/features/catalog/queries'
import { skillTheme } from '@/lib/skills'

// khớp ProductRequest ở backend
const schema = z
  .object({
    name: z.string().trim().min(1, 'Vui lòng nhập tên sản phẩm').max(200, 'Tối đa 200 ký tự'),
    description: z.string().max(10000, 'Mô tả quá dài'),
    price: z.coerce
      .number<string>({ error: 'Giá không hợp lệ' })
      .min(0, 'Giá không được âm')
      .max(9_999_999_999, 'Giá quá lớn'),
    stockQuantity: z.coerce.number<string>().int('Phải là số nguyên').min(0, 'Không được âm').max(1_000_000),
    minAge: z.coerce.number<string>().int().min(0, '0–18').max(18, '0–18'),
    maxAge: z.coerce.number<string>().int().min(0, '0–18').max(18, '0–18'),
    categoryId: z.coerce.number<string>().min(1, 'Chọn danh mục'),
    imageUrls: z.string(),
    active: z.boolean(),
  })
  .refine((v) => v.minAge <= v.maxAge, { message: 'Tuổi tối thiểu phải ≤ tuổi tối đa', path: ['maxAge'] })
type FormInput = z.input<typeof schema>
type FormOutput = z.output<typeof schema>

export function ProductFormDrawer({
  open,
  onClose,
  productId,
}: {
  open: boolean
  onClose: () => void
  productId: number | null
}) {
  const editing = productId !== null
  const detail = useQuery({
    queryKey: ['admin', 'product', productId],
    queryFn: () => adminApi.product(productId!),
    enabled: open && editing,
  })
  if (!open) return null
  if (editing && !detail.data) {
    return (
      <Modal open onClose={onClose} variant="drawer" size="lg" title="Sửa sản phẩm">
        {detail.isError ? (
          <ErrorState message={detail.error.message} onRetry={() => detail.refetch()} />
        ) : (
          <PageLoader />
        )}
      </Modal>
    )
  }
  // key: mở sản phẩm khác thì dựng lại form với giá trị ban đầu mới
  return <ProductForm key={productId ?? 'new'} product={detail.data} onClose={onClose} />
}

function ProductForm({ product: p, onClose }: { product?: ProductDetail; onClose: () => void }) {
  const editing = Boolean(p)
  const productId = p?.id ?? null
  const categories = useCategories()
  const skills = useSkills()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [impacts, setImpacts] = useState<Record<number, number>>(() =>
    Object.fromEntries((p?.skillImpacts ?? []).map((i) => [i.skillId, i.impactIndex])),
  )
  const form = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: p?.name ?? '',
      description: p?.description ?? '',
      price: p ? String(p.price) : '',
      stockQuantity: p ? String(p.stockQuantity) : '0',
      minAge: p ? String(p.minAge) : '3',
      maxAge: p ? String(p.maxAge) : '12',
      categoryId: p ? String(p.categoryId) : '',
      imageUrls: p?.images.map((i) => i.url).join('\n') ?? '',
      active: p?.active ?? true,
    },
  })
  const errors = form.formState.errors

  const save = useMutation({
    mutationFn: (v: FormOutput) => {
      const body: ProductRequest = {
        name: v.name,
        description: v.description.trim() || null,
        price: v.price,
        stockQuantity: v.stockQuantity,
        minAge: v.minAge,
        maxAge: v.maxAge,
        categoryId: v.categoryId,
        imageUrls: v.imageUrls
          .split('\n')
          .map((u) => u.trim())
          .filter(Boolean),
        skillImpacts: Object.entries(impacts)
          .filter(([, value]) => value > 0)
          .map(([skillId, impactIndex]) => ({ skillId: Number(skillId), impactIndex })),
        active: v.active,
      }
      return editing ? adminApi.updateProduct(productId!, body) : adminApi.createProduct(body)
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(['admin', 'product', saved.id], saved)
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
      queryClient.invalidateQueries({ queryKey: ['product', saved.id] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast.success(editing ? 'Đã lưu sản phẩm' : 'Đã thêm sản phẩm')
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <Modal
      open
      onClose={onClose}
      variant="drawer"
      size="lg"
      title={editing ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button loading={save.isPending} onClick={form.handleSubmit((v) => save.mutate(v))}>
            Lưu sản phẩm
          </Button>
        </>
      }
    >
      <form noValidate className="space-y-5" onSubmit={form.handleSubmit((v) => save.mutate(v))}>
        <Field label="Tên sản phẩm" htmlFor="pname" required error={errors.name?.message}>
          <Input id="pname" invalid={Boolean(errors.name)} {...form.register('name')} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Danh mục" htmlFor="pcat" required error={errors.categoryId?.message}>
            <Select id="pcat" invalid={Boolean(errors.categoryId)} {...form.register('categoryId')}>
              <option value="">Chọn danh mục</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Giá (₫)" htmlFor="pprice" required error={errors.price?.message}>
            <Input
              id="pprice"
              type="number"
              min={0}
              step={1000}
              invalid={Boolean(errors.price)}
              {...form.register('price')}
            />
          </Field>
          <Field label="Tồn kho" htmlFor="pstock" required error={errors.stockQuantity?.message}>
            <Input
              id="pstock"
              type="number"
              min={0}
              invalid={Boolean(errors.stockQuantity)}
              {...form.register('stockQuantity')}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Tuổi từ" htmlFor="pmin" error={errors.minAge?.message}>
              <Input
                id="pmin"
                type="number"
                min={0}
                max={18}
                invalid={Boolean(errors.minAge)}
                {...form.register('minAge')}
              />
            </Field>
            <Field label="đến" htmlFor="pmax" error={errors.maxAge?.message}>
              <Input
                id="pmax"
                type="number"
                min={0}
                max={18}
                invalid={Boolean(errors.maxAge)}
                {...form.register('maxAge')}
              />
            </Field>
          </div>
        </div>

        <fieldset className="rounded-md border border-line p-4">
          <legend className="px-1 text-[13px] font-semibold text-ink-2">Chỉ số tác động kỹ năng (0 – 10)</legend>
          <div className="space-y-4">
            {skills.data?.map((s) => {
              const value = impacts[s.id] ?? 0
              const theme = skillTheme(s.code, s.name)
              return (
                <label key={s.id} className="block">
                  <span className="mb-1 flex justify-between text-[13.5px]">
                    <span className="font-semibold text-ink-2">{s.name}</span>
                    <b style={{ color: theme.color }}>{value}/10</b>
                  </span>
                  <input
                    type="range"
                    min={0}
                    max={10}
                    step={1}
                    value={value}
                    onChange={(e) => setImpacts((prev) => ({ ...prev, [s.id]: Number(e.target.value) }))}
                    className="w-full"
                    style={{ accentColor: theme.color }}
                  />
                </label>
              )
            })}
          </div>
          <p className="mt-3 text-[12px] text-ink-muted">
            Đổi chỉ số sẽ tự tính lại hồ sơ kỹ năng của các bé đang có sản phẩm này. Từ 5 trở lên được xếp vào nhóm kỹ
            năng khi lọc.
          </p>
        </fieldset>

        <Field label="Mô tả" htmlFor="pdesc" error={errors.description?.message}>
          <Textarea id="pdesc" className="!min-h-[120px]" {...form.register('description')} />
        </Field>
        <Field
          label="Ảnh sản phẩm"
          htmlFor="pimg"
          hint="Mỗi dòng một đường dẫn ảnh (URL). Ảnh đầu tiên là ảnh đại diện."
        >
          <Textarea
            id="pimg"
            className="!min-h-[80px] font-mono text-[13px]"
            placeholder="https://…"
            {...form.register('imageUrls')}
          />
        </Field>
        <label className="flex items-center gap-2.5 text-[14px] font-semibold">
          <input type="checkbox" className="h-4 w-4 accent-primary" {...form.register('active')} />
          Đang bán (bỏ chọn để ẩn khỏi cửa hàng)
        </label>
        <button type="submit" hidden />
      </form>
    </Modal>
  )
}
