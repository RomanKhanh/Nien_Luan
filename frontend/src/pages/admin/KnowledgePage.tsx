import { useRef, useState, type DragEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminChatApi, CHAT_MOCK } from '@/api/chat'
import type { DocumentStatus, KnowledgeDocument } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { MockNotice } from '@/components/admin/MockNotice'
import { Pill } from '@/components/ui/Badges'
import { Button, Spinner } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { ConfirmDialog, Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader, Skeleton } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { adminChatKeys } from '@/features/chat/queries'
import { formatDateTime } from '@/lib/format'
import { DOCUMENT_STATUS } from '@/lib/labels'
import { useDebounced } from '@/lib/useDebounced'

const ACCEPT = ['pdf', 'docx', 'txt', 'md']
const MAX_MB = 10

const FILTERS: { value: DocumentStatus | undefined; label: string }[] = [
  { value: undefined, label: 'Tất cả' },
  { value: 'INDEXED', label: 'Đang dùng' },
  { value: 'PROCESSING', label: 'Đang xử lý' },
  { value: 'DISABLED', label: 'Đã tắt' },
]

const TYPE_COLORS: Record<string, string> = { PDF: '#e8467c', DOCX: '#3d7bff', TXT: '#0f9e7a', MD: '#e08700' }

