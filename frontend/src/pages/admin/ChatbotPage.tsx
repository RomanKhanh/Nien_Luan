import { useState, type ReactNode } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { adminChatApi, CHAT_MOCK } from '@/api/chat'
import type { ChatbotConfig, ChatbotConfigRequest, ChatStats } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { MockNotice } from '@/components/admin/MockNotice'
import { Pill } from '@/components/ui/Badges'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { MessageBubble } from '@/features/chat/MessageBubble'
import { adminChatKeys } from '@/features/chat/queries'
import { formatDate, formatDateTime, formatRelative } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'

const TABS = [
  { value: 'overview', label: 'Tổng quan', icon: '📊' },
  { value: 'history', label: 'Lịch sử hội thoại', icon: '💬' },
  { value: 'config', label: 'Cấu hình', icon: '⚙️' },
] as const
type Tab = (typeof TABS)[number]['value']

// Mục 2.13: giám sát chatbot (lịch sử, câu hỏi phổ biến, kiểm tra nội dung tư vấn) + cấu hình tham số
export default function ChatbotPage() {
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.value === params.get('tab'))?.value ?? 'overview') as Tab

  return (
    <>
      <AdminHeader
        title="Chatbot AI"
        description="Theo dõi câu hỏi của phụ huynh, kiểm tra nội dung tư vấn và cấu hình tham số của chatbot."
        actions={
          <>
            <ButtonLink to="/admin/knowledge" variant="secondary" size="sm">
              📚 Cơ sở tri thức
            </ButtonLink>
            <ButtonLink to="/admin/products" variant="secondary" size="sm">
              🧸 Chỉ số kỹ năng sản phẩm
            </ButtonLink>
          </>
        }
      />
      {CHAT_MOCK && <MockNotice />}
      <div className="-mx-1 mb-5 flex gap-1.5 overflow-x-auto px-1 pb-1" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => setParams(t.value === 'overview' ? {} : { tab: t.value })}
            className={`flex shrink-0 items-center gap-2 rounded-full px-4 py-2 text-[14px] font-bold transition ${
              tab === t.value
                ? 'bg-primary text-white shadow-[0_3px_0_#3a22b8]'
                : 'bg-surface text-ink-2 ring-2 ring-line hover:text-primary hover:ring-primary'
            }`}
          >
            <span aria-hidden>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'history' && <History />}
      {tab === 'config' && <Config />}
    </>
  )
}

// ---------- Tổng quan ----------

