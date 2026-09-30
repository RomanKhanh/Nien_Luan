import { useQueries, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { catalogApi, childApi } from '@/api/endpoints'
import type { SkillScore } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Bubbles, Mascot, Reveal, SkillIcon, TornEdge } from '@/components/decor/Decor'
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
      <section className="relative overflow-hidden bg-gradient-to-br from-sky to-sky-deep text-white">
        <div className="dots-bg absolute inset-0 opacity-60" aria-hidden />
        <Bubbles />
        <div className="container-page relative grid items-center gap-10 pb-16 pt-10 lg:grid-cols-[1.15fr_1fr] lg:pb-24 lg:pt-14">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-white px-3.5 py-1.5 text-[13px] font-bold text-sky-deep shadow-card">
              <span className="h-2 w-2 animate-pulse rounded-full bg-coral" aria-hidden />
              Học mà chơi · Gợi ý có giải thích lý do
            </span>
            <h1 className="sticker-text mt-5 font-display text-[44px] font-extrabold leading-[0.98] sm:text-[64px] lg:text-[72px]">
              <span className="block text-[#1c5fc6]">Chọn đồ chơi</span>
              <span className="block text-leaf">theo lộ trình</span>
              <span className="block text-sun">kỹ năng của bé</span>
            </h1>
            <p className="mt-5 max-w-xl text-[16px] text-white/90">
              Mỗi món đồ chơi STEM trên BrainBlocks đều có chỉ số tác động lên 4 nhóm kỹ năng. Tạo hồ sơ cho bé, xem
              nhóm nào đang được chú trọng và nhóm nào chưa khai thác.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <ButtonLink to={isCustomer ? '/children' : '/register'} size="lg" variant="accent">
                Tạo hồ sơ cho bé
              </ButtonLink>
              <ButtonLink
                to="/products"
                size="lg"
                variant="secondary"
                className="border-transparent shadow-[0_4px_0_rgb(13_27_62/0.18)]"
              >
                Khám phá sản phẩm
              </ButtonLink>
            </div>
            <dl className="mt-9 flex flex-wrap gap-3">
              <Stat value={total.data ? String(total.data.totalElements) : '—'} label="món đồ chơi STEM" />
              <Stat value="4 nhóm" label="kỹ năng được đo" />
              <Stat value="3–12" label="độ tuổi phù hợp" />
            </dl>
          </div>
          <HeroOrbit />
        </div>
        <TornEdge className="absolute inset-x-0 bottom-0" />
      </section>

      <AgeSection />
      <SkillSection />

      <section className="container-page mt-16">
        <Reveal>
          <SectionHead
            kicker="Được phụ huynh chọn nhiều"
            title="Sản phẩm nổi bật"
            link="/products"
            linkLabel="Xem tất cả"
          />
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {featured.isPending
              ? Array.from({ length: 4 }, (_, i) => <ProductCardSkeleton key={i} />)
              : featured.data?.content.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </Reveal>
      </section>

      <RoadmapSection />
    </>
  )
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="rounded-2xl bg-white/15 px-4 py-2.5 backdrop-blur-sm">
      <dt className="sr-only">{label}</dt>
      <dd className="font-display text-[26px] font-extrabold leading-none">{value}</dd>
      <dd className="mt-1 text-[12.5px] text-white/85">{label}</dd>
    </div>
  )
}

function SectionHead({
  kicker,
  title,
  link,
  linkLabel,
}: {
  kicker: string
  title: string
  link?: string
  linkLabel?: string
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <span className="section-kicker">{kicker}</span>
        <h2 className="section-title mt-3">{title}</h2>
      </div>
      {link && (
        <Link
          to={link}
          className="group inline-flex items-center gap-1.5 rounded-full border-2 border-primary px-4 py-1.5 text-[14px] font-bold text-primary transition hover:bg-primary hover:text-white"
        >
          {linkLabel}
          <span className="transition group-hover:translate-x-0.5" aria-hidden>
            →
          </span>
        </Link>
      )}
    </div>
  )
}

// linh vật ở giữa, 4 nhóm kỹ năng là các vòng tròn viền màu nối bằng đường nét đứt (bố cục banner stemplus)
const ORBIT = [
  { code: 'LOGIC', pos: 'left-[2%] top-[4%]', delay: '0s' },
  { code: 'CREATIVE', pos: 'right-[2%] top-[0%]', delay: '1.2s' },
  { code: 'STEM', pos: 'right-[0%] bottom-[8%]', delay: '2.1s' },
  { code: 'PROBLEM_SOLVING', pos: 'left-[0%] bottom-[2%]', delay: '0.6s' },
]

