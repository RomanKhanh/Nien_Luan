import { useQueries, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { catalogApi, childApi } from '@/api/endpoints'
import type { SkillScore } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard'
import { SkillBar } from '@/components/ui/Badges'
import { ButtonLink } from '@/components/ui/Button'
import { useSkills } from '@/features/catalog/queries'
import { childKeys } from '@/features/children/keys'
import { averageImpact } from '@/features/skills/skillMath'
import { AGE_GROUPS, SKILL_HINTS, skillTheme } from '@/lib/skills'

export default function HomePage() {
  const { isCustomer } = useAuth()
  const featured = useQuery({
    queryKey: ['products', { sort: 'relevance', size: 4, inStock: true }],
    queryFn: () => catalogApi.products({ sort: 'relevance', size: 4, inStock: true }),
  })
  const total = useQuery({
    queryKey: ['products', { size: 1 }],
    queryFn: () => catalogApi.products({ size: 1 }),
  })

  return (
    <>
      <section className="border-b border-line bg-surface">
        <div className="container-page grid items-center gap-10 py-12 lg:grid-cols-[1.1fr_1fr] lg:py-16">
          <div>
            <span className="inline-flex rounded-full bg-primary-soft px-3 py-1 text-[13px] font-semibold text-primary-hover">
              Gợi ý có giải thích lý do
            </span>
            <h1 className="mt-4 text-[34px] font-extrabold leading-[1.12] tracking-[-0.03em] sm:text-[46px]">
              Chọn đồ chơi theo <span className="text-primary">lộ trình kỹ năng</span> của bé
            </h1>
            <p className="mt-4 max-w-xl text-[16px] text-ink-muted">
              Mỗi món đồ chơi STEM trên BrainBlocks đều có chỉ số tác động lên 4 nhóm kỹ năng. Tạo hồ sơ cho bé, xem
              nhóm nào đang được chú trọng và nhóm nào chưa khai thác.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <ButtonLink to={isCustomer ? '/children' : '/register'} size="lg">
                Tạo hồ sơ cho bé
              </ButtonLink>
              <ButtonLink to="/products" size="lg" variant="secondary">
                Khám phá sản phẩm
              </ButtonLink>
            </div>
            <dl className="mt-9 flex flex-wrap gap-x-10 gap-y-4">
              <Stat value={total.data ? String(total.data.totalElements) : '—'} label="món đồ chơi STEM" />
              <Stat value="4 nhóm" label="kỹ năng được đo" />
              <Stat value="3–12" label="độ tuổi phù hợp" />
            </dl>
          </div>
          <HeroProfileCard />
        </div>
      </section>

      <AgeSection />
      <SkillSection />

      <section className="container-page mt-14">
        <div className="mb-5 flex items-end justify-between gap-4">
          <h2 className="text-[24px] font-bold tracking-[-0.02em] sm:text-[26px]">Sản phẩm nổi bật</h2>
          <Link to="/products" className="text-[14px] font-semibold">
            Xem tất cả →
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {featured.isPending
            ? Array.from({ length: 4 }, (_, i) => <ProductCardSkeleton key={i} />)
            : featured.data?.content.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>

      <section className="container-page mt-14">
        <div className="grid items-center gap-8 rounded-lg bg-secondary p-8 text-white sm:p-10 lg:grid-cols-[1.2fr_1fr]">
          <div>
            <span className="text-[13px] font-semibold uppercase tracking-[0.08em] text-accent">Lộ trình kỹ năng</span>
            <h2 className="mt-3 text-[26px] font-bold leading-tight tracking-[-0.02em] sm:text-[30px]">
              Không biết mua gì tiếp theo cho bé?
            </h2>
            <p className="mt-3 max-w-lg text-[15px] text-[#BFC7DA]">
              Hồ sơ kỹ năng tổng hợp từ đồ chơi bé đã có, chỉ ra nhóm kỹ năng chưa được khai thác và gợi ý món tiếp
              theo, luôn kèm lý do vì sao phù hợp — không chỉ là sản phẩm bán chạy.
            </p>
            <ButtonLink to="/children" variant="accent" size="lg" className="mt-6">
              Xem lộ trình của bé
            </ButtonLink>
          </div>
          <ol className="space-y-3 text-[14px]">
            {[
              'Tạo hồ sơ bé: tuổi và nhóm kỹ năng quan tâm',
              'Thêm đồ chơi bé đã có hoặc mua qua BrainBlocks',
              'Xem biểu đồ kỹ năng và gợi ý món tiếp theo',
            ].map((step, i) => (
              <li key={step} className="flex items-center gap-3 rounded-md bg-white/5 px-4 py-3">
                <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-accent text-[13px] font-bold text-ink">
                  {i + 1}
                </span>
                {step}
              </li>
            ))}
          </ol>
        </div>
      </section>
    </>
  )
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <dt className="sr-only">{label}</dt>
      <dd className="text-[24px] font-extrabold tracking-[-0.02em]">{value}</dd>
      <dd className="text-[13px] text-ink-muted">{label}</dd>
    </div>
  )
}

// khách đã có hồ sơ bé: hiện lộ trình thật của bé đầu tiên; còn lại hiện thẻ ví dụ (có ghi rõ)
function HeroProfileCard() {
  const { isCustomer } = useAuth()
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list, enabled: isCustomer })
  const first = children.data?.[0]
  const profile = useQuery({
    queryKey: childKeys.skillProfile(first?.id ?? 0),
    queryFn: () => childApi.skillProfile(first!.id),
    enabled: Boolean(first),
  })
  const skills = useSkills()

  const real = first && profile.data && profile.data.totalProducts > 0
  const scores: { code: string; name: string; value: number }[] = real
    ? profile.data!.skillScores.map((s: SkillScore) => ({
        code: s.skillCode,
        name: s.skillName,
        value: averageImpact(s, profile.data!.totalProducts),
      }))
    : (skills.data ?? []).slice(0, 4).map((s, i) => ({ code: s.code, name: s.name, value: [8, 4.5, 6, 7.5][i] ?? 5 }))
  const weakest = real ? profile.data!.weakestSkill : null

  return (
    <div className="card p-6">
      <div className="mb-5 flex items-center justify-between">
        <p className="font-bold">
          {real ? `Lộ trình của bé ${first!.name} · ${first!.age} tuổi` : 'Ví dụ: lộ trình của một bé 7 tuổi'}
        </p>
        {real && (
          <Link to={`/children/${first!.id}`} className="text-[13px] font-semibold">
            Xem đầy đủ
          </Link>
        )}
      </div>
      <div className="space-y-4">
        {scores.map((s) => (
          <SkillBar key={s.code} code={s.code} name={s.name} value={s.value} />
        ))}
      </div>
      <p className="mt-5 rounded-md bg-muted px-4 py-3 text-[13.5px] text-ink-2">
        {weakest ? (
          <>
            <b style={{ color: skillTheme(weakest.skillCode).color }}>{weakest.skillName}</b> là nhóm chưa được khai
            thác nhiều — xem gợi ý đồ chơi bổ sung trong hồ sơ của bé.
          </>
        ) : (
          'Điểm là mức tác động trung bình của các đồ chơi bé đã có lên từng nhóm kỹ năng (thang 0–10).'
        )}
      </p>
    </div>
  )
}