function Overview() {
  const stats = useQuery({ queryKey: adminChatKeys.stats, queryFn: adminChatApi.stats })
  if (stats.isPending) return <PageLoader />
  if (stats.isError) return <ErrorState message={stats.error.message} onRetry={() => stats.refetch()} />
  const s = stats.data
  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Tile icon="❓" color="#1fa6dd" label="Câu hỏi đã nhận" value={s.totalQuestions.toLocaleString('vi-VN')} />
        <Tile icon="💬" color="#5b3df5" label="Hội thoại" value={s.totalSessions.toLocaleString('vi-VN')} />
        <Tile
          icon="📅"
          color="#e08700"
          label="Câu hỏi 7 ngày qua"
          value={s.questionsLast7Days.toLocaleString('vi-VN')}
        />
        <Tile
          icon="⚡"
          color="#0f9e7a"
          label="Thời gian phản hồi TB"
          value={`${(s.avgResponseTimeMs / 1000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}s`}
        />
      </div>
      <div className="grid gap-5 xl:grid-cols-[1.6fr_1fr]">
        <Panel title="Câu hỏi 14 ngày gần nhất">
          <DailyChart data={s.dailyQuestions} />
        </Panel>
        <Panel title="Chủ đề tư vấn phổ biến">
          {s.topTopics.length === 0 ? (
            <Empty>Chưa có câu hỏi nào.</Empty>
          ) : (
            <ul className="space-y-3">
              {s.topTopics.map((t, i) => (
                <li key={t.topic}>
                  <div className="mb-1 flex justify-between text-[13.5px]">
                    <span className="font-semibold text-ink-2">{t.topic}</span>
                    <b className="tabular-nums">{t.total}</b>
                  </div>
                  <div className="h-2.5 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${(t.total / s.topTopics[0].total) * 100}%`,
                        background: TOPIC_COLORS[i % TOPIC_COLORS.length],
                      }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Câu hỏi được hỏi nhiều">
          {s.topQuestions.length === 0 ? (
            <Empty>Chưa có câu hỏi nào.</Empty>
          ) : (
            <ol className="space-y-2.5 text-[14px]">
              {s.topQuestions.map((q, i) => (
                <li key={q.question} className="flex items-start gap-3">
                  <Rank i={i} />
                  <span className="min-w-0 flex-1 text-ink-2">{q.question}</span>
                  <span className="shrink-0 text-ink-muted tabular-nums">{q.total} lần</span>
                </li>
              ))}
            </ol>
          )}
        </Panel>
        <Panel title="Sản phẩm được chatbot gợi ý nhiều">
          {s.topSuggestedProducts.length === 0 ? (
            <Empty>Chưa có gợi ý sản phẩm nào.</Empty>
          ) : (
            <ol className="space-y-2.5 text-[14px]">
              {s.topSuggestedProducts.map((p, i) => (
                <li key={p.productId} className="flex items-center gap-3">
                  <Rank i={i} />
                  <Link to={`/products/${p.productId}`} className="min-w-0 flex-1 truncate">
                    {p.productName}
                  </Link>
                  <span className="shrink-0 text-ink-muted tabular-nums">{p.total} lần</span>
                </li>
              ))}
            </ol>
          )}
        </Panel>
      </div>
    </div>
  )
}

const TOPIC_COLORS = ['#1fa6dd', '#e8467c', '#e08700', '#0f9e7a', '#5b3df5', '#7cc243']

function Rank({ i }: { i: number }) {
  return (
    <span
      className={`grid h-7 w-7 shrink-0 place-items-center rounded-full font-display text-[14px] font-extrabold ${
        i === 0 ? 'bg-sun text-ink' : i === 1 ? 'bg-sky-soft text-sky-deep' : 'bg-muted text-ink-2'
      }`}
    >
      {i + 1}
    </span>
  )
}

function DailyChart({ data }: { data: ChatStats['dailyQuestions'] }) {
  const rows = data.map((d) => ({ ...d, label: formatDate(d.date).slice(0, 5) }))
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
                <div className="rounded-xl border border-line bg-surface px-3 py-2 text-[12.5px] shadow-pop">
                  <p className="font-bold">{formatDate(row.date)}</p>
                  <p>{row.total} câu hỏi</p>
                </div>
              )
            }}
          />
          <Bar dataKey="total" name="Câu hỏi" fill="#1fa6dd" radius={[6, 6, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

// ---------- Lịch sử hội thoại ----------

function History() {
  const [keyword, setKeyword] = useState('')
  const debounced = useDebounced(keyword.trim(), 350)
  const [page, setPage] = useState(0)
  const [viewing, setViewing] = useState<number | null>(null)
  const params = { keyword: debounced || undefined, page, size: 12 }
  const sessions = useQuery({
    queryKey: adminChatKeys.sessions(params),
    queryFn: () => adminChatApi.sessions(params),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="card overflow-hidden">
      <div className="border-b border-line p-3">
        <Input
          type="search"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(0)
          }}
          placeholder="Tìm theo khách hàng, tên bé hoặc nội dung câu hỏi…"
          aria-label="Tìm hội thoại"
          className="max-w-md"
        />
      </div>
      {sessions.isPending ? (
        <PageLoader />
      ) : sessions.isError ? (
        <ErrorState message={sessions.error.message} onRetry={() => sessions.refetch()} />
      ) : sessions.data.content.length === 0 ? (
        <EmptyState
          title="Chưa có hội thoại nào"
          description="Khi phụ huynh trò chuyện với Bin, hội thoại sẽ hiện ở đây."
        />
      ) : (
        <ul className={`divide-y divide-line ${sessions.isPlaceholderData ? 'opacity-60' : ''}`}>
          {sessions.data.content.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                onClick={() => setViewing(s.id)}
                className="flex w-full flex-col gap-1 p-4 text-left transition hover:bg-sky-soft/50 sm:flex-row sm:items-center sm:gap-4"
              >
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-1 font-bold text-ink">{s.title ?? 'Hội thoại chưa có câu hỏi'}</p>
                  <p className="text-[13px] text-ink-muted">
                    {s.customerName} · {s.customerEmail}
                    {s.childName && ` · tư vấn cho bé ${s.childName}`}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3 text-[13px] text-ink-muted">
                  <Pill tone="blue">{Math.ceil(s.messageCount / 2)} câu hỏi</Pill>
                  <span>{formatRelative(s.lastMessageAt ?? s.startedAt)}</span>
                  <span className="font-bold text-primary" aria-hidden>
                    ›
                  </span>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
      {sessions.data && (
        <div className="border-t border-line p-4">
          <Pagination page={page} totalPages={sessions.data.totalPages} onChange={setPage} />
        </div>
      )}
      <Transcript id={viewing} onClose={() => setViewing(null)} />
    </div>
  )
}

// xem lại toàn bộ hội thoại để kiểm tra nội dung tư vấn
function Transcript({ id, onClose }: { id: number | null; onClose: () => void }) {
  const detail = useQuery({
    queryKey: adminChatKeys.session(id ?? 0),
    queryFn: () => adminChatApi.session(id!),
    enabled: id !== null,
  })
  const d = detail.data
  return (
    <Modal open={id !== null} onClose={onClose} title="Chi tiết hội thoại" variant="drawer" size="lg">
      {detail.isPending ? (
        <PageLoader />
      ) : detail.isError ? (
        <ErrorState message={detail.error.message} onRetry={() => detail.refetch()} />
      ) : (
        d && (
          <>
            <dl className="mb-5 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-2xl bg-sky-soft/60 p-4 text-[13.5px]">
              <dt className="text-ink-muted">Khách hàng</dt>
              <dd className="font-semibold">
                {d.customerName} · {d.customerEmail}
              </dd>
              <dt className="text-ink-muted">Hồ sơ bé</dt>
              <dd className="font-semibold">{d.childName ? `Bé ${d.childName}` : 'Không chọn (tư vấn chung)'}</dd>
              <dt className="text-ink-muted">Bắt đầu</dt>
              <dd className="font-semibold">{formatDateTime(d.startedAt)}</dd>
            </dl>
            {d.messages.length === 0 ? (
              <p className="text-[14px] text-ink-muted">Hội thoại chưa có tin nhắn.</p>
            ) : (
              <div className="space-y-4">
                {d.messages.map((m) => (
                  <MessageBubble key={m.id} message={m} review />
                ))}
              </div>
            )}
          </>
        )
      )}
    </Modal>
  )
}

// ---------- Cấu hình ----------

const configSchema = z.object({
  modelName: z.string().trim().min(1, 'Nhập tên mô hình').max(100, 'Tối đa 100 ký tự'),
  temperature: z.number({ error: 'Nhập số từ 0 đến 1' }).min(0, 'Tối thiểu 0').max(1, 'Tối đa 1'),
  maxTokens: z
    .number({ error: 'Nhập số nguyên' })
    .int('Nhập số nguyên')
    .min(64, 'Tối thiểu 64')
    .max(8192, 'Tối đa 8192'),
  topK: z.number({ error: 'Nhập số nguyên' }).int('Nhập số nguyên').min(1, 'Tối thiểu 1').max(20, 'Tối đa 20'),
  systemPrompt: z.string().max(4000, 'Tối đa 4000 ký tự'),
})
type ConfigValues = z.infer<typeof configSchema>

function Config() {
  const configs = useQuery({ queryKey: adminChatKeys.configs, queryFn: adminChatApi.configs })
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const queryClient = useQueryClient()
  const toast = useToast()

  const list = configs.data ?? []
  const selected = list.find((c) => c.id === selectedId) ?? list.find((c) => c.active) ?? list[0]

  const activate = useMutation({
    mutationFn: (id: number) => adminChatApi.activateConfig(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminChatKeys.configs })
      toast.success('Đã áp dụng cấu hình cho chatbot')
    },
    onError: (e) => toast.error(e.message),
  })

  if (configs.isPending) return <PageLoader />
  if (configs.isError) return <ErrorState message={configs.error.message} onRetry={() => configs.refetch()} />

  return (
    <div className="grid gap-5 xl:grid-cols-[300px_minmax(0,1fr)]">
      <aside className="card h-fit p-3">
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <p className="font-display text-[18px] font-extrabold">Các phiên bản</p>
          <Button size="sm" variant="soft" onClick={() => setCreating(true)}>
            ＋ Tạo mới
          </Button>
        </div>
        {list.length === 0 ? (
          <p className="p-3 text-[13.5px] text-ink-muted">Chưa có cấu hình nào.</p>
        ) : (
          <ul className="space-y-1">
            {list.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedId(c.id)
                    setCreating(false)
                  }}
                  className={`w-full rounded-2xl px-3 py-2.5 text-left transition ${
                    !creating && selected?.id === c.id ? 'bg-primary-soft' : 'hover:bg-sky-soft/70'
                  }`}
                >
                  <span className="flex items-center justify-between gap-2">
                    <b className="truncate text-[14px]">{c.modelName}</b>
                    {c.active && <Pill tone="green">Đang dùng</Pill>}
                  </span>
                  <span className="text-[12px] text-ink-muted">
                    #{c.id} · cập nhật {formatRelative(c.updatedAt)} · {c.adminName}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>
      <section className="card p-5 sm:p-6">
        {creating || !selected ? (
          <ConfigForm
            key="new"
            onDone={(c) => {
              setCreating(false)
              setSelectedId(c.id)
            }}
          />
        ) : (
          <ConfigForm
            key={selected.id}
            config={selected}
            onDone={(c) => setSelectedId(c.id)}
            onActivate={() => activate.mutate(selected.id)}
            activating={activate.isPending}
          />
        )}
      </section>
    </div>
  )
}

const DEFAULTS: ConfigValues = { modelName: '', temperature: 0.3, maxTokens: 1024, topK: 4, systemPrompt: '' }

function ConfigForm({
  config,
  onDone,
  onActivate,
  activating,
}: {
  config?: ChatbotConfig
  onDone: (c: ChatbotConfig) => void
  onActivate?: () => void
  activating?: boolean
}) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const form = useForm<ConfigValues>({
    resolver: zodResolver(configSchema),
    defaultValues: config
      ? {
          modelName: config.modelName,
          temperature: config.temperature,
          maxTokens: config.maxTokens,
          topK: config.topK,
          systemPrompt: config.systemPrompt ?? '',
        }
      : DEFAULTS,
  })
  const { errors, isDirty } = form.formState

  const save = useMutation({
    mutationFn: ({ values, asNew }: { values: ConfigValues; asNew: boolean }) => {
      const body: ChatbotConfigRequest = { ...values, systemPrompt: values.systemPrompt.trim() || null }
      return config && !asNew ? adminChatApi.updateConfig(config.id, body) : adminChatApi.createConfig(body)
    },
    onSuccess: (c, { asNew }) => {
      queryClient.invalidateQueries({ queryKey: adminChatKeys.configs })
      toast.success(config && !asNew ? 'Đã lưu cấu hình' : 'Đã tạo cấu hình mới (chưa áp dụng)')
      form.reset(form.getValues())
      onDone(c)
    },
    onError: (e) => toast.error(e.message),
  })
  const temperature = useWatch({ control: form.control, name: 'temperature' })

  return (
    <form
      noValidate
      onSubmit={form.handleSubmit((values) => save.mutate({ values, asNew: false }))}
      className="space-y-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="h2">{config ? `Cấu hình #${config.id}` : 'Cấu hình mới'}</h2>
          <p className="mt-1 text-[13px] text-ink-muted">
            {config?.active
              ? 'Chatbot đang dùng cấu hình này. Lưu thay đổi sẽ áp dụng ngay.'
              : 'Cấu hình chưa được áp dụng. Bấm “Áp dụng” để chatbot dùng cấu hình này.'}
          </p>
        </div>
        {config && !config.active && onActivate && (
          <Button variant="accent" size="sm" loading={activating} onClick={onActivate} disabled={isDirty}>
            ✓ Áp dụng cho chatbot
          </Button>
        )}
      </div>

      <p className="rounded-2xl bg-sky-soft/60 px-4 py-3 text-[13px] text-ink-2">
        🔒 API key của dịch vụ AI được lưu và sử dụng phía server, không nhập hay hiển thị ở đây.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Mô hình AI"
          htmlFor="modelName"
          required
          error={errors.modelName?.message}
          hint="Tên mô hình của nhà cung cấp AI"
        >
          <Input id="modelName" invalid={Boolean(errors.modelName)} {...form.register('modelName')} />
        </Field>
        <Field
          label={`Độ sáng tạo (temperature): ${Number.isFinite(temperature) ? temperature : '—'}`}
          htmlFor="temperature"
          error={errors.temperature?.message}
          hint="Thấp: trả lời ổn định, bám dữ liệu. Cao: đa dạng hơn."
        >
          <input
            id="temperature"
            type="range"
            min={0}
            max={1}
            step={0.05}
            className="mt-2 w-full accent-primary"
            {...form.register('temperature', { valueAsNumber: true })}
          />
        </Field>
        <Field label="Độ dài tối đa (max tokens)" htmlFor="maxTokens" required error={errors.maxTokens?.message}>
          <Input
            id="maxTokens"
            type="number"
            invalid={Boolean(errors.maxTokens)}
            {...form.register('maxTokens', { valueAsNumber: true })}
          />
        </Field>
        <Field
          label="Số đoạn tri thức mỗi câu hỏi (top K)"
          htmlFor="topK"
          required
          error={errors.topK?.message}
          hint="Số đoạn tài liệu RAG lấy ra làm căn cứ trả lời"
        >
          <Input
            id="topK"
            type="number"
            invalid={Boolean(errors.topK)}
            {...form.register('topK', { valueAsNumber: true })}
          />
        </Field>
      </div>
      <Field
        label="Chỉ dẫn hệ thống (system prompt)"
        htmlFor="systemPrompt"
        error={errors.systemPrompt?.message}
        hint="Vai trò, giọng điệu và giới hạn của chatbot (ví dụ: không chẩn đoán năng lực trẻ)."
      >
        <Textarea
          id="systemPrompt"
          rows={7}
          invalid={Boolean(errors.systemPrompt)}
          {...form.register('systemPrompt')}
        />
      </Field>

      <div className="flex flex-wrap justify-end gap-2.5 border-t border-line pt-4">
        {config && (
          <Button
            variant="secondary"
            loading={save.isPending && save.variables?.asNew}
            onClick={form.handleSubmit((values) => save.mutate({ values, asNew: true }))}
          >
            Lưu thành cấu hình mới
          </Button>
        )}
        <Button type="submit" loading={save.isPending && !save.variables?.asNew} disabled={config && !isDirty}>
          {config ? 'Lưu thay đổi' : 'Tạo cấu hình'}
        </Button>
      </div>
    </form>
  )
}

// ---------- khối dùng chung ----------

function Tile({ icon, color, label, value }: { icon: string; color: string; label: string; value: string }) {
  return (
    <div className="card relative overflow-hidden p-5">
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
    </div>
  )
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card p-5">
      <h2 className="mb-4 font-display text-[19px] font-extrabold">{title}</h2>
      {children}
    </section>
  )
}

function Empty({ children }: { children: ReactNode }) {
  return <p className="mt-2 text-[13.5px] text-ink-muted">{children}</p>
}
