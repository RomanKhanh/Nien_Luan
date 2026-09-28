import { Link } from 'react-router-dom'
import type { ProductSummary } from '@/api/types'
import { SkillBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { ageRange, formatDecimal, formatPrice } from '@/lib/format'
import { dominantSkill, topImpacts } from '@/lib/skills'
import { useAddToCart } from '@/features/cart/useCart'
import { ProductArt } from './ProductArt'

export function ProductCard({ product, showAddButton = true }: { product: ProductSummary; showAddButton?: boolean }) {
  const addToCart = useAddToCart()
  const outOfStock = product.stockQuantity <= 0
  return (
    <article className="group flex flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-card transition hover:-translate-y-0.5 hover:shadow-card-hover">
      <Link to={`/products/${product.id}`} className="relative block" aria-label={product.name}>
        <ProductArt url={product.thumbnailUrl} name={product.name} skillCode={dominantSkill(product.skillImpacts)} />
        <span className="absolute left-3 top-3 rounded-sm bg-surface/95 px-2 py-0.5 text-[12px] font-semibold text-ink-2">
          {ageRange(product.minAge, product.maxAge)}
        </span>
        {outOfStock && (
          <span className="absolute right-3 top-3 rounded-sm bg-ink px-2 py-0.5 text-[12px] font-semibold text-white">
            Hết hàng
          </span>
        )}
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <Link
          to={`/products/${product.id}`}
          className="line-clamp-2 min-h-[2.8em] text-[15px] font-semibold leading-snug text-ink hover:text-primary"
        >
          {product.name}
        </Link>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {topImpacts(product.skillImpacts).map((i) => (
            <SkillBadge key={i.skillId} code={i.skillCode} name={i.skillName} value={i.impactIndex} />
          ))}
        </div>
        <div className="mt-auto flex items-end justify-between pt-3">
          <span className="text-[17px] font-bold">{formatPrice(product.price)}</span>
          {product.reviewCount > 0 && (
            <span className="text-[13px] text-ink-muted">
              <span className="text-accent">★</span> {formatDecimal(product.averageRating)}
            </span>
          )}
        </div>
        {showAddButton && (
          <Button
            size="sm"
            variant={outOfStock ? 'primary' : 'secondary'}
            className="mt-3"
            block
            disabled={outOfStock}
            loading={addToCart.isPending && addToCart.variables?.productId === product.id}
            onClick={() => addToCart.mutate({ productId: product.id, quantity: 1 })}
          >
            {outOfStock ? 'Hết hàng' : 'Thêm vào giỏ'}
          </Button>
        )}
      </div>
    </article>
  )
}

export function ProductCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-lg border border-line bg-surface">
      <div className="aspect-[4/3] animate-pulse bg-muted" />
      <div className="space-y-2.5 p-4">
        <div className="h-4 w-4/5 animate-pulse rounded bg-muted" />
        <div className="h-4 w-2/5 animate-pulse rounded bg-muted" />
        <div className="h-9 w-full animate-pulse rounded bg-muted" />
      </div>
    </div>
  )
}
