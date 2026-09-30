import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { catalogApi } from '@/api/endpoints'
import type { ProductDetail } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { SkillIcon, TornEdge } from '@/components/decor/Decor'
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
import { ageRange, formatDecimal, formatPrice, formatRelative, initials } from '@/lib/format'
import { dominantSkill, skillTheme } from '@/lib/skills'

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
  const theme = skillTheme(dominantSkill(product.skillImpacts) ?? '')

  const buyNow = () => addToCart.mutate({ productId: product.id, quantity }, { onSuccess: () => navigate('/cart') })

  return (
    <>
      {/* nền trời nhạt phía sau khu ảnh + thông tin, kết thúc bằng mép giấy xé */}
      <div className="relative bg-sky-soft">
        <span className="absolute -left-16 top-24 h-48 w-48 rounded-full bg-white/70" aria-hidden />
        <span className="absolute right-[8%] top-6 h-24 w-24 rounded-full bg-white/70" aria-hidden />
        <div className="container-page relative pb-14 pt-6">
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
              <div className="relative">
                <span
                  className="absolute -right-3 -top-3 h-16 w-16 animate-float rounded-full opacity-80"
                  style={{ background: theme.color }}
                  aria-hidden
                />
                <span
                  className="absolute -bottom-3 -left-3 h-10 w-10 animate-float-slow rounded-xl bg-sun"
                  aria-hidden
                />
                <div
                  className="relative overflow-hidden rounded-[28px] border-[6px] bg-white shadow-pop"
                  style={{ borderColor: theme.color }}
                >
                  <ProductArt url={image?.url} name={product.name} skillCode={dominantSkill(product.skillImpacts)} />
                </div>
              </div>
              {product.images.length > 1 && (
                <div className="mt-5 flex gap-2 overflow-x-auto p-1">
                  {product.images.map((img, i) => (
                    <button
                      type="button"
                      key={img.id}
                      onClick={() => setImageIndex(i)}
                      className={`w-20 shrink-0 overflow-hidden rounded-2xl border-[3px] bg-white transition ${i === imageIndex ? 'border-primary' : 'border-transparent opacity-70 hover:opacity-100'}`}
                      aria-label={`Ảnh ${i + 1}`}
                    >
                      <img src={img.url} alt="" className="aspect-[4/3] w-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div>
              <div className="flex flex-wrap gap-2">
                <span className="inline-flex -rotate-2 rounded-full bg-sun px-3 py-1 text-[13px] font-bold text-ink shadow-card">
                  🎈 {ageRange(product.minAge, product.maxAge)}
                </span>
                <Link
                  to={`/products?categoryId=${product.categoryId}`}
                  className="inline-flex rounded-full bg-white px-3 py-1 text-[13px] font-bold text-sky-deep shadow-card"
                >
                  {product.categoryName}
                </Link>
              </div>
              <h1 className="mt-3 font-display text-[32px] font-extrabold leading-[1.1] sm:text-[40px]">
                {product.name}
              </h1>
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
              <p className="mt-4 font-display text-[38px] font-extrabold leading-none text-coral">
                {formatPrice(product.price)}
              </p>
              <p className={`mt-2 text-[14px] font-semibold ${outOfStock ? 'text-danger' : 'text-success'}`}>
                {outOfStock ? 'Tạm hết hàng' : `Còn ${product.stockQuantity} sản phẩm trong kho`}
              </p>

              <div className="card mt-6 border-0 p-5 shadow-card-hover">
                <div className="mb-4 flex items-baseline justify-between">
                  <p className="font-display text-[19px] font-extrabold">Chỉ số tác động lên kỹ năng</p>
                  <span className="text-[12.5px] text-ink-muted">thang 0 – 10</span>
                </div>
                {product.skillImpacts.length === 0 ? (
                  <p className="text-[14px] text-ink-muted">Sản phẩm chưa được thiết lập chỉ số kỹ năng.</p>
                ) : (
                  <div className="space-y-3.5">
                    {product.skillImpacts.map((s) => {
                      const t = skillTheme(s.skillCode, s.skillName)
                      return (
                        <div key={s.skillId} className="flex items-center gap-3">
                          <span
                            className="grid h-9 w-9 shrink-0 place-items-center rounded-full"
                            style={{ background: t.soft, color: t.color }}
                            aria-hidden
                          >
                            <SkillIcon code={s.skillCode} className="h-5 w-5" />
                          </span>
                          <div className="flex-1">
                            <SkillBar code={s.skillCode} name={s.skillName} value={s.impactIndex} />
                          </div>
                        </div>
                      )
                    })}
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
        </div>
        <TornEdge className="absolute inset-x-0 bottom-0" seed={29} />
      </div>

      <div className="container-page pb-6">
        <section className="mt-8 grid gap-8 lg:grid-cols-[1.4fr_1fr]">
          <div>
            <h2 className="h2">Mô tả</h2>
            <p className="mt-3 whitespace-pre-line text-ink-2">{product.description ?? 'Chưa có mô tả.'}</p>
          </div>
          <dl className="card grid grid-cols-[auto_1fr] gap-x-6 gap-y-2.5 self-start border-dashed border-sky/50 bg-sky-soft/40 p-5 text-[14px]">
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
    </>
  )
}

const AVATAR_COLORS = ['#1fa6dd', '#e8467c', '#7cc243', '#e08700', '#5b3df5']

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
        <div className="card self-start border-0 bg-[#fff7e0] p-5">
          <p className="font-display text-[52px] font-extrabold leading-none text-[#b37800]">
            {formatDecimal(product.averageRating)}
          </p>
          <Stars value={product.averageRating} size={16} />
          <p className="mt-1 text-[13px] text-ink-muted">{product.reviewCount} đánh giá</p>
          <div className="mt-4 space-y-1.5">
            {stars.map((s) => {
              const count = product.ratingBreakdown[String(s)] ?? 0
              const pct = product.reviewCount ? (count / product.reviewCount) * 100 : 0
              return (
                <div key={s} className="flex items-center gap-2 text-[12.5px] text-ink-muted">
                  <span className="w-3">{s}</span>
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-white">
                    <div className="h-full rounded-full bg-sun" style={{ width: `${pct}%` }} />
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
          <ul className="space-y-3">
            {reviews.data?.content.map((r, i) => (
              <li key={r.id} className="flex gap-3">
                <span
                  className="grid h-10 w-10 shrink-0 place-items-center rounded-full font-display text-[16px] font-extrabold text-white"
                  style={{ background: AVATAR_COLORS[i % AVATAR_COLORS.length] }}
                  aria-hidden
                >
                  {initials(r.customerName)}
                </span>
                {/* bong bóng lời nói */}
                <div className="relative flex-1 rounded-2xl rounded-tl-sm border-[1.5px] border-line bg-surface px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2 text-[14px]">
                    <b>{r.customerName}</b>
                    <Stars value={r.rating} size={13} />
                    <span className="text-[12.5px] text-ink-faint">{formatRelative(r.createdAt)}</span>
                  </div>
                  {r.comment && <p className="mt-1 text-[14px] text-ink-2">{r.comment}</p>}
                </div>
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
