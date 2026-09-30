import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { SkillTimeline } from '@/api/types'
import { formatDate, formatDecimal } from '@/lib/format'
import { skillTheme } from '@/lib/skills'
import { averageImpact } from './skillMath'

/**
 * Biểu đồ lộ trình: mỗi mốc là một lần bé có thêm đồ chơi, giá trị là mức tác động trung bình (0-10)
 * cộng dồn tới mốc đó. Màu theo nhóm kỹ năng cố định (đã kiểm tra bằng validate_palette của dataviz).
 */
export function SkillTimelineChart({ timeline }: { timeline: SkillTimeline }) {
  const skills = timeline.points[0]?.skillScores ?? []
  const data = timeline.points.map((p, i) => {
    const row: Record<string, number | string> = { index: i + 1, product: p.productName, date: p.addedAt }
    p.skillScores.forEach((s) => (row[s.skillCode] = averageImpact(s, p.totalProducts)))
    return row
  })

  return (
    <div>
      <div className="h-64 w-full">
        <ResponsiveContainer>
          <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: -16 }}>
            <CartesianGrid stroke="#EDEBE6" vertical={false} />
            <XAxis
              dataKey="index"
              tickFormatter={(v) => `Món ${v}`}
              tick={{ fontSize: 12, fill: '#6B6862' }}
              axisLine={{ stroke: '#E1DED7' }}
              tickLine={false}
            />
            <YAxis
              domain={[0, 10]}
              ticks={[0, 2, 4, 6, 8, 10]}
              tick={{ fontSize: 12, fill: '#6B6862' }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip content={<TimelineTooltip />} cursor={{ stroke: '#A9A59D', strokeDasharray: '3 3' }} />
            <Legend iconType="plainline" wrapperStyle={{ fontSize: 13, color: '#3C3A36' }} />
            {skills.map((s) => (
              <Line
                key={s.skillCode}
                type="monotone"
                dataKey={s.skillCode}
                name={s.skillName}
                stroke={skillTheme(s.skillCode).color}
                strokeWidth={2}
                dot={{ r: 4, strokeWidth: 2, stroke: '#fff', fill: skillTheme(s.skillCode).color }}
                activeDot={{ r: 5, strokeWidth: 2, stroke: '#fff' }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <details className="mt-3 text-[13px]">
        <summary className="cursor-pointer font-semibold text-ink-muted">Xem dạng bảng</summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-left">
            <thead className="text-ink-muted">
              <tr>
                <th className="py-1.5 pr-3 font-semibold">Mốc</th>
                <th className="py-1.5 pr-3 font-semibold">Đồ chơi</th>
                {skills.map((s) => (
                  <th key={s.skillCode} className="py-1.5 pr-3 font-semibold">
                    {s.skillName}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.index} className="border-t border-line">
                  <td className="py-1.5 pr-3">{formatDate(String(row.date))}</td>
                  <td className="py-1.5 pr-3">{row.product}</td>
                  {skills.map((s) => (
                    <td key={s.skillCode} className="py-1.5 pr-3 tabular-nums">
                      {formatDecimal(Number(row[s.skillCode]))}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}

interface TooltipPayload {
  payload: Record<string, number | string>
  dataKey: string
  name: string
  value: number
  color: string
}

function TimelineTooltip({ active, payload }: { active?: boolean; payload?: TooltipPayload[] }) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <div className="max-w-[240px] rounded-md border border-line bg-surface px-3.5 py-2.5 text-[12.5px] shadow-pop">
      <p className="font-bold text-ink">
        Món {row.index}: {row.product}
      </p>
      <p className="mb-1.5 text-ink-muted">{formatDate(String(row.date))}</p>
      {[...payload]
        .sort((a, b) => b.value - a.value)
        .map((p) => (
          <p key={p.dataKey} className="flex items-center justify-between gap-4 text-ink-2">
            <span className="flex items-center gap-1.5">
              <span className="h-0.5 w-3 rounded" style={{ background: p.color }} aria-hidden />
              {p.name}
            </span>
            <b className="tabular-nums text-ink">{formatDecimal(p.value)}</b>
          </p>
        ))}
    </div>
  )
}
