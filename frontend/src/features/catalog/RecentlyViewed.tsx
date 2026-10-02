import { useEffect, useState } from 'react'
import { useQueries } from '@tanstack/react-query'
import { catalogApi } from '@/api/endpoints'
import type { ProductDetail, ProductSummary } from '@/api/types'
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard'

// Sản phẩm vừa xem: chỉ lưu id trong localStorage của trình duyệt này (tiện ích riêng mỗi người xem,
// không cần đồng bộ). Đọc/ghi đều bọc try/catch vì localStorage có thể bị chặn (chế độ riêng tư...).
const KEY = 'brainblocks.recentlyViewed'
const MAX_STORED = 12
const CHANGE_EVENT = 'brainblocks:recently-viewed'

function readIds(): number[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((id): id is number => Number.isInteger(id)) : []
  } catch {
    return []
  }
}

function writeIds(ids: number[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(ids))
    window.dispatchEvent(new Event(CHANGE_EVENT))
  } catch {
    /* không lưu được thì thôi, tính năng phụ */
  }
}

// gọi ở trang chi tiết: đưa sản phẩm lên đầu danh sách
export function recordProductView(id: number) {
  writeIds([id, ...readIds().filter((x) => x !== id)].slice(0, MAX_STORED))
}

function useRecentIds() {
  const [ids, setIds] = useState(readIds)
  useEffect(() => {
    const sync = () => setIds(readIds())
    window.addEventListener(CHANGE_EVENT, sync)
    window.addEventListener('storage', sync) // tab khác xem thêm sản phẩm
    return () => {
      window.removeEventListener(CHANGE_EVENT, sync)
      window.removeEventListener('storage', sync)
    }
  }, [])
  return ids
}

// chi tiết sản phẩm -> dạng thẻ; dùng chung cache ['product', id] với trang chi tiết
function toSummary(d: ProductDetail): ProductSummary {
  return { ...d, thumbnailUrl: d.images[0]?.url ?? null }
}

export function RecentlyViewed({ excludeId, limit = 4 }: { excludeId?: number; limit?: number }) {
  const ids = useRecentIds()
    .filter((id) => id !== excludeId)
    .slice(0, limit)
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: ['product', id],
      queryFn: () => catalogApi.product(id),
      staleTime: 5 * 60_000,
      retry: false,
    })),
  })

  if (ids.length === 0) return null
  // sản phẩm đã ngừng bán (404) thì bỏ qua
  const products = results.flatMap((r) => (r.data ? [toSummary(r.data)] : []))
  const loading = results.some((r) => r.isPending)
  if (!loading && products.length === 0) return null

  return (
    <section className="mt-12">
      <h2 className="h2 mb-5">Bạn vừa xem</h2>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {loading
          ? ids.map((id) => <ProductCardSkeleton key={id} />)
          : products.map((p) => <ProductCard key={p.id} product={p} />)}
      </div>
    </section>
  )
}
