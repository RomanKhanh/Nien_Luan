import { useState, type ReactNode } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { catalogApi, childApi } from '@/api/endpoints'
import type { ChildProfile, Page, ProductQuery, ProductSort, ProductSummary, SkillGain } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { SkillIcon } from '@/components/decor/Decor'
import { PageHero } from '@/components/layout/PageHero'
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { useCategories, useSkills } from '@/features/catalog/queries'
import { childKeys } from '@/features/children/keys'
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

// chỉ có khi đang xem "Hợp với bé": sắp theo mức bổ sung cho hồ sơ kỹ năng của bé
const FIT_SORT = { value: 'fit' as const, label: 'Bổ sung nhiều nhất cho bé' }

// một ô sản phẩm; gains chỉ có ở chế độ "Hợp với bé"
type ListItem = { product: ProductSummary; gains?: SkillGain[] }

// bộ lọc nằm trên URL để chia sẻ / quay lại trang vẫn giữ nguyên
function readQuery(params: URLSearchParams, forChild: boolean): ProductQuery {
  const num = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined)
  const sort = params.get('sort') as ProductSort | null
  return {
    keyword: params.get('keyword') ?? undefined,
    categoryId: num('categoryId'),
    ageFrom: num('ageFrom'),
    ageTo: num('ageTo'),
    skills: params.get('skills')?.split(',').filter(Boolean) ?? [],
    minPrice: num('minPrice'),
    maxPrice: num('maxPrice'),
    inStock: params.get('inStock') === 'true' || undefined,
    // mặc định: "Hợp với bé" sắp theo mức bổ sung, còn lại theo mức độ phù hợp; fit không dùng được khi bỏ chọn bé
    sort: sort && (forChild || sort !== 'fit') ? sort : forChild ? 'fit' : 'relevance',
    page: num('page') ?? 0,
    size: PAGE_SIZE,
  }
}

