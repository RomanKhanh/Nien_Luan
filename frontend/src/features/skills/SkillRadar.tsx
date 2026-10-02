import { PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart, ResponsiveContainer, Tooltip } from 'recharts'
import { formatDecimal } from '@/lib/format'
import { skillLevel, skillTheme } from '@/lib/skills'

export interface RadarPoint {
  code: string
  name: string
  current: number
  // điểm nếu thêm món đang xem; không có thì chỉ vẽ hồ sơ hiện tại
  projected?: number
}

const PRIMARY = '#5b3df5'
const CONTEXT = '#a9a59d'

/**
 * "Hình dạng" bộ đồ chơi của bé trên 4 trục kỹ năng (thang 0-10).
 * Kiểu nhấn mạnh: bình thường hồ sơ hiện tại vẽ màu primary; khi đang xem một món thì hồ sơ hiện tại lùi về xám
 * làm nền, hồ sơ dự kiến vẽ primary nét đứt. Bảng giá trị nằm ở thanh mức kỹ năng và tooltip.
 */
export function SkillRadar({ points, projectedLabel }: { points: RadarPoint[]; projectedLabel?: string }) {
  const comparing = points.some((p) => p.projected !== undefined)
  const data = points.map((p) => ({
    ...p,
    axis: skillTheme(p.code, p.name).short || p.name,
    projected: p.projected ?? p.current,
  }))

  return (
    <div>
      {/* lề ngang rộng để nhãn trục trái/phải ("Sáng tạo") không bị cắt */}
      <div className="h-[250px] w-full" role="img" aria-label={radarSummary(points, projectedLabel)}>
        <ResponsiveContainer>
          <RadarChart data={data} outerRadius="80%" margin={{ top: 16, right: 52, bottom: 16, left: 52 }}>
            <PolarGrid stroke="#e1ded7" />
            <PolarAngleAxis dataKey="axis" tick={{ fontSize: 12.5, fill: '#3c3a36', fontWeight: 600 }} />
            <PolarRadiusAxis domain={[0, 10]} tickCount={6} tick={false} axisLine={false} />
            <Radar
              name="Hiện tại"
              dataKey="current"
              stroke={comparing ? CONTEXT : PRIMARY}
              strokeWidth={2}
              fill={comparing ? CONTEXT : PRIMARY}
              fillOpacity={comparing ? 0.12 : 0.18}
              dot={{ r: 4, strokeWidth: 2, stroke: '#fff', fill: comparing ? CONTEXT : PRIMARY }}
              isAnimationActive={false}
            />
            {comparing && (
              <Radar
                name={projectedLabel ?? 'Nếu thêm món này'}
                dataKey="projected"
                stroke={PRIMARY}
                strokeWidth={2}
                strokeDasharray="5 4"
                fill={PRIMARY}
                fillOpacity={0.12}
                dot={{ r: 4, strokeWidth: 2, stroke: '#fff', fill: PRIMARY }}
                isAnimationActive={false}
              />
            )}
            <Tooltip content={<RadarTooltip comparing={comparing} />} />
          </RadarChart>
        </ResponsiveContainer>
      </div>
      {/* chú thích HTML dưới biểu đồ, không đè lên nhãn trục dưới */}
      {comparing && (
        <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 text-[12.5px] text-ink-2">
          <li className="flex items-center gap-1.5">
            <svg width="18" height="4" aria-hidden>
              <line x1="0" y1="2" x2="18" y2="2" stroke={CONTEXT} strokeWidth="2" />
            </svg>
            Hiện tại
          </li>
          <li className="flex items-center gap-1.5">
            <svg width="18" height="4" aria-hidden>
              <line x1="0" y1="2" x2="18" y2="2" stroke={PRIMARY} strokeWidth="2" strokeDasharray="5 4" />
            </svg>
            {projectedLabel ?? 'Nếu thêm món này'}
          </li>
        </ul>
      )}
    </div>
  )
}

function RadarTooltip({
  active,
  payload,
  comparing,
}: {
  active?: boolean
  payload?: { payload: RadarPoint & { axis: string } }[]
  comparing: boolean
}) {
  const p = payload?.[0]?.payload
  if (!active || !p) return null
  return (
    <div className="rounded-xl border border-line bg-surface px-3 py-2 text-[12.5px] text-ink shadow-pop">
      <p className="font-bold">{p.name}</p>
      <p className="text-ink-2">
        Hiện tại: {skillLevel(p.current)} ({formatDecimal(p.current)})
      </p>
      {comparing && p.projected !== undefined && p.projected !== p.current && (
        <p className="font-semibold text-ink">
          Nếu thêm: {skillLevel(p.projected)} ({formatDecimal(p.projected)})
        </p>
      )}
    </div>
  )
}

// mô tả bằng chữ cho trình đọc màn hình
function radarSummary(points: RadarPoint[], projectedLabel?: string) {
  const now = points.map((p) => `${p.name} ${skillLevel(p.current)}`).join(', ')
  const changed = points.filter((p) => p.projected !== undefined && p.projected > p.current)
  if (changed.length === 0) return `Hồ sơ kỹ năng hiện tại: ${now}`
  return `Hồ sơ kỹ năng hiện tại: ${now}. ${projectedLabel ?? 'Nếu thêm món này'}: ${changed
    .map((p) => `${p.name} lên ${skillLevel(p.projected!)}`)
    .join(', ')}`
}