// Mục 2.12: tài liệu làm cơ sở tri thức (RAG) cho chatbot tư vấn
export default function KnowledgePage() {
  const [status, setStatus] = useState<DocumentStatus | undefined>()
  const [keyword, setKeyword] = useState('')
  const debounced = useDebounced(keyword.trim(), 350)
  const [page, setPage] = useState(0)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [renaming, setRenaming] = useState<KnowledgeDocument | null>(null)
  const [removing, setRemoving] = useState<KnowledgeDocument | null>(null)
  const [viewing, setViewing] = useState<KnowledgeDocument | null>(null)
  const queryClient = useQueryClient()
  const toast = useToast()

  const params = { status, keyword: debounced || undefined, page, size: 10 }
  const docs = useQuery({
    queryKey: adminChatKeys.documents(params),
    queryFn: () => adminChatApi.documents(params),
    placeholderData: keepPreviousData,
    // còn tài liệu đang tách đoạn / tạo embedding thì hỏi lại trạng thái định kỳ
    refetchInterval: (q) => (q.state.data?.content.some((d) => d.status === 'PROCESSING') ? 2000 : false),
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'knowledge'] })

  const setDocStatus = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'INDEXED' | 'DISABLED' }) =>
      adminChatApi.setDocumentStatus(id, value),
    onSuccess: (d) => {
      invalidate()
      toast.success(d.status === 'DISABLED' ? 'Đã tắt tài liệu, chatbot sẽ không dùng nữa' : 'Đã bật lại tài liệu')
    },
    onError: (e) => toast.error(e.message),
  })
  const reindex = useMutation({
    mutationFn: (id: number) => adminChatApi.reindexDocument(id),
    onSuccess: () => {
      invalidate()
      toast.info('Đang lập chỉ mục lại tài liệu…')
    },
    onError: (e) => toast.error(e.message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => adminChatApi.deleteDocument(id),
    onSuccess: () => {
      invalidate()
      setRemoving(null)
      toast.success('Đã xoá tài liệu khỏi cơ sở tri thức')
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <AdminHeader
        title="Cơ sở tri thức"
        description="Tài liệu giáo dục STEM, phương pháp phát triển tư duy, hướng dẫn sử dụng sản phẩm… để chatbot truy xuất khi trả lời."
        actions={<Button onClick={() => setUploadOpen(true)}>＋ Tải tài liệu lên</Button>}
      />
      {CHAT_MOCK && <MockNotice />}

      <ol className="mb-5 grid gap-3 sm:grid-cols-3">
        {[
          ['📤', 'Tải lên', 'PDF, DOCX, TXT hoặc Markdown'],
          ['✂️', 'Tách đoạn & tạo embedding', 'Hệ thống tự xử lý sau khi tải lên'],
          ['🤖', 'Chatbot truy xuất', 'Chỉ tài liệu “Đang dùng” được dùng để trả lời'],
        ].map(([icon, title, text], i) => (
          <li key={title} className="card flex items-center gap-3 p-4">
            <span
              className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-sky-soft text-[20px]"
              aria-hidden
            >
              {icon}
            </span>
            <span>
              <span className="block text-[14px] font-bold">
                {i + 1}. {title}
              </span>
              <span className="block text-[12.5px] text-ink-muted">{text}</span>
            </span>
          </li>
        ))}
      </ol>

      <div className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-line p-3">
          <div className="flex gap-1.5 overflow-x-auto">
            {FILTERS.map((f) => (
              <button
                key={f.label}
                type="button"
                onClick={() => {
                  setStatus(f.value)
                  setPage(0)
                }}
                className={`shrink-0 rounded-full px-3.5 py-1.5 text-[13px] font-bold transition ${
                  status === f.value ? 'bg-primary text-white shadow-[0_3px_0_#3a22b8]' : 'text-ink-2 hover:bg-sky-soft'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
          <Input
            type="search"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
            placeholder="Tìm theo tên tài liệu…"
            aria-label="Tìm tài liệu"
            className="ml-auto max-w-xs !py-2"
          />
        </div>

        {docs.isPending ? (
          <PageLoader />
        ) : docs.isError ? (
          <ErrorState message={docs.error.message} onRetry={() => docs.refetch()} />
        ) : docs.data.content.length === 0 ? (
          <EmptyState
            icon="📚"
            title="Chưa có tài liệu nào"
            description="Tải tài liệu lên để chatbot có thêm kiến thức khi tư vấn cho phụ huynh."
            action={<Button onClick={() => setUploadOpen(true)}>＋ Tải tài liệu lên</Button>}
          />
        ) : (
          <ul className={`divide-y divide-line ${docs.isPlaceholderData ? 'opacity-60' : ''}`}>
            {docs.data.content.map((d) => {
              const st = DOCUMENT_STATUS[d.status]
              const busy =
                (setDocStatus.isPending && setDocStatus.variables?.id === d.id) ||
                (reindex.isPending && reindex.variables === d.id)
              return (
                <li key={d.id} className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
                  <div className="flex min-w-0 flex-1 items-center gap-3">
                    <span
                      className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl font-display text-[13px] font-extrabold text-white"
                      style={{ background: TYPE_COLORS[d.fileType] ?? '#6b6862' }}
                      aria-label={`Tệp ${d.fileType}`}
                    >
                      {d.fileType}
                    </span>
                    <div className="min-w-0">
                      <p className="line-clamp-1 font-bold">{d.title}</p>
                      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[12.5px] text-ink-muted">
                        <Pill tone={st.tone}>
                          {d.status === 'PROCESSING' && <Spinner className="mr-1 h-3 w-3" />}
                          {st.label}
                        </Pill>
                        <span>{d.chunkCount} đoạn</span>
                        <span>
                          · {formatDateTime(d.uploadedAt)} · {d.uploadedByName}
                        </span>
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="secondary" onClick={() => setViewing(d)}>
                      Xem đoạn
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setRenaming(d)}>
                      Đổi tên
                    </Button>
                    {d.status !== 'PROCESSING' && (
                      <Button
                        size="sm"
                        variant={d.status === 'DISABLED' ? 'soft' : 'secondary'}
                        loading={busy && setDocStatus.isPending}
                        onClick={() =>
                          setDocStatus.mutate({ id: d.id, value: d.status === 'DISABLED' ? 'INDEXED' : 'DISABLED' })
                        }
                      >
                        {d.status === 'DISABLED' ? 'Bật lại' : 'Tắt'}
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={d.status === 'PROCESSING'}
                      loading={busy && reindex.isPending}
                      onClick={() => reindex.mutate(d.id)}
                      title="Tách đoạn và tạo embedding lại (khi đổi cách xử lý hoặc tệp lỗi)"
                    >
                      ↻ Lập chỉ mục lại
                    </Button>
                    <Button size="sm" variant="danger-outline" onClick={() => setRemoving(d)}>
                      Xoá
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
        {docs.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={docs.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>

      <UploadModal open={uploadOpen} onClose={() => setUploadOpen(false)} onUploaded={invalidate} />
      {renaming && <RenameModal doc={renaming} onClose={() => setRenaming(null)} onSaved={invalidate} />}
      <ChunksDrawer doc={viewing} onClose={() => setViewing(null)} />
      <ConfirmDialog
        open={Boolean(removing)}
        title="Xoá tài liệu này?"
        message={
          <>
            <b>{removing?.title}</b> và toàn bộ {removing?.chunkCount} đoạn đã tách sẽ bị xoá; chatbot không còn dùng
            tài liệu này để trả lời.
          </>
        }
        confirmLabel="Xoá tài liệu"
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
        onClose={() => setRemoving(null)}
      />
    </>
  )
}

function UploadModal({ open, onClose, onUploaded }: { open: boolean; onClose: () => void; onUploaded: () => void }) {
  const toast = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)

  const reset = () => {
    setFile(null)
    setTitle('')
    setError(null)
  }
  const close = () => {
    reset()
    onClose()
  }

  const pick = (f: File | undefined) => {
    if (!f) return
    const ext = f.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ACCEPT.includes(ext)) {
      setError(`Chỉ nhận tệp ${ACCEPT.map((a) => a.toUpperCase()).join(', ')}.`)
      return
    }
    if (f.size > MAX_MB * 1024 * 1024) {
      setError(`Tệp tối đa ${MAX_MB} MB.`)
      return
    }
    setError(null)
    setFile(f)
    if (!title.trim()) setTitle(f.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' '))
  }

  const upload = useMutation({
    mutationFn: () => adminChatApi.uploadDocument({ title: title.trim(), file: file! }),
    onSuccess: () => {
      onUploaded()
      toast.success('Đã tải lên, hệ thống đang tách đoạn và lập chỉ mục')
      close()
    },
    onError: (e) => setError(e.message),
  })

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    pick(e.dataTransfer.files[0])
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Tải tài liệu lên"
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Huỷ
          </Button>
          <Button disabled={!file || !title.trim()} loading={upload.isPending} onClick={() => upload.mutate()}>
            Tải lên
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={`flex w-full flex-col items-center rounded-[22px] border-[3px] border-dashed px-4 py-8 text-center transition ${
            dragging ? 'border-sky bg-sky-soft' : 'border-line-strong bg-[#f7fbfd] hover:border-sky'
          }`}
        >
          <span className="text-[38px]" aria-hidden>
            {file ? '📄' : '📤'}
          </span>
          {file ? (
            <>
              <span className="mt-1 max-w-full truncate font-bold text-ink">{file.name}</span>
              <span className="text-[12.5px] text-ink-muted">
                {(file.size / 1024).toLocaleString('vi-VN', { maximumFractionDigits: 0 })} KB · bấm để chọn tệp khác
              </span>
            </>
          ) : (
            <>
              <span className="mt-1 font-bold text-ink">Kéo thả tệp vào đây hoặc bấm để chọn</span>
              <span className="text-[12.5px] text-ink-muted">
                {ACCEPT.map((a) => a.toUpperCase()).join(', ')} · tối đa {MAX_MB} MB
              </span>
            </>
          )}
        </button>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT.map((a) => `.${a}`).join(',')}
          className="hidden"
          onChange={(e) => {
            pick(e.target.files?.[0])
            e.target.value = ''
          }}
        />
        <Field
          label="Tên tài liệu"
          htmlFor="docTitle"
          required
          hint="Tên hiển thị trong phần “Nguồn tham khảo” của chatbot"
        >
          <Input id="docTitle" value={title} maxLength={255} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        {error && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-[13px] font-semibold text-danger">
            {error}
          </p>
        )}
      </div>
    </Modal>
  )
}

function RenameModal({ doc, onClose, onSaved }: { doc: KnowledgeDocument; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const [title, setTitle] = useState(doc.title)
  const save = useMutation({
    mutationFn: () => adminChatApi.updateDocument(doc.id, { title: title.trim() }),
    onSuccess: () => {
      onSaved()
      toast.success('Đã đổi tên tài liệu')
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Modal
      open
      onClose={onClose}
      title="Đổi tên tài liệu"
      size="sm"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            Huỷ
          </Button>
          <Button
            size="sm"
            disabled={!title.trim() || title.trim() === doc.title}
            loading={save.isPending}
            onClick={() => save.mutate()}
          >
            Lưu
          </Button>
        </>
      }
    >
      <Field label="Tên tài liệu" htmlFor="renameTitle" required>
        <Input id="renameTitle" value={title} maxLength={255} onChange={(e) => setTitle(e.target.value)} autoFocus />
      </Field>
    </Modal>
  )
}

// các đoạn văn bản đã tách (DocumentChunk) — dùng để kiểm tra chatbot sẽ "đọc" được gì
function ChunksDrawer({ doc, onClose }: { doc: KnowledgeDocument | null; onClose: () => void }) {
  const chunks = useQuery({
    queryKey: adminChatKeys.chunks(doc?.id ?? 0),
    queryFn: () => adminChatApi.documentChunks(doc!.id),
    enabled: doc !== null,
  })
  return (
    <Modal open={doc !== null} onClose={onClose} title={doc?.title ?? ''} variant="drawer" size="lg">
      {chunks.isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : chunks.isError ? (
        <ErrorState message={chunks.error.message} onRetry={() => chunks.refetch()} />
      ) : chunks.data.length === 0 ? (
        <p className="text-[14px] text-ink-muted">Tài liệu chưa được tách đoạn.</p>
      ) : (
        <>
          <p className="mb-4 text-[13px] text-ink-muted">
            {chunks.data.length} đoạn · {chunks.data.filter((c) => c.embedded).length} đoạn đã tạo embedding
          </p>
          <ol className="space-y-3">
            {chunks.data.map((c) => (
              <li key={c.id} className="rounded-2xl border-[1.5px] border-line p-4">
                <div className="mb-1.5 flex items-center justify-between gap-2">
                  <span className="font-display text-[15px] font-extrabold text-sky-deep">
                    Đoạn #{c.chunkIndex + 1}
                  </span>
                  <Pill tone={c.embedded ? 'green' : 'amber'}>
                    {c.embedded ? 'Đã có embedding' : 'Chưa có embedding'}
                  </Pill>
                </div>
                <p className="whitespace-pre-line text-[13.5px] text-ink-2">{c.content}</p>
              </li>
            ))}
          </ol>
        </>
      )}
    </Modal>
  )
}
