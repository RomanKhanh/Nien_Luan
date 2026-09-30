import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { chatApi } from '@/api/chat'
import { ConfirmDialog } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { formatRelative } from '@/lib/format'
import { chatKeys, useChatSessions } from './queries'

// lịch sử hội thoại của phụ huynh (mục 2.3: xem lại tư vấn trước đó)
export function SessionList({
  activeId,
  onSelect,
  onDeleted,
}: {
  activeId: number | null
  onSelect: (id: number) => void
  onDeleted?: (id: number) => void
}) {
  const sessions = useChatSessions()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [removing, setRemoving] = useState<{ id: number; title: string } | null>(null)
  const remove = useMutation({
    mutationFn: (id: number) => chatApi.deleteSession(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: chatKeys.sessions })
      queryClient.removeQueries({ queryKey: chatKeys.session(id) })
      setRemoving(null)
      onDeleted?.(id)
      toast.success('Đã xoá hội thoại')
    },
    onError: (e) => toast.error(e.message),
  })

  if (sessions.isPending) {
    return (
      <div className="space-y-2 p-3">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-14" />
        ))}
      </div>
    )
  }
  if (sessions.isError) return <p className="p-4 text-[13px] text-danger">{sessions.error.message}</p>
  if (sessions.data.length === 0) {
    return (
      <p className="p-6 text-center text-[13.5px] text-ink-muted">Chưa có hội thoại nào. Hãy hỏi Bin câu đầu tiên!</p>
    )
  }
  return (
    <>
      <ul className="space-y-1 p-2">
        {sessions.data.map((s) => {
          const title = s.title ?? 'Hội thoại mới'
          return (
            <li key={s.id} className="group relative">
              <button
                type="button"
                onClick={() => onSelect(s.id)}
                aria-current={s.id === activeId ? 'true' : undefined}
                className={`w-full rounded-2xl px-3 py-2.5 pr-10 text-left transition ${
                  s.id === activeId ? 'bg-primary-soft' : 'hover:bg-sky-soft/70'
                }`}
              >
                <p
                  className={`line-clamp-1 text-[13.5px] font-bold ${s.id === activeId ? 'text-primary-hover' : 'text-ink'}`}
                >
                  {title}
                </p>
                <p className="mt-0.5 text-[12px] text-ink-muted">
                  {s.childName ? `Bé ${s.childName} · ` : ''}
                  {formatRelative(s.lastMessageAt ?? s.startedAt)} · {Math.ceil(s.messageCount / 2)} câu hỏi
                </p>
              </button>
              <button
                type="button"
                aria-label={`Xoá hội thoại ${title}`}
                onClick={() => setRemoving({ id: s.id, title })}
                className="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-ink-faint opacity-100 transition hover:bg-danger-soft hover:text-danger sm:opacity-0 sm:group-hover:opacity-100 sm:focus:opacity-100"
              >
                🗑
              </button>
            </li>
          )
        })}
      </ul>
      <ConfirmDialog
        open={Boolean(removing)}
        title="Xoá hội thoại này?"
        message={
          <>
            Hội thoại <b>“{removing?.title}”</b> và toàn bộ tin nhắn sẽ bị xoá.
          </>
        }
        confirmLabel="Xoá hội thoại"
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
        onClose={() => setRemoving(null)}
      />
    </>
  )
}