function HeroOrbit() {
  return (
    <div className="relative mx-auto aspect-square w-full max-w-[460px]">
      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full animate-spin-slow" aria-hidden>
        <circle
          cx="50"
          cy="50"
          r="36"
          fill="none"
          stroke="white"
          strokeOpacity="0.7"
          strokeWidth="0.6"
          strokeDasharray="2 2.4"
        />
      </svg>
      <div className="absolute left-1/2 top-1/2 w-[46%] -translate-x-1/2 -translate-y-1/2">
        <div className="absolute inset-[6%] rounded-full bg-white/25 blur-xl" aria-hidden />
        <Mascot className="relative w-full animate-float drop-shadow-[0_12px_18px_rgb(13_27_62/0.25)]" />
      </div>
      {ORBIT.map((o) => {
        const t = skillTheme(o.code)
        return (
          <Link
            key={o.code}
            to={`/products?skills=${o.code}`}
            className={`group absolute ${o.pos} flex w-[28%] animate-float-slow flex-col items-center gap-1.5 text-ink hover:text-ink`}
            style={{ animationDelay: o.delay }}
          >
            <span
              className="grid aspect-square w-full place-items-center rounded-full border-[6px] bg-white shadow-pop transition group-hover:scale-110 group-hover:animate-wiggle"
              style={{ borderColor: t.color, color: t.color }}
            >
              <SkillIcon code={o.code} className="h-[44%] w-[44%]" />
            </span>
            <span
              className="rounded-full bg-white px-3 py-0.5 text-[12.5px] font-bold shadow-card"
              style={{ color: t.color }}
            >
              {t.short}
            </span>
          </Link>
        )
      })}
    </div>
  )
}

const AGE_STYLES = [
  { bg: '#e4f5fc', ring: '#1fa6dd', emoji: '🧸' },
  { bg: '#fdebf2', ring: '#e8467c', emoji: '🎨' },
  { bg: '#fef3e0', ring: '#e08700', emoji: '🧩' },
  { bg: '#e6f6f1', ring: '#0f9e7a', emoji: '🤖' },
]

function AgeSection() {
  // số sản phẩm mỗi nhóm tuổi: lấy totalElements với size = 1
  const counts = useQueries({
    queries: AGE_GROUPS.map((g) => ({
      queryKey: ['products', { ageFrom: g.from, ageTo: g.to, size: 1 }],
      queryFn: () => catalogApi.products({ ageFrom: g.from, ageTo: g.to, size: 1 }),
      staleTime: 5 * 60_000,
    })),
  })
  return (
    <section className="container-page mt-12">
      <Reveal>
        <SectionHead
          kicker="Bé nhà bạn mấy tuổi?"
          title="Chọn theo độ tuổi của bé"
          link="/products"
          linkLabel="Tất cả độ tuổi"
        />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {AGE_GROUPS.map((g, i) => {
            const st = AGE_STYLES[i % AGE_STYLES.length]
            return (
              <Link
                key={g.label}
                to={`/products?ageFrom=${g.from}&ageTo=${g.to}`}
                className="group relative overflow-hidden rounded-[22px] border-2 p-5 text-ink transition hover:-translate-y-1 hover:text-ink hover:shadow-card-hover"
                style={{ background: st.bg, borderColor: st.ring }}
              >
                <span
                  className="absolute -right-6 -top-6 h-24 w-24 rounded-full opacity-20 transition group-hover:scale-125"
                  style={{ background: st.ring }}
                  aria-hidden
                />
                <span
                  className="grid h-14 w-14 place-items-center rounded-full border-4 bg-white text-[26px] group-hover:animate-wiggle"
                  style={{ borderColor: st.ring }}
                  aria-hidden
                >
                  {st.emoji}
                </span>
                <p className="mt-3 font-display text-[24px] font-extrabold leading-tight">{g.label}</p>
                <p className="text-[13.5px] font-semibold" style={{ color: st.ring }}>
                  {counts[i].data ? `${counts[i].data.totalElements} sản phẩm` : '…'}
                </p>
              </Link>
            )
          })}
        </div>
      </Reveal>
    </section>
  )
}