function AgeSection() {
  // số sản phẩm mỗi nhóm tuổi: lấy totalElements với size = 1
  const counts = useQueries({
    queries: AGE_GROUPS.map((g) => ({
      queryKey: ['products', { ageFrom: g.from, ageTo: g.to, size: 1 }],
      queryFn: () => catalogApi.products({ ageFrom: g.from, ageTo: g.to, size: 1 }),
      staleTime: 5 * 60_000,
    })),
  })
  const colors = ['#EBF1FF', '#FDEBF2', '#FEF3E0', '#E6F6F1']
  return (
    <section className="container-page mt-14">
      <div className="mb-5 flex items-end justify-between gap-4">
        <h2 className="text-[24px] font-bold tracking-[-0.02em] sm:text-[26px]">Chọn theo độ tuổi của bé</h2>
        <Link to="/products" className="text-[14px] font-semibold">
          Tất cả độ tuổi →
        </Link>
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {AGE_GROUPS.map((g, i) => (
          <Link
            key={g.label}
            to={`/products?ageFrom=${g.from}&ageTo=${g.to}`}
            className="rounded-lg border border-line p-5 text-ink transition hover:-translate-y-0.5 hover:text-ink hover:shadow-card-hover"
            style={{ background: colors[i] }}
          >
            <p className="text-[20px] font-bold">{g.label}</p>
            <p className="mt-1 text-[13.5px] text-ink-muted">
              {counts[i].data ? `${counts[i].data.totalElements} sản phẩm` : '…'}
            </p>
          </Link>
        ))}
      </div>
    </section>
  )
}

function SkillSection() {
  const skills = useSkills()
  return (
    <section className="container-page mt-14">
      <h2 className="text-[24px] font-bold tracking-[-0.02em] sm:text-[26px]">Chọn theo nhóm kỹ năng</h2>
      <p className="mb-5 mt-1 text-[14px] text-ink-muted">Bốn nhóm kỹ năng được dùng nhất quán trên toàn bộ website</p>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {(skills.data ?? []).map((s) => {
          const theme = skillTheme(s.code, s.name)
          return (
            <Link
              key={s.id}
              to={`/products?skills=${s.code}`}
              className="card p-5 text-ink transition hover:-translate-y-0.5 hover:text-ink hover:shadow-card-hover"
            >
              <span className="mb-4 block h-10 w-10 rounded-md" style={{ background: theme.color }} aria-hidden />
              <p className="text-[16px] font-bold">{s.name}</p>
              <p className="mt-1 text-[13.5px] text-ink-muted">{SKILL_HINTS[s.code] ?? s.description}</p>
            </Link>
          )
        })}
      </div>
    </section>
  )
}
