import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { catalogApi } from '@/api/endpoints'
import type { ProductDetail } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { ProductArt } from '@/components/product/ProductArt'
import { ProductCard } from '@/components/product/ProductCard'
import { SkillBar, Stars } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { QuantityStepper } from '@/components/ui/QuantityStepper'
import { useAddToCart } from '@/features/cart/useCart'
import { AssignToChildModal } from '@/features/children/AssignToChildModal'
import { WriteReviewModal } from '@/features/reviews/WriteReviewModal'
import { ageRange, formatDecimal, formatPrice, formatRelative } from '@/lib/format'
import { dominantSkill } from '@/lib/skills'

export default function ProductDetailPage() {
  const id = Number(useParams().id)
  const product = useQuery({ queryKey: ['product', id], queryFn: () => catalogApi.product(id), enabled: id > 0 })

  if (product.isPending) return <PageLoader />
  if (product.isError) {
    return (
      <div className="container-page">
        {product.error.status === 404 ? (
          <EmptyState
            title="Sản phẩm không tồn tại hoặc đã ngừng bán"
            action={<Link to="/products">Xem sản phẩm khác</Link>}
          />
        ) : (
          <ErrorState message={product.error.message} onRetry={() => product.refetch()} />
        )}
      </div>
    )
  }
  return <ProductView product={product.data} />
}