// dải xanh có mép giấy xé và hoạ tiết chấm (giống nền section "Chương trình" của stemplus)
function SkillSection() {
  const skills = useSkills()
  return (
    <section className="relative mt-16">
      <TornEdge color="var(--color-sky-deep)" seed={23} className="-mb-px" />
      <div className="dots-bg relative overflow-hidden bg-sky-deep py-12 text-white">
        <span className="absolute -left-10 top-10 h-40 w-40 rounded-full border-2 border-white/20" aria-hidden />
        <span className="absolute bottom-6 right-10 h-24 w-24 rotate-12 border-2 border-white/20" aria-hidden />
        <div className="container-page relative">
          <Reveal>
            <span className="section-kicker bg-white/15 text-sun">4 nhóm kỹ năng</span>
            <h2 className="section-title mt-3 text-white">Chọn theo nhóm kỹ năng</h2>
            <p className="mt-1 text-[14.5px] text-white/85">
              Bốn nhóm kỹ năng được dùng nhất quán trên toàn bộ website
            </p>
          </Reveal>
          <div className="mt-7 grid grid-cols-2 gap-4 lg:grid-cols-4">
            {(skills.data ?? []).map((s, i) => {
              const theme = skillTheme(s.code, s.name)
              return (
                <Reveal key={s.id} delay={i * 90}>
                  <Link
                    to={`/products?skills=${s.code}`}
                    className="group flex h-full flex-col items-center rounded-[22px] bg-white p-5 text-center text-ink shadow-pop transition hover:-translate-y-1.5 hover:text-ink"
                  >
                    <span
                      className="grid h-20 w-20 place-items-center rounded-full border-[5px] transition group-hover:rotate-6 group-hover:scale-105"
                      style={{ borderColor: theme.color, color: theme.color, background: theme.soft }}
                    >
                      <SkillIcon code={s.code} className="h-9 w-9" />
                    </span>
                    <p
                      className="mt-3 font-display text-[20px] font-extrabold leading-tight"
                      style={{ color: theme.color }}
                    >
                      {s.name}
                    </p>
                    <p className="mt-1 text-[13.5px] text-ink-muted">{SKILL_HINTS[s.code] ?? s.description}</p>
                  </Link>
                </Reveal>
              )
            })}
          </div>
        </div>
      </div>
      <TornEdge color="var(--color-sky-deep)" flip seed={41} className="-mt-px" />
    </section>
  )
}

const STEPS = [
  { title: 'Tạo hồ sơ bé', text: 'Tuổi và nhóm kỹ năng quan tâm', color: '#1fa6dd' },
  { title: 'Thêm đồ chơi', text: 'Món bé đã có hoặc mua qua BrainBlocks', color: '#7cc243' },
  { title: 'Xem lộ trình', text: 'Biểu đồ kỹ năng và gợi ý món tiếp theo', color: '#ffc233' },
]

function RoadmapSection() {
  return (
    <section className="container-page mt-16">
      <Reveal>
        <div className="relative overflow-hidden rounded-[28px] bg-secondary p-6 text-white sm:p-10">
          <span className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-primary/40" aria-hidden />
          <span className="absolute -bottom-20 left-1/3 h-44 w-44 rounded-full bg-sky/25" aria-hidden />
          <div className="relative grid items-center gap-8 lg:grid-cols-[1.1fr_1fr]">
            <div>
              <span className="section-kicker bg-white/10 text-sun">Lộ trình kỹ năng</span>
              <h2 className="section-title mt-3 text-white">Không biết mua gì tiếp theo cho bé?</h2>
              <p className="mt-3 max-w-lg text-[15px] text-[#BFC7DA]">
                Hồ sơ kỹ năng tổng hợp từ đồ chơi bé đã có, chỉ ra nhóm kỹ năng chưa được khai thác và gợi ý món tiếp
                theo, luôn kèm lý do vì sao phù hợp — không chỉ là sản phẩm bán chạy.
              </p>
              <ol className="relative mt-7 grid gap-4 sm:grid-cols-3">
                {/* đường nét đứt nối các bước */}
                <span
                  className="absolute left-[16%] right-[16%] top-6 hidden border-t-[3px] border-dashed border-white/30 sm:block"
                  aria-hidden
                />
                {STEPS.map((s, i) => (
                  <li
                    key={s.title}
                    className="relative flex items-start gap-3 sm:flex-col sm:items-center sm:text-center"
                  >
                    <span
                      className="grid h-12 w-12 shrink-0 place-items-center rounded-full border-4 border-secondary font-display text-[22px] font-extrabold text-ink"
                      style={{ background: s.color }}
                    >
                      {i + 1}
                    </span>
                    <span>
                      <span className="block font-bold text-white">{s.title}</span>
                      <span className="block text-[13px] text-[#BFC7DA]">{s.text}</span>
                    </span>
                  </li>
                ))}
              </ol>
              <ButtonLink to="/children" variant="accent" size="lg" className="mt-8">
                Xem lộ trình của bé
              </ButtonLink>
            </div>
            <HeroProfileCard />
          </div>
        </div>
      </Reveal>
    </section>
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
    <div className="relative rotate-[-1.5deg] rounded-[22px] bg-white p-6 text-ink shadow-pop transition hover:rotate-0">
      <span className="absolute -top-3 left-8 h-6 w-20 rotate-[-4deg] rounded-sm bg-sun/80" aria-hidden />
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
