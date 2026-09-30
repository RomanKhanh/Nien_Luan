import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { ProductImage } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/Toast'

// khớp ProductImageService ở backend
const MAX_IMAGES = 10
const MAX_FILE_SIZE = 5 * 1024 * 1024
const ACCEPT = 'image/jpeg,image/png,image/webp'

// ảnh sản phẩm: tải lên, xoá, chọn ảnh đại diện, đổi thứ tự; mỗi thao tác lưu ngay, không chờ "Lưu sản phẩm"
export function ProductImagesField({ productId, images }: { productId: number; images: ProductImage[] }) {
  const input = useRef<HTMLInputElement>(null)
  const toast = useToast()
  const queryClient = useQueryClient()
  const sorted = [...images].sort((a, b) => a.displayOrder - b.displayOrder)

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'product', productId] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] })
    queryClient.invalidateQueries({ queryKey: ['product', productId] })
    queryClient.invalidateQueries({ queryKey: ['products'] })
  }
  const options = { onSuccess: refresh, onError: (e: Error) => toast.error(e.message) }

  const upload = useMutation({
    // tải lần lượt để thứ tự ảnh đúng thứ tự chọn file; lỗi giữa chừng vẫn làm mới để thấy các ảnh đã lên
    mutationFn: async (files: File[]) => {
      for (const file of files) {
        await adminApi.uploadProductImage(productId, file)
      }
    },
    onSuccess: refresh,
    onError: (e: Error) => {
      refresh()
      toast.error(e.message)
    },
  })
  const remove = useMutation({ mutationFn: (id: number) => adminApi.deleteProductImage(productId, id), ...options })
  const setThumbnail = useMutation({
    mutationFn: (id: number) => adminApi.setProductThumbnail(productId, id),
    ...options,
  })
  const reorder = useMutation({
    mutationFn: (ids: number[]) => adminApi.reorderProductImages(productId, ids),
    ...options,
  })
  const busy = upload.isPending || remove.isPending || setThumbnail.isPending || reorder.isPending

  const pickFiles = (list: FileList | null) => {
    const files = Array.from(list ?? [])
    if (input.current) input.current.value = ''
    if (files.length === 0) return
    if (sorted.length + files.length > MAX_IMAGES) {
      toast.error(`Mỗi sản phẩm tối đa ${MAX_IMAGES} ảnh.`)
      return
    }
    if (files.some((f) => f.size > MAX_FILE_SIZE)) {
      toast.error('Mỗi ảnh tối đa 5MB.')
      return
    }
    upload.mutate(files)
  }

  const move = (index: number, delta: number) => {
    const ids = sorted.map((i) => i.id)
    const [id] = ids.splice(index, 1)
    ids.splice(index + delta, 0, id)
    reorder.mutate(ids)
  }

  return (
    <div>
      {sorted.length > 0 && (
        <ul className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {sorted.map((img, index) => (
            <li key={img.id} className="overflow-hidden rounded-md border border-line bg-surface">
              <div className="relative">
                <img src={img.url} alt={`Ảnh ${index + 1}`} className="aspect-[4/3] w-full object-cover" />
                {img.thumbnail && (
                  <span className="absolute left-2 top-2 rounded-full bg-primary px-2 py-0.5 text-[11px] font-bold text-white">
                    Ảnh đại diện
                  </span>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 px-2 py-1.5 text-[12.5px] font-semibold">
                <span className="flex gap-1">
                  <button
                    type="button"
                    aria-label="Chuyển ảnh lên trước"
                    className="px-1.5 text-ink-2 hover:text-primary disabled:text-ink-faint"
                    disabled={busy || index === 0}
                    onClick={() => move(index, -1)}
                  >
                    ←
                  </button>
                  <button
                    type="button"
                    aria-label="Chuyển ảnh ra sau"
                    className="px-1.5 text-ink-2 hover:text-primary disabled:text-ink-faint"
                    disabled={busy || index === sorted.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    →
                  </button>
                </span>
                {!img.thumbnail && (
                  <button
                    type="button"
                    className="text-primary hover:underline disabled:text-ink-faint"
                    disabled={busy}
                    onClick={() => setThumbnail.mutate(img.id)}
                  >
                    Đặt đại diện
                  </button>
                )}
                <button
                  type="button"
                  className="text-danger hover:underline disabled:text-ink-faint"
                  disabled={busy}
                  onClick={() => remove.mutate(img.id)}
                >
                  Xoá
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <input
        ref={input}
        type="file"
        accept={ACCEPT}
        multiple
        hidden
        aria-label="Chọn ảnh sản phẩm"
        onChange={(e) => pickFiles(e.target.files)}
      />
      <Button
        variant="secondary"
        size="sm"
        loading={upload.isPending}
        disabled={busy || sorted.length >= MAX_IMAGES}
        onClick={() => input.current?.click()}
      >
        Tải ảnh lên
      </Button>
      <p className="mt-2 text-[12px] text-ink-muted">
        JPG, PNG hoặc WebP, tối đa 5MB mỗi ảnh và {MAX_IMAGES} ảnh mỗi sản phẩm. Ảnh được lưu ngay khi tải lên.
      </p>
    </div>
  )
}
