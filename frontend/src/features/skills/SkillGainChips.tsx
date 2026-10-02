import type { SkillGain } from '@/api/types'
import { formatDecimal } from '@/lib/format'
import { skillLevel, skillTheme } from '@/lib/skills'

const MIN_NOTABLE_GAIN = 0.5
const MAX_CHIPS = 3

/**
 * Mức tăng điểm hồ sơ kỹ năng của bé nếu thêm một sản phẩm, dạng chip "+2,5 Sáng tạo".
 * Hiện cả lúc chuyển mức (vd Mới bắt đầu → Đang phát triển) để phụ huynh hiểu con số.
 */
export function SkillGainChips({ gains }: { gains: SkillGain[] }) {
  const sorted = gains.filter((g) => g.gain > 0).sort((a, b) => b.gain - a.gain)
  // mức tăng dưới 0,5 chỉ làm rối; món nào cũng chỉ tăng ít thì vẫn giữ nhóm tăng nhiều nhất
  const notable = sorted.filter((g) => g.gain >= MIN_NOTABLE_GAIN).slice(0, MAX_CHIPS)
  const shown = notable.length > 0 ? notable : sorted.slice(0, 1)
  if (shown.length === 0) return null
  return (
    <ul className="flex flex-wrap gap-1.5">
      {shown.map((g) => {
        const theme = skillTheme(g.skillCode, g.skillName)
        const from = skillLevel(g.currentScore)
        const to = skillLevel(g.projectedScore)
        return (
          <li
            key={g.skillId}
            className="rounded-full px-2.5 py-1 text-[12.5px] font-bold"
            style={{ background: theme.soft, color: theme.color }}
            title={`${g.skillName}: ${formatDecimal(g.currentScore)} → ${formatDecimal(g.projectedScore)}`}
          >
            +{formatDecimal(g.gain)} {theme.short || g.skillName}
            {from !== to && <span className="font-semibold"> · lên {to.toLowerCase()}</span>}
          </li>
        )
      })}
    </ul>
  )
}
