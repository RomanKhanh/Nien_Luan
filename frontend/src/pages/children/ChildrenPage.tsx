import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { childApi } from '@/api/endpoints'
import type { ChildProfile } from '@/api/types'
import { Button, ButtonLink } from '@/components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '@/components/ui/States'
import { ChildFormModal } from '@/features/children/ChildFormModal'
import { childKeys } from '@/features/children/keys'
import { averageImpact } from '@/features/skills/skillMath'
import { formatDate, formatDecimal, initials } from '@/lib/format'
import { GENDER } from '@/lib/labels'
import { skillTheme } from '@/lib/skills'

export default function ChildrenPage() {
  const children = useQuery({ queryKey: childKeys.list, queryFn: childApi.list })
  const [editing, setEditing] = useState<ChildProfile | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  const openForm = (child: ChildProfile | null) => {
    setEditing(child)
    setFormOpen(true)
  }

  return (
    <div className="container-page py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="h1">Hồ sơ của các bé</h1>
          <p className="mt-1 text-[14px] text-ink-muted">
            Mỗi hồ sơ có lộ trình kỹ năng riêng và gợi ý đồ chơi phù hợp.
          </p>
        </div>
        {children.data && children.data.length > 0 && <Button onClick={() => openForm(null)}>+ Thêm hồ sơ bé</Button>}
      </div>

      <div className="mt-6">
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
              icon="☺"
              title="Chưa có hồ sơ bé nào"
              description="Tạo hồ sơ để BrainBlocks tổng hợp lộ trình kỹ năng và gợi ý đồ chơi phù hợp cho bé."
              action={<Button onClick={() => openForm(null)}>Tạo hồ sơ đầu tiên</Button>}
            />
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {children.data.map((c) => (
              <ChildCard key={c.id} child={c} onEdit={() => openForm(c)} />
            ))}
          </div>
        )}
      </div>

      <ChildFormModal open={formOpen} onClose={() => setFormOpen(false)} child={editing} />
    </div>
  )
}

function ChildCard({ child, onEdit }: { child: ChildProfile; onEdit: () => void }) {
  const profile = useQuery({
    queryKey: childKeys.skillProfile(child.id),
    queryFn: () => childApi.skillProfile(child.id),
  })
  const total = profile.data?.totalProducts ?? 0
  return (
    <article className="card flex flex-col p-5">
      <div className="flex items-center gap-3">
        <span className="grid h-12 w-12 place-items-center rounded-full bg-primary-soft text-[15px] font-bold text-primary">
          {initials(child.name)}
        </span>
        <div>
          <h2 className="text-[17px] font-bold">Bé {child.name}</h2>
          <p className="text-[13px] text-ink-muted">
            {[child.gender && GENDER[child.gender], `${child.age} tuổi`, `sinh ${formatDate(child.birthDate)}`]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </div>
      </div>
      <div className="mt-5 grid grid-cols-4 gap-2">
        {(profile.data?.skillScores ?? []).slice(0, 4).map((s) => {
          const theme = skillTheme(s.skillCode, s.skillName)
          return (
            <div key={s.skillId} className="rounded-md px-2 py-2.5 text-center" style={{ background: theme.soft }}>
              <p className="text-[18px] font-bold" style={{ color: theme.color }}>
                {formatDecimal(averageImpact(s, total))}
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
