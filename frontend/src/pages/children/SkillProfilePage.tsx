import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { childApi } from '@/api/endpoints'
import type { ChildProfile, SkillProfile } from '@/api/types'
import { ProductArt } from '@/components/product/ProductArt'
import { Pill, SkillBar } from '@/components/ui/Badges'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Modal'
import { EmptyState, ErrorState, PageLoader, Skeleton } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { useAddToCart } from '@/features/cart/useCart'
import { AddToyModal } from '@/features/children/AddToyModal'
import { ChildFormModal } from '@/features/children/ChildFormModal'
import { childKeys, invalidateChildData } from '@/features/children/keys'
import { averageImpact } from '@/features/skills/skillMath'
import { SkillTimelineChart } from '@/features/skills/SkillTimelineChart'
import { formatDate, formatDecimal, formatPrice, initials } from '@/lib/format'
import { skillTheme } from '@/lib/skills'

export default function SkillProfilePage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list })
  const child = useQuery({ queryKey: childKeys.detail(id), queryFn: () => childApi.get(id), enabled: id > 0 })
  const [editOpen, setEditOpen] = useState(false)

  if (child.isPending) return <PageLoader />
  if (child.isError) {
    return (
      <div className="container-page">
        {child.error.status === 404 ? (
          <EmptyState
            title="Không tìm thấy hồ sơ bé"
            action={<ButtonLink to="/children">Về danh sách hồ sơ</ButtonLink>}
          />
        ) : (
          <ErrorState message={child.error.message} onRetry={() => child.refetch()} />
        )}
      </div>
    )
  }

  return (
    <div className="container-page py-8">
      <nav className="text-[13px] text-ink-muted" aria-label="Breadcrumb">
        <Link to="/children" className="text-ink-muted hover:text-ink">
          Hồ sơ bé
        </Link>{' '}
        / <span className="text-ink-2">Bé {child.data.name}</span>
      </nav>

      {/* chuyển nhanh giữa các bé */}
      <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
        {children.data?.map((c) => (
          <Link
            key={c.id}
            to={`/children/${c.id}`}
            className={`flex shrink-0 items-center gap-2.5 rounded-full border-[1.5px] py-1.5 pl-1.5 pr-4 text-[14px] font-semibold ${
              c.id === id
                ? 'border-primary bg-primary-soft text-primary-hover'
                : 'border-line bg-surface text-ink-2 hover:text-ink'
            }`}
          >
            <span className="grid h-7 w-7 place-items-center rounded-full bg-surface text-[11px] font-bold text-primary">
              {initials(c.name)}
            </span>
            Bé {c.name} · {c.age} tuổi
          </Link>
        ))}
        <Link
          to="/children"
          className="flex shrink-0 items-center rounded-full border-[1.5px] border-dashed border-line-strong px-4 text-[14px] font-semibold text-ink-muted"
        >
          + Thêm hồ sơ bé
        </Link>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="min-w-0 space-y-6">
          <SkillMap child={child.data} />
          <ToyList child={child.data} />
          <Recommendations child={child.data} />
        </div>
        <aside className="space-y-4">
          <div className="card p-5">
            <p className="font-bold">Nhóm kỹ năng quan tâm</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {child.data.interestedSkills.length === 0 ? (
                <p className="text-[13.5px] text-ink-muted">Chưa chọn nhóm nào.</p>
              ) : (
                child.data.interestedSkills.map((s) => {
                  const t = skillTheme(s.code, s.name)
                  return (
                    <span
                      key={s.id}
                      className="rounded-full px-3 py-1 text-[13px] font-semibold"
                      style={{ background: t.soft, color: t.color }}
                    >
                      {s.name}
                    </span>
                  )
                })
              )}
            </div>
            {child.data.note && <p className="mt-4 text-[13.5px] text-ink-2">“{child.data.note}”</p>}
            <Button variant="secondary" block className="mt-5" onClick={() => setEditOpen(true)}>
              Chỉnh sửa hồ sơ bé
            </Button>
          </div>
          <p className="rounded-md bg-muted px-4 py-3 text-[12.5px] text-ink-muted">
            Chỉ số này chỉ mang tính <b>tham khảo khi chọn đồ chơi</b>, không đánh giá năng lực hay sự phát triển của
            trẻ.
          </p>
        </aside>
      </div>

      <ChildFormModal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        child={child.data}
        onDeleted={() => navigate('/children')}
      />
    </div>
  )
}