export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const { isCustomer } = useAuth()
  const [filterOpen, setFilterOpen] = useState(false)

  // "Hợp với bé": ?child=<id> -> lọc theo tuổi bé, ẩn món bé đã có, kèm mức bổ sung cho hồ sơ kỹ năng
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list, enabled: isCustomer })
  const childParam = Number(params.get('child')) || undefined
  const child = isCustomer ? children.data?.find((c) => c.id === childParam) : undefined
  const waitingForChild = isCustomer && childParam !== undefined && children.isPending
  const query = readQuery(params, Boolean(child))
  const defaultSort = child ? 'fit' : 'relevance'

  const products = useQuery({
    queryKey: child ? ['product-matches', child.id, query] : ['products', query],
    queryFn: async (): Promise<Page<ListItem>> => {
      if (child) return childApi.productMatches(child.id, query)
      const page = await catalogApi.products(query)
      return { ...page, content: page.content.map((product) => ({ product })) }
    },
    enabled: !waitingForChild,
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
    (query.ageFrom !== undefined && !child ? 1 : 0) +
    (query.skills?.length ?? 0) +
    (query.categoryId ? 1 : 0) +
    (query.minPrice !== undefined || query.maxPrice !== undefined ? 1 : 0) +
    (query.inStock ? 1 : 0)

  // xoá bộ lọc nhưng vẫn giữ bé đang chọn
  const clearFilters = () => setParams(child ? { child: String(child.id) } : {})
  const filters = <FilterPanel query={query} update={update} child={child} />

  return (
    <>
      <PageHero
        crumbs={[{ label: 'Sản phẩm' }]}
        kicker="Đồ chơi STEM"
        title={
          child ? `Hợp với bé ${child.name}` : query.keyword ? `Kết quả cho “${query.keyword}”` : 'Tất cả sản phẩm'
        }
        subtitle={
          child
            ? `Đồ chơi hợp ${child.age} tuổi mà bé chưa có, kèm mức bổ sung cho hồ sơ kỹ năng của bé.`
            : 'Lọc theo độ tuổi, nhóm kỹ năng và mức giá để tìm món phù hợp với bé.'
        }
      >
        <QuickSkillChips
          selected={query.skills ?? []}
          onToggle={(next) => update({ skills: next.join(',') || undefined })}
        />
        {isCustomer && (children.data?.length ?? 0) > 0 && (
          <ChildPicker
            childrenList={children.data!}
            selectedId={child?.id}
            onSelect={(id) => update({ child: id ? String(id) : undefined, sort: undefined })}
          />
        )}
      </PageHero>
      <div className="container-page py-8">
        <div className="grid gap-8 lg:grid-cols-[260px_1fr]">
          <aside className="hidden lg:block">
            <div className="card sticky top-28 p-5">
              <div className="mb-4 flex items-center justify-between">
                <p className="font-display text-[20px] font-extrabold">Bộ lọc</p>
                {activeCount > 0 && (
                  <button type="button" className="text-[13px] font-semibold text-primary" onClick={clearFilters}>
                    Xoá tất cả
                  </button>
                )}
              </div>
              {filters}
            </div>
          </aside>

          <div>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              {/* chưa chọn bộ lọc / từ khóa nào thì đang xem toàn bộ, không nói "phù hợp bộ lọc" */}
              {!child && activeCount === 0 && !query.keyword ? (
                <p className="text-[14px] font-semibold text-ink">Tất cả sản phẩm</p>
              ) : (
                <p className="text-[14px] text-ink-muted">
                  <b className="text-ink">{products.data?.totalElements ?? '…'}</b>{' '}
                  {child ? `món hợp tuổi bé ${child.name}, đã ẩn món bé có rồi` : 'sản phẩm phù hợp bộ lọc'}
                </p>
              )}
              <div className="flex items-center gap-2">
                <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFilterOpen(true)}>
                  Bộ lọc{activeCount > 0 && ` · ${activeCount}`}
                </Button>
                <label className="flex items-center gap-2 text-[13px] text-ink-muted">
                  <span className="hidden sm:inline">Sắp xếp</span>
                  <Select
                    value={query.sort}
                    onChange={(e) => update({ sort: e.target.value === defaultSort ? undefined : e.target.value })}
                    className="!w-auto !py-2 text-[13px]"
                  >
                    {(child ? [FIT_SORT, ...SORTS] : SORTS).map((s) => (
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
                description={
                  child
                    ? `Không còn món nào hợp tuổi bé ${child.name} mà bé chưa có với bộ lọc này.`
                    : 'Thử bỏ bớt bộ lọc hoặc đổi từ khoá tìm kiếm.'
                }
                action={
                  <Button variant="secondary" onClick={clearFilters}>
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
                    <ProductCard key={p.product.id} product={p.product} gains={p.gains} />
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
              <Button variant="secondary" onClick={clearFilters}>
                Xoá tất cả
              </Button>
              <Button onClick={() => setFilterOpen(false)}>Xem {products.data?.totalElements ?? ''} sản phẩm</Button>
            </>
          }
        >
          {filters}
        </Modal>
      </div>
    </>
  )
}

// hàng nút nhanh 4 nhóm kỹ năng ngay dưới tiêu đề (bấm lại để bỏ chọn)
function QuickSkillChips({ selected, onToggle }: { selected: string[]; onToggle: (next: string[]) => void }) {
  const skills = useSkills()
  return (
    <div className="mt-6 flex flex-wrap gap-2.5">
      {(skills.data ?? []).map((s) => {
        const t = skillTheme(s.code, s.name)
        const on = selected.includes(s.code)
        return (
          <button
            key={s.id}
            type="button"
            aria-pressed={on}
            onClick={() => onToggle(on ? selected.filter((c) => c !== s.code) : [...selected, s.code])}
            className="flex items-center gap-2 rounded-full border-2 bg-white py-1.5 pl-1.5 pr-4 text-[13.5px] font-bold shadow-card transition hover:-translate-y-0.5"
            style={{ borderColor: on ? t.color : 'transparent', color: t.color, background: on ? t.soft : '#fff' }}
          >
            <span className="grid h-7 w-7 place-items-center rounded-full text-white" style={{ background: t.color }}>
              <SkillIcon code={s.code} className="h-4 w-4" />
            </span>
            {s.name}
            {on && <span aria-hidden>✓</span>}
          </button>
        )
      })}
    </div>
  )
}

// "Hợp với bé": chọn bé để lọc theo tuổi bé và xem mức bổ sung; "Tất cả" = danh sách thường
function ChildPicker({
  childrenList,
  selectedId,
  onSelect,
}: {
  childrenList: ChildProfile[]
  selectedId?: number
  onSelect: (id: number | undefined) => void
}) {
  const chip = (on: boolean) =>
    `rounded-full border-2 px-3.5 py-1.5 text-[13.5px] font-bold transition hover:-translate-y-0.5 ${
      on ? 'border-sun bg-sun text-ink shadow-card' : 'border-white/60 bg-white/15 text-white hover:bg-white/25'
    }`
  return (
    <div className="mt-4 flex flex-wrap items-center gap-2">
      <span className="text-[13.5px] font-semibold text-white/90">Hợp với bé:</span>
      <button
        type="button"
        aria-pressed={!selectedId}
        className={chip(!selectedId)}
        onClick={() => onSelect(undefined)}
      >
        Tất cả sản phẩm
      </button>
      {childrenList.map((c) => (
        <button
          key={c.id}
          type="button"
          aria-pressed={selectedId === c.id}
          className={chip(selectedId === c.id)}
          onClick={() => onSelect(c.id)}
        >
          🧒 {c.name} · {c.age} tuổi
        </button>
      ))}
    </div>
  )
}

function FilterPanel({
  query,
  update,
  child,
}: {
  query: ProductQuery
  update: (changes: Record<string, string | undefined>) => void
  child?: ChildProfile
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
        {child ? (
          <p className="rounded-xl bg-primary-soft px-3 py-2 text-[13px] font-semibold text-primary-hover">
            Theo tuổi bé {child.name}: {child.age} tuổi
          </p>
        ) : (
          <AgeOptions query={query} update={update} />
        )}
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

function AgeOptions({
  query,
  update,
}: {
  query: ProductQuery
  update: (changes: Record<string, string | undefined>) => void
}) {
  return (
    <>
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
    </>
  )
}

function FilterGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 flex items-center gap-2 text-[13px] font-bold uppercase tracking-[0.06em] text-ink-muted">
        <span className="h-2 w-2 rounded-full bg-sun" aria-hidden />
        {title}
      </legend>
      <div className="space-y-0.5">{children}</div>
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
    <label
      className={`flex cursor-pointer items-center gap-2.5 rounded-xl px-2 py-1.5 transition hover:bg-sky-soft ${checked ? 'bg-primary-soft font-semibold text-primary-hover' : 'text-ink-2'}`}
    >
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
