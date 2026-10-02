import type { SkillGain } from '@/api/types'
import { formatDecimal } from '@/lib/format'
import { skillLevel, skillTheme } from '@/lib/skills'

const MIN_NOTABLE_GAIN = 0.5
const MAX_CHIPS = 3

/**
 * Mức tăng điểm hồ sơ kỹ năng của bé nếu thêm một sản phẩm, dạng chip "+2,5 Sáng tạo".
 * Hiện cả lúc chuyển mức (vd Mới bắt đầu → Đang phát triển) để phụ huynh hiểu con số.
 *
 * compact (thẻ gợi ý): chỉ các nhóm tăng từ 0,5, tối đa 3 chip.
 * Đầy đủ (trang sản phẩm): mọi nhóm sản phẩm có tác động, nhóm tăng ít làm nhạt kèm ghi chú,
 * để phụ huynh không hiểu nhầm là món không giúp gì cho nhóm đó.
 */
export function SkillGainChips({ gains, compact = true }: { gains: SkillGain[]; compact?: boolean }) {
  const sorted = [...gains].sort((a, b) => b.gain - a.gain)
  let shown = sorted
  if (compact) {
    // món nào cũng chỉ tăng ít thì vẫn giữ nhóm tăng nhiều nhất
    const notable = sorted.filter((g) => g.gain >= MIN_NOTABLE_GAIN).slice(0, MAX_CHIPS)
    shown = notable.length > 0 ? notable : sorted.filter((g) => g.gain > 0).slice(0, 1)
  }
  if (shown.length === 0) return null
  const hasSmall = !compact && shown.some((g) => g.gain < MIN_NOTABLE_GAIN)

  return (
    <div>
      <ul className="flex flex-wrap gap-1.5">
        {shown.map((g) => {
          const theme = skillTheme(g.skillCode, g.skillName)
          const from = skillLevel(g.currentScore)
          const to = skillLevel(g.projectedScore)
          const small = g.gain < MIN_NOTABLE_GAIN
          return (
            <li
              key={g.skillId}
              className={`rounded-full px-2.5 py-1 text-[12.5px] font-bold ${small ? 'opacity-60' : ''}`}
              style={{ background: theme.soft, color: theme.color }}
              title={`${g.skillName}: ${formatDecimal(g.currentScore)} → ${formatDecimal(g.projectedScore)}`}
            >
              +{formatDecimal(g.gain)} {theme.short || g.skillName}
              {from !== to && <span className="font-semibold"> · lên {to.toLowerCase()}</span>}
            </li>
          )
        })}
      </ul>
      {hasSmall && (
        <p className="mt-1.5 text-[12px] text-ink-muted">
          Nhóm màu nhạt: bé đã có nhiều đồ chơi nhóm này nên món này bổ sung thêm ít.
        </p>
      )}
    </div>
  )
}
