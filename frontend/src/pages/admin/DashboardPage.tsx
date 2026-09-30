import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { adminApi } from '@/api/endpoints'
import type { AdminStats, OrderStatus } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { OrderStatusBadge } from '@/components/ui/Badges'
import { ErrorState, PageLoader } from '@/components/ui/States'
import { useSkills } from '@/features/catalog/queries'
import { formatDate, formatPrice } from '@/lib/format'
import { ORDER_STATUS } from '@/lib/labels'
import { skillTheme } from '@/lib/skills'

export default function DashboardPage() {
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: adminApi.stats })
  return (
    <>
      <AdminHeader title="Tổng quan" description="Số liệu cửa hàng, đơn hàng và mức quan tâm theo nhóm kỹ năng." />
      {stats.isPending ? (
        <PageLoader />
      ) : stats.isError ? (
        <ErrorState message={stats.error.message} onRetry={() => stats.refetch()} />
      ) : (
        <Dashboard s={stats.data} />
      )}
    </>
  )
}

function Dashboard({ s }: { s: AdminStats }) {
  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Tile
          label="Khách hàng"
          value={s.totalCustomers.toLocaleString('vi-VN')}
          sub={`+${s.newCustomersLast30Days} trong 30 ngày`}
          to="/admin/users"
          icon="👥"
          color="#1fa6dd"
        />
        <Tile
          label="Sản phẩm đang bán"
          value={s.activeProducts.toLocaleString('vi-VN')}
          sub={s.lowStockProducts > 0 ? `${s.lowStockProducts} sản phẩm sắp hết hàng` : 'Tồn kho ổn định'}
          warn={s.lowStockProducts > 0}
          to="/admin/products"
          icon="🧸"
          color="#e8467c"
        />
        <Tile
          label="Tổng đơn hàng"
          value={s.totalOrders.toLocaleString('vi-VN')}
          sub={`${s.ordersByStatus.PENDING} đơn chờ xác nhận`}
          to="/admin/orders"
          icon="📦"
          color="#e08700"
        />
        <Tile label="Doanh thu (đơn đã giao)" value={formatPrice(s.deliveredRevenue)} icon="💰" color="#0f9e7a" />
      </div>

      <div className="grid gap-5 xl:grid-cols-[1.6fr_1fr]">
        <Panel title="Đơn hàng 14 ngày gần nhất" note="Không tính đơn đã huỷ">
          <DailyOrdersChart data={s.dailyOrders} />
        </Panel>
        <Panel title="Đơn theo trạng thái">
          <ul className="space-y-3">
            {(Object.keys(ORDER_STATUS) as OrderStatus[]).map((status) => {
              const count = s.ordersByStatus[status] ?? 0
              const pct = s.totalOrders ? (count / s.totalOrders) * 100 : 0
              return (
                <li key={status}>
                  <div className="mb-1 flex items-center justify-between text-[13.5px]">
                    <OrderStatusBadge status={status} />
                    <b className="tabular-nums">{count}</b>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                  </div>
                </li>
              )
            })}
          </ul>
        </Panel>
      </div>

      <div className="grid gap-5 xl:grid-cols-3">
        <Panel title="Sản phẩm bán chạy">
          {s.topProducts.length === 0 ? (
            <Empty>Chưa có đơn hàng.</Empty>
          ) : (
            <ol className="space-y-2.5 text-[14px]">
              {s.topProducts.map((p, i) => (
                <li key={p.productId} className="flex items-center gap-3">
                  <span
                    className={`grid h-7 w-7 shrink-0 place-items-center rounded-full font-display text-[14px] font-extrabold ${
                      i === 0 ? 'bg-sun text-ink' : i === 1 ? 'bg-sky-soft text-sky-deep' : 'bg-muted text-ink-2'
                    }`}
                  >
                    {i + 1}
                  </span>
                  <span className="min-w-0 flex-1 truncate">{p.productName}</span>
                  <span className="shrink-0 text-ink-muted tabular-nums">{p.quantity} sp</span>
                </li>
              ))}
            </ol>
          )}
        </Panel>
        <SkillInterestPanel s={s} />
        <Panel title="Chatbot tư vấn">
          <p className="text-[28px] font-extrabold">{s.chatQuestions.toLocaleString('vi-VN')}</p>
          <p className="text-[13px] text-ink-muted">câu hỏi đã gửi đến chatbot</p>
          {s.chatTopics.length === 0 ? (
            <Empty>Chưa có dữ liệu chủ đề tư vấn (chatbot chưa được triển khai).</Empty>
          ) : (
            <ul className="mt-3 space-y-1.5 text-[14px]">
              {s.chatTopics.map((t) => (
                <li key={t.topic} className="flex justify-between">
                  <span>{t.topic}</span>
                  <b>{t.total}</b>
                </li>
              ))}
            </ul>
          )}
          {s.pendingComplaints > 0 && (
            <Link
              to="/admin/complaints"
              className="mt-4 block rounded-md bg-warning-soft px-3 py-2.5 text-[13.5px] font-semibold text-[#B45309]"
            >
              {s.pendingComplaints} khiếu nại / yêu cầu đang chờ tiếp nhận →
            </Link>
          )}
        </Panel>
      </div>
    </div>
  )
}