function SkillMap({ child }: { child: ChildProfile }) {
  const profile = useQuery({
    queryKey: childKeys.skillProfile(child.id),
    queryFn: () => childApi.skillProfile(child.id),
  })
  const timeline = useQuery({ queryKey: childKeys.timeline(child.id), queryFn: () => childApi.timeline(child.id) })

  if (profile.isPending) return <Skeleton className="h-80" />
  if (profile.isError) return <ErrorState message={profile.error.message} onRetry={() => profile.refetch()} />

  const p = profile.data
  return (
    <section className="card p-5 sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-[21px] font-bold tracking-[-0.02em]">Bản đồ kỹ năng của bé {child.name}</h1>
        <span className="text-[13px] text-ink-muted">
          Tổng hợp từ {p.totalProducts} món đồ chơi{p.updatedAt && ` · cập nhật ${formatDate(p.updatedAt)}`}
        </span>
      </div>

      {p.totalProducts === 0 ? (
        <EmptyState
          title="Chưa có dữ liệu kỹ năng"
          description="Thêm đồ chơi bé đã có, hoặc mua đồ chơi và gắn cho bé ở bước thanh toán — hồ sơ sẽ tự tổng hợp khi đơn được giao."
        />
      ) : (
        <>
          <div className="mt-5 grid gap-6 md:grid-cols-2">
            <div className="space-y-4">
              {p.skillScores.map((s) => (
                <SkillBar
                  key={s.skillId}
                  code={s.skillCode}
                  name={s.skillName}
                  value={averageImpact(s, p.totalProducts)}
                />
              ))}
              <p className="text-[12px] text-ink-faint">Mức tác động trung bình mỗi món lên từng nhóm (thang 0–10).</p>
            </div>
            <Highlights profile={p} />
          </div>
          <Distribution profile={p} />
          {timeline.data && timeline.data.points.length > 1 && (
            <div className="mt-7 border-t border-line pt-5">
              <p className="mb-3 font-bold">Lộ trình theo từng món đồ chơi</p>
              <SkillTimelineChart timeline={timeline.data} />
            </div>
          )}
        </>
      )}
    </section>
  )
}

function Highlights({ profile }: { profile: SkillProfile }) {
  const { strongestSkill: strong, weakestSkill: weak, totalProducts } = profile
  return (
    <div className="space-y-3">
      {strong && (
        <div className="rounded-md border border-line p-4">
          <Pill tone="green">Đang được chú trọng</Pill>
          <p className="mt-2 font-bold" style={{ color: skillTheme(strong.skillCode).color }}>
            {strong.skillName} · {formatDecimal(averageImpact(strong, totalProducts))}/10
          </p>
          <p className="mt-1 text-[13.5px] text-ink-muted">
            Chiếm {formatDecimal(strong.percentage)}% tổng tác động từ đồ chơi của bé.
          </p>
        </div>
      )}
      {weak && weak.skillId !== strong?.skillId && (
        <div className="rounded-md border border-line p-4">
          <Pill tone="amber">Chưa khai thác nhiều</Pill>
          <p className="mt-2 font-bold" style={{ color: skillTheme(weak.skillCode).color }}>
            {weak.skillName} · {formatDecimal(averageImpact(weak, totalProducts))}/10
          </p>
          <p className="mt-1 text-[13.5px] text-ink-muted">
            Chỉ chiếm {formatDecimal(weak.percentage)}% — xem gợi ý bên dưới để cân bằng lộ trình.
          </p>
        </div>
      )}
    </div>
  )
}

// phân bổ giữa các nhóm kỹ năng: thanh 100% chia theo percentage, có nhãn trực tiếp
function Distribution({ profile }: { profile: SkillProfile }) {
  const parts = profile.skillScores.filter((s) => s.percentage > 0)
  return (
    <div className="mt-7">
      <p className="mb-2.5 text-[14px] font-bold">Phân bổ giữa các nhóm kỹ năng</p>
      <div className="flex h-3.5 gap-[2px] overflow-hidden rounded-full" role="img" aria-label="Tỉ lệ phân bổ kỹ năng">
        {parts.map((s) => (
          <div
            key={s.skillId}
            title={`${s.skillName}: ${formatDecimal(s.percentage)}%`}
            style={{ width: `${s.percentage}%`, background: skillTheme(s.skillCode).color }}
          />
        ))}
      </div>
      <ul className="mt-2.5 flex flex-wrap gap-x-5 gap-y-1 text-[13px] text-ink-2">
        {profile.skillScores.map((s) => (
          <li key={s.skillId} className="flex items-center gap-1.5">
            <span
              className="h-2.5 w-2.5 rounded-full"
              style={{ background: skillTheme(s.skillCode).color }}
              aria-hidden
            />
            {s.skillName} <b className="tabular-nums">{formatDecimal(s.percentage)}%</b>
          </li>
        ))}
      </ul>
    </div>
  )
}

