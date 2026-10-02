import { useQuery } from '@tanstack/react-query'
import { childApi } from '@/api/endpoints'
import type { ChildProfile } from '@/api/types'
import { Skeleton } from '@/components/ui/States'
import { formatDecimal } from '@/lib/format'
import { skillLevel, skillTheme } from '@/lib/skills'

// một nhóm chiếm từ tỉ lệ này trong tổng mức tăng (và có từ 2 món) thì nhắc cân bằng
const LOPSIDED_SHARE = 0.75

/**
 * Hồ sơ kỹ năng của bé trước / sau khi nhận các món gán cho bé trong đơn này (bước thanh toán).
 * Các món dồn vào một nhóm thì gợi ý nhẹ nhóm đang thấp nhất để lộ trình cân bằng hơn.
 */
export function ChildBundlePreview({ child, productIds }: { child: ChildProfile; productIds: number[] }) {
  const ids = [...productIds].sort((a, b) => a - b)
  const preview = useQuery({
    queryKey: ['skill-preview-bundle', child.id, ids],
    queryFn: () => childApi.skillPreviewBundle(child.id, ids),
  })

  if (preview.isPending) return <Skeleton className="h-24" />
  if (preview.isError) return null

  const { gains, alreadyOwnedProductIds } = preview.data
  const totalGain = gains.reduce((sum, g) => sum + g.gain, 0)
  const top = [...gains].sort((a, b) => b.gain - a.gain)[0]
  const lowest = [...gains].sort((a, b) => a.projectedScore - b.projectedScore)[0]
  const newCount = ids.length - alreadyOwnedProductIds.length
  const lopsided =
    newCount >= 2 && top && totalGain > 0 && top.gain / totalGain >= LOPSIDED_SHARE && lowest?.skillId !== top.skillId

  return (
    <div className="rounded-2xl border-2 border-dashed border-sky/50 bg-sky-soft/40 p-4">
      <p className="text-[14px] font-bold">
        Hồ sơ của bé {child.name} sau khi nhận {newCount} món
      </p>
      {totalGain <= 0 ? (
        <p className="mt-1 text-[13px] text-ink-muted">
          {newCount === 0 ? 'Bé đã có các món này trong hồ sơ.' : 'Các món này không làm thay đổi hồ sơ của bé.'}
        </p>
      ) : (
        <ul className="mt-2.5 grid gap-x-6 gap-y-1.5 sm:grid-cols-2">
          {gains.map((g) => {
            const theme = skillTheme(g.skillCode, g.skillName)
            const from = skillLevel(g.currentScore)
            const to = skillLevel(g.projectedScore)
            return (
              <li key={g.skillId} className="flex items-baseline justify-between gap-2 text-[13px]">
                <span className="shrink-0 whitespace-nowrap font-semibold" style={{ color: theme.color }}>
                  {theme.short || g.skillName}
                </span>
                <span className="text-right text-ink-2">
                  {from === to ? from : `${from} → ${to}`}
                  {g.gain > 0 && <b className="ml-1.5 tabular-nums">+{formatDecimal(g.gain)}</b>}
                </span>
              </li>
            )
          })}
        </ul>
      )}
      {alreadyOwnedProductIds.length > 0 && newCount > 0 && (
        <p className="mt-2 text-[12px] text-ink-muted">
          {alreadyOwnedProductIds.length} món bé đã có nên không tính thêm.
        </p>
      )}
      {lopsided && (
        <p className="mt-2.5 rounded-xl bg-[#fff7e0] px-3 py-2 text-[12.5px] text-ink-2">
          💡 Các món cho bé đều thiên về <b>{top.skillName}</b>. Nếu muốn lộ trình cân bằng hơn, có thể cân nhắc thêm
          một món nhóm <b>{lowest.skillName}</b>.
        </p>
      )}
    </div>
  )
}
