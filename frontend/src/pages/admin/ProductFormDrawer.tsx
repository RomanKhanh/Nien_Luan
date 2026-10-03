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
import { youtubeId } from '@/lib/youtube'
import { ProductImagesField } from './ProductImagesField'

// ô số nguyên bắt buộc của thông số vận chuyển: trống thì báo nhập, còn lại phải là số nguyên trong (0, max]
const shippingInt = (label: string, emptyMessage: string, max: number, maxMessage: string) =>
  z
    .string()
    .trim()
    .min(1, emptyMessage)
    .pipe(
      z.coerce
        .number<string>({ error: `${label} không hợp lệ` })
        .int(`${label} phải là số nguyên`)
        .positive(`${label} phải lớn hơn 0`)
        .max(max, maxMessage),
    )

// khớp ProductRequest ở backend
const schema = z
  .object({
    name: z.string().trim().min(1, 'Vui lòng nhập tên sản phẩm').max(200, 'Tối đa 200 ký tự'),
    description: z.string().max(10000, 'Mô tả quá dài'),
    videoUrl: z
      .string()
      .trim()
      .max(500, 'Link quá dài')
      .refine((v) => v === '' || youtubeId(v) !== null, 'Link YouTube không hợp lệ'),
    price: z.coerce
      .number<string>({ error: 'Giá không hợp lệ' })
      .min(0, 'Giá không được âm')
      .max(9_999_999_999, 'Giá quá lớn'),
    stockQuantity: z.coerce.number<string>().int('Phải là số nguyên').min(0, 'Không được âm').max(1_000_000),
    minAge: z.coerce.number<string>().int().min(0, '0–18').max(18, '0–18'),
    maxAge: z.coerce.number<string>().int().min(0, '0–18').max(18, '0–18'),
    categoryId: z.coerce.number<string>().min(1, 'Chọn danh mục'),
    weightGrams: shippingInt('Cân nặng', 'Vui lòng nhập cân nặng sau đóng gói', 50_000, 'Cân nặng tối đa 50.000 g (50 kg)'),
    lengthCm: shippingInt('Chiều dài', 'Vui lòng nhập chiều dài gói hàng', 200, 'Chiều dài tối đa 200 cm'),
    widthCm: shippingInt('Chiều rộng', 'Vui lòng nhập chiều rộng gói hàng', 200, 'Chiều rộng tối đa 200 cm'),
    heightCm: shippingInt('Chiều cao', 'Vui lòng nhập chiều cao gói hàng', 200, 'Chiều cao tối đa 200 cm'),
    active: z.boolean(),
  })
  .refine((v) => v.minAge <= v.maxAge, { message: 'Tuổi tối thiểu phải ≤ tuổi tối đa', path: ['maxAge'] })
type FormInput = z.input<typeof schema>
type FormOutput = z.output<typeof schema>

const SHIPPING_FIELDS: {
  name: 'weightGrams' | 'lengthCm' | 'widthCm' | 'heightCm'
  label: string
  max: number
  placeholder: string
}[] = [
  { name: 'weightGrams', label: 'Cân nặng (g)', max: 50_000, placeholder: 'VD 650' },
  { name: 'lengthCm', label: 'Dài (cm)', max: 200, placeholder: 'VD 30' },
  { name: 'widthCm', label: 'Rộng (cm)', max: 200, placeholder: 'VD 22' },
  { name: 'heightCm', label: 'Cao (cm)', max: 200, placeholder: 'VD 8' },
]

export function ProductFormDrawer({
  open,
  onClose,
  productId,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  productId: number | null
  // sản phẩm vừa tạo xong: chuyển sang chế độ sửa để tải ảnh lên
  onCreated: (id: number) => void
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
  return <ProductForm key={productId ?? 'new'} product={detail.data} onClose={onClose} onCreated={onCreated} />
}

function ProductForm({
  product: p,
  onClose,
  onCreated,
}: {
  product?: ProductDetail
  onClose: () => void
  onCreated: (id: number) => void
}) {
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
      videoUrl: p?.videoUrl ?? '',
      price: p ? String(p.price) : '',
      stockQuantity: p ? String(p.stockQuantity) : '0',
      minAge: p ? String(p.minAge) : '3',
      maxAge: p ? String(p.maxAge) : '12',
      categoryId: p ? String(p.categoryId) : '',
      // sản phẩm mới để trống, buộc admin đo và nhập
      weightGrams: p ? String(p.weightGrams) : '',
      lengthCm: p ? String(p.lengthCm) : '',
      widthCm: p ? String(p.widthCm) : '',
      heightCm: p ? String(p.heightCm) : '',
      active: p?.active ?? true,
    },
  })
  const errors = form.formState.errors

  const save = useMutation({
    mutationFn: (v: FormOutput) => {
      const body: ProductRequest = {
        name: v.name,
        description: v.description.trim() || null,
        videoUrl: v.videoUrl || null,
        price: v.price,
        stockQuantity: v.stockQuantity,
        minAge: v.minAge,
        maxAge: v.maxAge,
        categoryId: v.categoryId,
        weightGrams: v.weightGrams,
        lengthCm: v.lengthCm,
        widthCm: v.widthCm,
        heightCm: v.heightCm,
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
      if (editing) {
        toast.success('Đã lưu sản phẩm')
        onClose()
      } else {
        // giữ ngăn kéo mở ở chế độ sửa: ảnh chỉ tải lên được khi sản phẩm đã có id
        toast.success('Đã thêm sản phẩm, giờ bạn có thể tải ảnh lên')
        onCreated(saved.id)
      }
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
          <legend className="px-1 text-[13px] font-semibold text-ink-2">Thông tin vận chuyển</legend>
          <p className="mb-3 text-[12.5px] text-ink-muted">
            Đo hộp sau khi đóng gói (tính cả thùng và xốp chèn). Số liệu này sẽ dùng để tính phí giao hàng.
          </p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {SHIPPING_FIELDS.map((f) => (
              <Field key={f.name} label={f.label} htmlFor={`p-${f.name}`} required error={errors[f.name]?.message}>
                <Input
                  id={`p-${f.name}`}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={f.max}
                  step={1}
                  placeholder={f.placeholder}
                  invalid={Boolean(errors[f.name])}
                  {...form.register(f.name)}
                />
              </Field>
            ))}
          </div>
        </fieldset>

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
          label="Video hướng dẫn sử dụng (YouTube)"
          htmlFor="pvideo"
          error={errors.videoUrl?.message}
          hint="Dán link YouTube, ví dụ https://www.youtube.com/watch?v=… hoặc https://youtu.be/…. Để trống nếu chưa có."
        >
          <Input
            id="pvideo"
            type="url"
            placeholder="https://www.youtube.com/watch?v=..."
            invalid={Boolean(errors.videoUrl)}
            {...form.register('videoUrl')}
          />
        </Field>
        <Field label="Ảnh sản phẩm">
          {p ? (
            <ProductImagesField productId={p.id} images={p.images} />
          ) : (
            <p className="rounded-md bg-muted px-4 py-3 text-[13.5px] text-ink-muted">
              Lưu sản phẩm trước, sau đó tải ảnh lên ngay tại đây.
            </p>
          )}
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