function SkillInterestPanel({ s }: { s: AdminStats }) {
  const skills = useSkills()
  const max = Math.max(1, ...s.skillInterests.map((i) => i.children))
  const codeOf = (id: number) => skills.data?.find((k) => k.id === id)?.code ?? ''
  return (
    <Panel title="Nhóm kỹ năng phụ huynh quan tâm" note="Số hồ sơ bé chọn quan tâm">
      {s.skillInterests.length === 0 ? (
        <Empty>Chưa có hồ sơ bé nào chọn nhóm quan tâm.</Empty>
      ) : (
        <ul className="space-y-3">
          {s.skillInterests.map((i) => {
            const theme = skillTheme(codeOf(i.skillId), i.skillName)
            return (
              <li key={i.skillId}>
                <div className="mb-1 flex justify-between text-[13.5px]">
                  <span className="font-semibold text-ink-2">{i.skillName}</span>
                  <b className="tabular-nums">{i.children}</b>
                </div>
                <div className="h-2 overflow-hidden rounded-full" style={{ background: theme.soft }}>
                  <div
                    className="h-full rounded-full"
                    style={{ width: `${(i.children / max) * 100}%`, background: theme.color }}
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Panel>
  )
}

function DailyOrdersChart({ data }: { data: AdminStats['dailyOrders'] }) {
  const rows = data.map((d) => ({ ...d, label: formatDate(d.date).slice(0, 5), amount: Number(d.amount) }))
  return (
    <div className="h-60">
      <ResponsiveContainer>
        <BarChart data={rows} margin={{ top: 8, right: 4, bottom: 0, left: -24 }} barCategoryGap={4}>
          <CartesianGrid stroke="#EDEBE6" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 11, fill: '#6B6862' }}
            tickLine={false}
            axisLine={{ stroke: '#E1DED7' }}
            interval="preserveStartEnd"
          />
          <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#6B6862' }} axisLine={false} tickLine={false} />
          <Tooltip
            cursor={{ fill: '#F1EFEA' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              const row = payload[0].payload as (typeof rows)[number]
              return (
                <div className="rounded-md border border-line bg-surface px-3 py-2 text-[12.5px] shadow-pop">
                  <p className="font-bold">{formatDate(row.date)}</p>
                  <p>
                    {row.orders} đơn · {formatPrice(row.amount)}
                  </p>
                </div>
              )
            }}
          />
          <Bar
            dataKey="orders"
            name="Số đơn"
            fill="#5B3DF5"
            radius={[4, 4, 0, 0]}
            maxBarSize={28}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function Tile({
  label,
  value,
  sub,
  warn,
  to,
  icon,
  color,
}: {
  label: string
  value: string
  sub?: string
  warn?: boolean
  to?: string
  icon: string
  color: string
}) {
  const body = (
    <>
      <span
        className="absolute -right-6 -top-6 h-24 w-24 rounded-full opacity-15"
        style={{ background: color }}
        aria-hidden
      />
      <span
        className="relative mb-3 grid h-11 w-11 place-items-center rounded-full text-[20px]"
        style={{ background: `${color}22` }}
        aria-hidden
      >
        {icon}
      </span>
      <p className="relative text-[13px] font-semibold text-ink-muted">{label}</p>
      <p className="relative mt-0.5 font-display text-[30px] font-extrabold leading-tight" style={{ color }}>
        {value}
      </p>
      {sub && (
        <p className={`relative mt-0.5 text-[12.5px] ${warn ? 'font-semibold text-[#B45309]' : 'text-ink-muted'}`}>
          {sub}
        </p>
      )}
    </>
  )
  return to ? (
    <Link
      to={to}
      className="card relative block overflow-hidden p-5 transition hover:-translate-y-0.5 hover:shadow-card-hover"
    >
      {body}
    </Link>
  ) : (
    <div className="card relative overflow-hidden p-5">{body}</div>
  )
}

function Panel({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="card p-5">
      <div className="mb-4 flex items-baseline justify-between gap-2">
        <h2 className="font-display text-[19px] font-extrabold">{title}</h2>
        {note && <span className="text-[12px] text-ink-muted">{note}</span>}
      </div>
      {children}
    </section>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return <p className="mt-2 text-[13.5px] text-ink-muted">{children}</p>
}
