import { useState, type ReactNode } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { catalogApi } from '@/api/endpoints'
import type { ProductQuery, ProductSort } from '@/api/types'
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { useCategories, useSkills } from '@/features/catalog/queries'
import { AGE_GROUPS, skillTheme } from '@/lib/skills'

const PAGE_SIZE = 12

const SORTS: { value: ProductSort; label: string }[] = [
  { value: 'relevance', label: 'Mức độ phù hợp' },
  { value: 'price_asc', label: 'Giá tăng dần' },
  { value: 'price_desc', label: 'Giá giảm dần' },
  { value: 'newest', label: 'Mới nhất' },
]

const PRICE_RANGES = [
  { label: 'Dưới 200.000₫', min: undefined, max: 200000 },
  { label: '200.000₫ – 500.000₫', min: 200000, max: 500000 },
  { label: '500.000₫ – 1.000.000₫', min: 500000, max: 1000000 },
  { label: 'Trên 1.000.000₫', min: 1000000, max: undefined },
]

// bộ lọc nằm trên URL để chia sẻ / quay lại trang vẫn giữ nguyên
function readQuery(params: URLSearchParams): ProductQuery {
  const num = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined)
  return {
    keyword: params.get('keyword') ?? undefined,
    categoryId: num('categoryId'),
    ageFrom: num('ageFrom'),
    ageTo: num('ageTo'),
    skills: params.get('skills')?.split(',').filter(Boolean) ?? [],
    minPrice: num('minPrice'),
    maxPrice: num('maxPrice'),
    inStock: params.get('inStock') === 'true' || undefined,
    sort: (params.get('sort') as ProductSort) ?? 'relevance',
    page: num('page') ?? 0,
    size: PAGE_SIZE,
  }
}

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const query = readQuery(params)
  const [filterOpen, setFilterOpen] = useState(false)

  const products = useQuery({
    queryKey: ['products', query],
    queryFn: () => catalogApi.products(query),
    placeholderData: keepPreviousData,
  })

  // đổi bộ lọc thì về trang đầu
  const update = (changes: Record<string, string | undefined>, resetPage = true) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined || v === '' ? next.delete(k) : next.set(k, v)))
    if (resetPage) next.delete('page')
    setParams(next)
  }

  const activeCount =
    (query.ageFrom !== undefined ? 1 : 0) +
    (query.skills?.length ?? 0) +
    (query.categoryId ? 1 : 0) +
    (query.minPrice !== undefined || query.maxPrice !== undefined ? 1 : 0) +
    (query.inStock ? 1 : 0)

  const filters = <FilterPanel query={query} update={update} />

  return (
    <div className="container-page py-6">
      <nav className="text-[13px] text-ink-muted" aria-label="Breadcrumb">
        <Link to="/" className="text-ink-muted hover:text-ink">
          Trang chủ
        </Link>{' '}
        / <span className="text-ink-2">Sản phẩm</span>
      </nav>
      <h1 className="h1 mt-2">{query.keyword ? `Kết quả cho “${query.keyword}”` : 'Tất cả sản phẩm'}</h1>

      <div className="mt-6 grid gap-8 lg:grid-cols-[260px_1fr]">
        <aside className="hidden lg:block">
          <div className="card sticky top-20 p-5">
            <div className="mb-4 flex items-center justify-between">
              <p className="font-bold">Bộ lọc</p>
              {activeCount > 0 && (
                <button type="button" className="text-[13px] font-semibold text-primary" onClick={() => setParams({})}>
                  Xoá tất cả
                </button>
              )}
            </div>
            {filters}
          </div>
        </aside>

        <div>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="text-[14px] text-ink-muted">
              <b className="text-ink">{products.data?.totalElements ?? '…'}</b> sản phẩm phù hợp bộ lọc
            </p>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFilterOpen(true)}>
                Bộ lọc{activeCount > 0 && ` · ${activeCount}`}
              </Button>
              <label className="flex items-center gap-2 text-[13px] text-ink-muted">
                <span className="hidden sm:inline">Sắp xếp</span>
                <Select
                  value={query.sort}
                  onChange={(e) => update({ sort: e.target.value === 'relevance' ? undefined : e.target.value })}
                  className="!w-auto !py-2 text-[13px]"
                >
                  {SORTS.map((s) => (
                    <option key={s.value} value={s.value}>
                      {s.label}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
          </div>

          {products.isError ? (
            <ErrorState message={products.error.message} onRetry={() => products.refetch()} />
          ) : products.isPending ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
              {Array.from({ length: 6 }, (_, i) => (
                <ProductCardSkeleton key={i} />
              ))}
            </div>
          ) : products.data.content.length === 0 ? (
            <EmptyState
              title="Chưa có sản phẩm nào"
              description="Thử bỏ bớt bộ lọc hoặc đổi từ khoá tìm kiếm."
              action={
                <Button variant="secondary" onClick={() => setParams({})}>
                  Xoá bộ lọc
                </Button>
              }
            />
          ) : (
            <>
              <div
                className={`grid grid-cols-2 gap-4 md:grid-cols-3 ${products.isPlaceholderData ? 'opacity-60' : ''}`}
              >
                {products.data.content.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
              <div className="mt-8">
                <Pagination
                  page={products.data.page}
                  totalPages={products.data.totalPages}
                  onChange={(page) => {
                    update({ page: page === 0 ? undefined : String(page) }, false)
                    window.scrollTo({ top: 0, behavior: 'smooth' })
                  }}
                />
              </div>
            </>
          )}
        </div>
      </div>

      <Modal
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        title="Bộ lọc"
        footer={
          <>
            <Button variant="secondary" onClick={() => setParams({})}>
              Xoá tất cả
            </Button>
            <Button onClick={() => setFilterOpen(false)}>Xem {products.data?.totalElements ?? ''} sản phẩm</Button>
          </>
        }
      >
        {filters}
      </Modal>
    </div>
  )
}

function FilterPanel({
  query,
  update,
}: {
  query: ProductQuery
  update: (changes: Record<string, string | undefined>) => void
}) {
  const skills = useSkills()
  const categories = useCategories()
  const selectedSkills = query.skills ?? []

  const toggleSkill = (code: string) => {
    const next = selectedSkills.includes(code) ? selectedSkills.filter((c) => c !== code) : [...selectedSkills, code]
    update({ skills: next.join(',') || undefined })
  }

  return (
    <div className="space-y-6 text-[14px]">
      <FilterGroup title="Độ tuổi">
        {AGE_GROUPS.map((g) => {
          const checked = query.ageFrom === g.from && query.ageTo === g.to
          return (
            <CheckRow
              key={g.label}
              type="radio"
              name="age"
              checked={checked}
              label={g.label}
              onChange={() =>
                update(
                  checked ? { ageFrom: undefined, ageTo: undefined } : { ageFrom: String(g.from), ageTo: String(g.to) },
                )
              }
            />
          )
        })}
      </FilterGroup>

      <FilterGroup title="Nhóm kỹ năng">
        {(skills.data ?? []).map((s) => (
          <CheckRow
            key={s.id}
            checked={selectedSkills.includes(s.code)}
            label={
              <span className="flex items-center gap-2">
                <span
                  className="h-2.5 w-2.5 rounded-full"
                  style={{ background: skillTheme(s.code).color }}
                  aria-hidden
                />
                {s.name}
              </span>
            }
            onChange={() => toggleSkill(s.code)}
          />
        ))}
        <p className="text-[12px] text-ink-faint">Sản phẩm có chỉ số tác động từ 5/10 trở lên vào nhóm đã chọn.</p>
      </FilterGroup>

      <FilterGroup title="Khoảng giá">
        {PRICE_RANGES.map((r) => {
          const checked = query.minPrice === r.min && query.maxPrice === r.max
          return (
            <CheckRow
              key={r.label}
              type="radio"
              name="price"
              checked={checked}
              label={r.label}
              onChange={() =>
                update(
                  checked
                    ? { minPrice: undefined, maxPrice: undefined }
                    : { minPrice: r.min?.toString(), maxPrice: r.max?.toString() },
                )
              }
            />
          )
        })}
      </FilterGroup>

      <FilterGroup title="Danh mục">
        {(categories.data ?? []).map((c) => (
          <CheckRow
            key={c.id}
            type="radio"
            name="category"
            checked={query.categoryId === c.id}
            label={
              <span className="flex w-full justify-between">
                {c.name}
                <span className="text-ink-faint">{c.productCount}</span>
              </span>
            }
            onChange={() => update({ categoryId: query.categoryId === c.id ? undefined : String(c.id) })}
          />
        ))}
      </FilterGroup>

      <CheckRow
        checked={Boolean(query.inStock)}
        label="Chỉ hiện sản phẩm còn hàng"
        onChange={() => update({ inStock: query.inStock ? undefined : 'true' })}
      />
    </div>
  )
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2.5 text-[13px] font-bold uppercase tracking-[0.06em] text-ink-muted">{title}</legend>
      <div className="space-y-2">{children}</div>
    </fieldset>
  )
}

// radio trong nhóm vẫn bấm lại được để bỏ chọn (onClick thay vì chỉ onChange)
function CheckRow({
  checked,
  label,
  onChange,
  type = 'checkbox',
  name,
}: {
  checked: boolean
  label: ReactNode
  onChange: () => void
  type?: 'checkbox' | 'radio'
  name?: string
}) {
  return (
    <label className="flex cursor-pointer items-center gap-2.5 text-ink-2">
      <input
        type={type}
        name={name}
        checked={checked}
        onChange={() => {}}
        onClick={onChange}
        className="h-4 w-4 shrink-0 accent-primary"
      />
      <span className="flex-1">{label}</span>
    </label>
  )
}