function ToyList({ child }: { child: ChildProfile }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const products = useQuery({ queryKey: childKeys.products(child.id), queryFn: () => childApi.products(child.id) })
  const [addOpen, setAddOpen] = useState(false)
  const [removing, setRemoving] = useState<{ id: number; name: string } | null>(null)
  const [showAll, setShowAll] = useState(false)
  const remove = useMutation({
    mutationFn: (childProductId: number) => childApi.removeProduct(child.id, childProductId),
    onSuccess: () => {
      invalidateChildData(queryClient, child.id)
      toast.success('Đã gỡ đồ chơi khỏi hồ sơ')
      setRemoving(null)
    },
    onError: (e) => toast.error(e.message),
  })
  const list = products.data ?? []
  const visible = showAll ? list : list.slice(0, 4)

  return (
    <section className="card p-5 sm:p-6">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-[18px] font-bold">Đồ chơi bé đã có ({list.length})</h2>
        <Button variant="secondary" size="sm" onClick={() => setAddOpen(true)}>
          + Tự thêm đồ chơi
        </Button>
      </div>
      {products.isPending ? (
        <Skeleton className="mt-4 h-24" />
      ) : list.length === 0 ? (
        <p className="mt-4 text-[14px] text-ink-muted">Chưa có đồ chơi nào trong hồ sơ.</p>
      ) : (
        <ul className="mt-4 divide-y divide-line">
          {visible.map((cp) => (
            <li key={cp.id} className="flex items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <Link to={`/products/${cp.productId}`} className="font-semibold text-ink hover:text-primary">
                  {cp.productName}
                </Link>
                <p className="text-[12.5px] text-ink-muted">
                  {cp.source === 'PURCHASED' ? 'Đã mua' : 'Tự thêm'} · {formatDate(cp.addedAt)}
                </p>
              </div>
              <button
                type="button"
                className="rounded-md px-2.5 py-1.5 text-[13px] font-semibold text-ink-muted hover:bg-danger-soft hover:text-danger"
                onClick={() => setRemoving({ id: cp.id, name: cp.productName })}
              >
                Gỡ
              </button>
            </li>
          ))}
        </ul>
      )}
      {list.length > 4 && (
        <button
          type="button"
          className="mt-2 text-[14px] font-semibold text-primary"
          onClick={() => setShowAll((v) => !v)}
        >
          {showAll ? 'Thu gọn' : `Xem cả ${list.length} món →`}
        </button>
      )}
      <AddToyModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        childId={child.id}
        ownedProductIds={list.map((p) => p.productId)}
      />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Gỡ đồ chơi khỏi hồ sơ?"
        message={
          <>
            <b>{removing?.name}</b> sẽ không còn được tính vào hồ sơ kỹ năng của bé.
          </>
        }
        confirmLabel="Gỡ khỏi hồ sơ"
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
        onClose={() => setRemoving(null)}
      />
    </section>
  )
}

function Recommendations({ child }: { child: ChildProfile }) {
  const recs = useQuery({
    queryKey: childKeys.recommendations(child.id),
    queryFn: () => childApi.recommendations(child.id, 4),
  })
  const addToCart = useAddToCart()
  return (
    <section className="card p-5 sm:p-6">
      <h2 className="text-[18px] font-bold">Đề xuất tiếp theo</h2>
      <p className="text-[13.5px] text-ink-muted">
        Dựa trên tuổi của bé, nhóm kỹ năng đang thiếu và nhóm phụ huynh quan tâm
      </p>
      {recs.isPending ? (
        <Skeleton className="mt-4 h-40" />
      ) : recs.isError ? (
        <ErrorState message={recs.error.message} onRetry={() => recs.refetch()} />
      ) : recs.data.length === 0 ? (
        <p className="mt-4 text-[14px] text-ink-muted">Chưa có sản phẩm còn hàng phù hợp độ tuổi của bé.</p>
      ) : (
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {recs.data.map((r) => (
            <article key={r.productId} className="flex flex-col overflow-hidden rounded-md border border-line">
              <Link to={`/products/${r.productId}`} className="block">
                <ProductArt url={r.thumbnailUrl} name={r.name} className="!aspect-[16/7]" />
              </Link>
              <div className="flex flex-1 flex-col p-4">
                <Link to={`/products/${r.productId}`} className="font-semibold text-ink hover:text-primary">
                  {r.name}
                </Link>
                <p className="mt-0.5 text-[15px] font-bold">{formatPrice(r.price)}</p>
                <div className="mt-2.5 rounded-sm bg-primary-soft px-3 py-2 text-[13px] text-ink-2">
                  <b className="text-primary-hover">Vì sao: </b>
                  {r.reasons.join('; ')}.
                </div>
                <div className="mt-auto flex gap-2 pt-3">
                  <Button
                    size="sm"
                    className="flex-1"
                    loading={addToCart.isPending && addToCart.variables?.productId === r.productId}
                    onClick={() => addToCart.mutate({ productId: r.productId, quantity: 1 })}
                  >
                    Thêm vào giỏ
                  </Button>
                  <ButtonLink to={`/products/${r.productId}`} size="sm" variant="secondary">
                    Chi tiết
                  </ButtonLink>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
