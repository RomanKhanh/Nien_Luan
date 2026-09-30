import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Field'
import { ConfirmDialog, Modal } from '@/components/ui/Modal'
import { PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { useCategories, useSkills } from '@/features/catalog/queries'
import { skillTheme } from '@/lib/skills'

// quản lý danh mục và nhóm kỹ năng (đề 2.9); chỉ số kỹ năng từng sản phẩm sửa ở trang Sản phẩm
type Editing =
  | { kind: 'category'; id: number | null; name: string; description: string }
  | { kind: 'skill'; id: number | null; code: string; name: string; description: string }

export default function CatalogPage() {
  const categories = useCategories()
  const skills = useSkills()
  const [editing, setEditing] = useState<Editing | null>(null)
  const [deleting, setDeleting] = useState<{ kind: 'category' | 'skill'; id: number; name: string } | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['categories'] })
    queryClient.invalidateQueries({ queryKey: ['skills'] })
  }

  const remove = useMutation({
    mutationFn: (target: { kind: 'category' | 'skill'; id: number }) =>
      target.kind === 'category' ? adminApi.deleteCategory(target.id) : adminApi.deleteSkill(target.id),
    onSuccess: () => {
      refresh()
      toast.success('Đã xoá')
      setDeleting(null)
    },
    onError: (e) => {
      toast.error(e.message)
      setDeleting(null)
    },
  })

  return (
    <>
      <AdminHeader
        title="Danh mục & nhóm kỹ năng"
        description="Tiêu chí phân loại sản phẩm dùng cho bộ lọc, hồ sơ kỹ năng và gợi ý."
      />
      <div className="grid gap-5 xl:grid-cols-2">
        <section className="card">
          <div className="flex items-center justify-between border-b border-line p-4">
            <h2 className="font-bold">Danh mục sản phẩm</h2>
            <Button size="sm" onClick={() => setEditing({ kind: 'category', id: null, name: '', description: '' })}>
              + Thêm danh mục
            </Button>
          </div>
          {categories.isPending ? (
            <PageLoader />
          ) : (
            <ul className="divide-y divide-line">
              {categories.data?.map((c) => (
                <li key={c.id} className="flex items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">{c.name}</p>
                    <p className="truncate text-[12.5px] text-ink-muted">
                      {c.productCount} sản phẩm đang bán{c.description && ` · ${c.description}`}
                    </p>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() =>
                      setEditing({ kind: 'category', id: c.id, name: c.name, description: c.description ?? '' })
                    }
                  >
                    Sửa
                  </Button>
                  <Button
                    size="sm"
                    variant="danger-outline"
                    onClick={() => setDeleting({ kind: 'category', id: c.id, name: c.name })}
                  >
                    Xoá
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card">
          <div className="flex items-center justify-between border-b border-line p-4">
            <h2 className="font-bold">Nhóm kỹ năng</h2>
            <Button
              size="sm"
              onClick={() => setEditing({ kind: 'skill', id: null, code: '', name: '', description: '' })}
            >
              + Thêm nhóm
            </Button>
          </div>
          {skills.isPending ? (
            <PageLoader />
          ) : (
            <ul className="divide-y divide-line">
              {skills.data?.map((s) => (
                <li key={s.id} className="flex items-center gap-3 px-4 py-3">
                  <span
                    className="h-8 w-8 shrink-0 rounded-md"
                    style={{ background: skillTheme(s.code).color }}
                    aria-hidden
                  />
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">
                      {s.name} <span className="ml-1 font-mono text-[12px] text-ink-muted">{s.code}</span>
                    </p>
                    <p className="truncate text-[12.5px] text-ink-muted">{s.description}</p>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() =>
                      setEditing({
                        kind: 'skill',
                        id: s.id,
                        code: s.code,
                        name: s.name,
                        description: s.description ?? '',
                      })
                    }
                  >
                    Sửa
                  </Button>
                  <Button
                    size="sm"
                    variant="danger-outline"
                    onClick={() => setDeleting({ kind: 'skill', id: s.id, name: s.name })}
                  >
                    Xoá
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <p className="border-t border-line px-4 py-3 text-[12.5px] text-ink-muted">
            Chỉ xoá được nhóm chưa gắn với sản phẩm hay hồ sơ bé nào. Mã nhóm cố định sau khi tạo.
          </p>
        </section>
      </div>

      {editing && <EditModal editing={editing} onClose={() => setEditing(null)} onSaved={refresh} />}
      <ConfirmDialog
        open={Boolean(deleting)}
        title={`Xoá “${deleting?.name}”?`}
        message={
          deleting?.kind === 'category'
            ? 'Chỉ xoá được danh mục không còn sản phẩm.'
            : 'Nhóm kỹ năng sẽ bị xoá khỏi hệ thống.'
        }
        confirmLabel="Xoá"
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(null)}
      />
    </>
  )
}

function EditModal({ editing, onClose, onSaved }: { editing: Editing; onClose: () => void; onSaved: () => void }) {
  const [draft, setDraft] = useState(editing)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const save = useMutation({
    mutationFn: (): Promise<unknown> => {
      if (draft.kind === 'category') {
        const body = { name: draft.name, description: draft.description }
        return draft.id ? adminApi.updateCategory(draft.id, body) : adminApi.createCategory(body)
      }
      const body = { code: draft.code, name: draft.name, description: draft.description }
      return draft.id ? adminApi.updateSkill(draft.id, body) : adminApi.createSkill(body)
    },
    onSuccess: () => {
      onSaved()
      toast.success('Đã lưu')
      onClose()
    },
    onError: (e) => setError(e.message),
  })

  const submit = () => {
    if (!draft.name.trim()) return setError('Vui lòng nhập tên')
    if (draft.kind === 'skill' && !/^[A-Z][A-Z_]{1,29}$/.test(draft.code)) {
      return setError('Mã gồm 2–30 chữ in hoa hoặc dấu gạch dưới, VD: CRITICAL_THINKING')
    }
    setError(null)
    save.mutate()
  }

  const isSkill = draft.kind === 'skill'
  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`${draft.id ? 'Sửa' : 'Thêm'} ${isSkill ? 'nhóm kỹ năng' : 'danh mục'}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button loading={save.isPending} onClick={submit}>
            Lưu
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <p className="rounded-md bg-danger-soft px-3 py-2 text-[13px] text-danger">{error}</p>}
        {isSkill && (
          <Field label="Mã nhóm" htmlFor="scode" hint={draft.id ? 'Không đổi được mã.' : 'VD: CRITICAL_THINKING'}>
            <Input
              id="scode"
              value={draft.code}
              disabled={Boolean(draft.id)}
              onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })}
            />
          </Field>
        )}
        <Field label="Tên" htmlFor="ename" required>
          <Input
            id="ename"
            value={draft.name}
            maxLength={100}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
        </Field>
        <Field label="Mô tả" htmlFor="edesc">
          <Textarea
            id="edesc"
            value={draft.description}
            maxLength={1000}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
          />
        </Field>
      </div>
    </Modal>
  )
}