function ProductView({ product }: { product: ProductDetail }) {
  const { isCustomer, session } = useAuth()
  const navigate = useNavigate()
  const [quantity, setQuantity] = useState(1)
  const [imageIndex, setImageIndex] = useState(0)
  const [assignOpen, setAssignOpen] = useState(false)
  const addToCart = useAddToCart()
  const outOfStock = product.stockQuantity <= 0
  const maxQuantity = Math.min(product.stockQuantity, 99)
  const image = product.images[imageIndex]

  const buyNow = () => addToCart.mutate({ productId: product.id, quantity }, { onSuccess: () => navigate('/cart') })

  return (
    <div className="container-page py-6">
      <nav className="text-[13px] text-ink-muted" aria-label="Breadcrumb">
        <Link to="/" className="text-ink-muted hover:text-ink">
          Trang chủ
        </Link>{' '}
        /{' '}
        <Link to="/products" className="text-ink-muted hover:text-ink">
          Sản phẩm
        </Link>{' '}
        /{' '}
        <Link to={`/products?categoryId=${product.categoryId}`} className="text-ink-muted hover:text-ink">
          {product.categoryName}
        </Link>
      </nav>

      <div className="mt-5 grid gap-8 lg:grid-cols-2 lg:gap-12">
        <div>
          <div className="overflow-hidden rounded-lg border border-line">
            <ProductArt url={image?.url} name={product.name} skillCode={dominantSkill(product.skillImpacts)} />
          </div>
          {product.images.length > 1 && (
            <div className="mt-3 flex gap-2 overflow-x-auto">
              {product.images.map((img, i) => (
                <button
                  type="button"
                  key={img.id}
                  onClick={() => setImageIndex(i)}
                  className={`w-20 shrink-0 overflow-hidden rounded-md border-2 ${i === imageIndex ? 'border-primary' : 'border-transparent'}`}
                  aria-label={`Ảnh ${i + 1}`}
                >
                  <img src={img.url} alt="" className="aspect-[4/3] w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div>
          <span className="inline-flex rounded-sm bg-muted px-2 py-0.5 text-[12.5px] font-semibold text-ink-2">
            {ageRange(product.minAge, product.maxAge)}
          </span>
          <h1 className="mt-3 text-[26px] font-bold leading-tight tracking-[-0.02em] sm:text-[32px]">{product.name}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-[14px] text-ink-muted">
            {product.reviewCount > 0 ? (
              <>
                <Stars value={product.averageRating} />
                <b className="text-ink">{formatDecimal(product.averageRating)}</b>· {product.reviewCount} đánh giá
              </>
            ) : (
              'Chưa có đánh giá'
            )}
            {product.soldCount > 0 && <span>· đã bán {product.soldCount.toLocaleString('vi-VN')}</span>}
          </div>
          <p className="mt-4 text-[30px] font-extrabold tracking-[-0.02em]">{formatPrice(product.price)}</p>
          <p className={`mt-1 text-[14px] font-medium ${outOfStock ? 'text-danger' : 'text-success'}`}>
            {outOfStock ? 'Tạm hết hàng' : `Còn ${product.stockQuantity} sản phẩm trong kho`}
          </p>

          <div className="card mt-6 p-5">
            <div className="mb-4 flex items-baseline justify-between">
              <p className="font-bold">Chỉ số tác động lên kỹ năng</p>
              <span className="text-[12.5px] text-ink-muted">thang 0 – 10</span>
            </div>
            {product.skillImpacts.length === 0 ? (
              <p className="text-[14px] text-ink-muted">Sản phẩm chưa được thiết lập chỉ số kỹ năng.</p>
            ) : (
              <div className="space-y-3.5">
                {product.skillImpacts.map((s) => (
                  <SkillBar key={s.skillId} code={s.skillCode} name={s.skillName} value={s.impactIndex} />
                ))}
              </div>
            )}
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <QuantityStepper
              value={quantity}
              max={Math.max(1, maxQuantity)}
              onChange={setQuantity}
              disabled={outOfStock}
            />
            <Button
              className="flex-1"
              size="lg"
              disabled={outOfStock}
              loading={addToCart.isPending}
              onClick={() => addToCart.mutate({ productId: product.id, quantity })}
            >
              Thêm vào giỏ hàng
            </Button>
            <Button
              className="flex-1"
              size="lg"
              variant="accent"
              disabled={outOfStock || addToCart.isPending}
              onClick={buyNow}
            >
              Mua ngay
            </Button>
          </div>
          {(isCustomer || !session) && (
            <Button
              variant="secondary"
              block
              className="mt-3"
              onClick={() =>
                session ? setAssignOpen(true) : navigate('/login', { state: { from: `/products/${product.id}` } })
              }
            >
              Bé đã có món này? Gán vào hồ sơ bé
            </Button>
          )}
        </div>
      </div>

      <section className="mt-12 grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <h2 className="h2">Mô tả</h2>
          <p className="mt-3 whitespace-pre-line text-ink-2">{product.description ?? 'Chưa có mô tả.'}</p>
        </div>
        <dl className="card grid grid-cols-[auto_1fr] gap-x-6 gap-y-2.5 self-start p-5 text-[14px]">
          <dt className="text-ink-muted">Danh mục</dt>
          <dd className="font-semibold">{product.categoryName}</dd>
          <dt className="text-ink-muted">Độ tuổi</dt>
          <dd className="font-semibold">{ageRange(product.minAge, product.maxAge)}</dd>
          <dt className="text-ink-muted">Nhóm kỹ năng</dt>
          <dd className="font-semibold">
            {product.skillImpacts
              .filter((s) => s.impactIndex >= 5)
              .map((s) => s.skillName)
              .join(', ') || '—'}
          </dd>
        </dl>
      </section>

      <ReviewSection product={product} />
      <RelatedProducts product={product} />

      {isCustomer && (
        <AssignToChildModal
          open={assignOpen}
          onClose={() => setAssignOpen(false)}
          productId={product.id}
          productName={product.name}
        />
      )}
    </div>
  )
}

function ReviewSection({ product }: { product: ProductDetail }) {
  const { isCustomer } = useAuth()
  const [page, setPage] = useState(0)
  const [writeOpen, setWriteOpen] = useState(false)
  const reviews = useQuery({
    queryKey: ['reviews', product.id, page],
    queryFn: () => catalogApi.reviews(product.id, page, 5),
  })
  const stars = [5, 4, 3, 2, 1]

  return (
    <section className="mt-12">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="h2">Đánh giá từ phụ huynh</h2>
        {isCustomer && (
          <Button variant="secondary" size="sm" onClick={() => setWriteOpen(true)}>
            Viết đánh giá
          </Button>
        )}
      </div>
      <div className="mt-5 grid gap-8 md:grid-cols-[240px_1fr]">
        <div className="card self-start p-5">
          <p className="text-[40px] font-extrabold leading-none">{formatDecimal(product.averageRating)}</p>
          <Stars value={product.averageRating} size={16} />
          <p className="mt-1 text-[13px] text-ink-muted">{product.reviewCount} đánh giá</p>
          <div className="mt-4 space-y-1.5">
            {stars.map((s) => {
              const count = product.ratingBreakdown[String(s)] ?? 0
              const pct = product.reviewCount ? (count / product.reviewCount) * 100 : 0
              return (
                <div key={s} className="flex items-center gap-2 text-[12.5px] text-ink-muted">
                  <span className="w-3">{s}</span>
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-accent" style={{ width: `${pct}%` }} />
                  </div>
                  <span className="w-6 text-right">{count}</span>
                </div>
              )
            })}
          </div>
        </div>
        <div>
          {reviews.data?.content.length === 0 && (
            <p className="py-6 text-ink-muted">Chưa có đánh giá nào. Đánh giá được mở sau khi đơn hàng đã giao.</p>
          )}
          <ul className="divide-y divide-line">
            {reviews.data?.content.map((r) => (
              <li key={r.id} className="py-4 first:pt-0">
                <div className="flex flex-wrap items-center gap-2 text-[14px]">
                  <b>{r.customerName}</b>
                  <Stars value={r.rating} size={13} />
                  <span className="text-[12.5px] text-ink-faint">{formatRelative(r.createdAt)}</span>
                </div>
                {r.comment && <p className="mt-1.5 text-[14px] text-ink-2">{r.comment}</p>}
              </li>
            ))}
          </ul>
          {reviews.data && (
            <div className="mt-4">
              <Pagination page={page} totalPages={reviews.data.totalPages} onChange={setPage} />
            </div>
          )}
        </div>
      </div>
      <WriteReviewModal
        open={writeOpen}
        onClose={() => setWriteOpen(false)}
        productId={product.id}
        productName={product.name}
      />
    </section>
  )
}

function RelatedProducts({ product }: { product: ProductDetail }) {
  const related = useQuery({
    queryKey: ['products', { categoryId: product.categoryId, size: 5 }],
    queryFn: () => catalogApi.products({ categoryId: product.categoryId, size: 5 }),
  })
  const items = related.data?.content.filter((p) => p.id !== product.id).slice(0, 4) ?? []
  if (items.length === 0) return null
  return (
    <section className="mt-12">
      <h2 className="h2 mb-5">Cùng danh mục</h2>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {items.map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
    </section>
  )
}
