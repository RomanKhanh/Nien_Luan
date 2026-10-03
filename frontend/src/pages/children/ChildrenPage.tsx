import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { childApi } from '@/api/endpoints'
import type { ChildProfile } from '@/api/types'
import { SkillIcon } from '@/components/decor/Decor'
import { Button, ButtonLink } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/States'
import { ChildFormModal } from '@/features/children/ChildFormModal'
import { childKeys } from '@/features/children/keys'
import { formatDate, initials } from '@/lib/format'
import { GENDER } from '@/lib/labels'
import { skillLevel, skillTheme } from '@/lib/skills'
import { AccountShell } from '../account/AccountShell'

export default function ChildrenPage() {
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list })
  const [editing, setEditing] = useState<ChildProfile | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  const openForm = (child: ChildProfile | null) => {
    setEditing(child)
    setFormOpen(true)
  }

  // nằm trong khung tài khoản (menu bên trái) như Đơn hàng, Thông báo...
  return (
    <AccountShell>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="h1">Hồ sơ của các bé</h1>
          <p className="mt-1 text-[14px] text-ink-muted">
            Mỗi hồ sơ có lộ trình kỹ năng riêng và gợi ý đồ chơi phù hợp.
          </p>
        </div>
        {children.data && children.data.length > 0 && (
          <Button variant="accent" onClick={() => openForm(null)}>
            + Thêm hồ sơ bé
          </Button>
        )}
      </div>
      <div>
        <div>
          {children.isPending ? (
            <div className="grid gap-4 md:grid-cols-2">
              <Skeleton className="h-56" />
              <Skeleton className="h-56" />
            </div>
          ) : children.isError ? (
            <ErrorState message={children.error.message} onRetry={() => children.refetch()} />
          ) : children.data.length === 0 ? (
            <div className="card">
              <EmptyState
                icon="🧒"
                title="Chưa có hồ sơ bé nào"
                description="Tạo hồ sơ để BrainBlocks tổng hợp lộ trình kỹ năng và gợi ý đồ chơi phù hợp cho bé."
                action={<Button onClick={() => openForm(null)}>Tạo hồ sơ đầu tiên</Button>}
              />
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {children.data.map((c, i) => (
                <ChildCard
                  key={c.id}
                  child={c}
                  color={CARD_COLORS[i % CARD_COLORS.length]}
                  onEdit={() => openForm(c)}
                />
              ))}
            </div>
          )}
        </div>

        <ChildFormModal open={formOpen} onClose={() => setFormOpen(false)} child={editing} />
      </div>
    </AccountShell>
  )
}

const CARD_COLORS = ['#1fa6dd', '#e8467c', '#7cc243', '#e08700']

function ChildCard({ child, color, onEdit }: { child: ChildProfile; color: string; onEdit: () => void }) {
  const profile = useQuery({
    queryKey: childKeys.skillProfile(child.id),
    queryFn: () => childApi.skillProfile(child.id),
  })
  const total = profile.data?.totalProducts ?? 0
  return (
    <article className="card relative flex flex-col overflow-hidden p-5 transition hover:-translate-y-1 hover:shadow-card-hover">
      <span
        className="absolute -right-10 -top-10 h-32 w-32 rounded-full opacity-15"
        style={{ background: color }}
        aria-hidden
      />
      <div className="relative flex items-center gap-3">
        <span
          className="grid h-14 w-14 place-items-center rounded-full border-4 border-white font-display text-[20px] font-extrabold text-white shadow-card"
          style={{ background: color }}
        >
          {initials(child.name)}
        </span>
        <div>
          <h2 className="font-display text-[22px] font-extrabold leading-tight">Bé {child.name}</h2>
          <p className="text-[13px] text-ink-muted">
            {[child.gender && GENDER[child.gender], `${child.age} tuổi`, `sinh ${formatDate(child.birthDate)}`]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </div>
      </div>
      <div className="mt-5 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {(profile.data?.skillScores ?? []).slice(0, 4).map((s) => {
          const theme = skillTheme(s.skillCode, s.skillName)
          return (
            <div
              key={s.skillId}
              className="rounded-2xl px-2 py-2.5 text-center"
              style={{ background: theme.soft, color: theme.color }}
            >
              <SkillIcon code={s.skillCode} className="mx-auto h-5 w-5" />
              <p className="mt-1 text-[13.5px] font-extrabold leading-tight sm:min-h-[2.5em]" style={{ color: theme.color }}>
                {skillLevel(s.score)}
              </p>
              <p className="truncate text-[12px] font-semibold text-ink-2">{theme.short || s.skillName}</p>
            </div>
          )
        })}
      </div>
      <p className="mt-4 text-[13px] text-ink-muted">
        {total} đồ chơi
        {child.interestedSkills.length > 0 && ` · quan tâm: ${child.interestedSkills.map((s) => s.name).join(', ')}`}
      </p>
      <div className="mt-4 flex gap-2.5">
        <ButtonLink to={`/children/${child.id}`} variant="soft" className="flex-1">
          Xem lộ trình
        </ButtonLink>
        <Button variant="secondary" onClick={onEdit}>
          Sửa
        </Button>
      </div>
    </article>
  )
}
