import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { childApi } from '@/api/endpoints'
import type { ChildProfile, Gender } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Chip, Field, Input, Textarea } from '@/components/ui/Field'
import { ConfirmDialog, Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import { useSkills } from '@/features/catalog/queries'
import { GENDER } from '@/lib/labels'
import { childKeys } from './keys'

const today = () => new Date().toISOString().slice(0, 10)

// khớp ChildProfileRequest: tên <= 100, ngày sinh trong quá khứ, ghi chú <= 1000
const schema = z.object({
  name: z.string().trim().min(1, 'Vui lòng nhập tên gọi của bé').max(100, 'Tối đa 100 ký tự'),
  birthDate: z
    .string()
    .min(1, 'Vui lòng chọn ngày sinh')
    .refine((v) => v < today(), 'Ngày sinh không hợp lệ'),
  note: z.string().max(1000, 'Tối đa 1000 ký tự'),
})
type FormValues = z.infer<typeof schema>

interface Props {
  open: boolean
  onClose: () => void
  child?: ChildProfile | null
  onDeleted?: () => void
}

// chỉ mount form khi mở, key theo hồ sơ để mỗi lần mở form lấy lại dữ liệu mới nhất
export function ChildFormModal(props: Props) {
  return props.open ? <ChildForm key={props.child?.id ?? 'new'} {...props} /> : null
}

function ChildForm({ open, onClose, child, onDeleted }: Props) {
  const skills = useSkills()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [gender, setGender] = useState<Gender | null>(child?.gender ?? null)
  const [skillIds, setSkillIds] = useState<number[]>(child?.interestedSkills.map((s) => s.id) ?? [])
  const [confirmDelete, setConfirmDelete] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: child?.name ?? '', birthDate: child?.birthDate ?? '', note: child?.note ?? '' },
  })
  const errors = form.formState.errors

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const body = {
        name: values.name,
        birthDate: values.birthDate,
        gender,
        note: values.note.trim() || null,
        interestedSkillIds: skillIds,
      }
      return child ? childApi.update(child.id, body) : childApi.create(body)
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: childKeys.list })
      queryClient.setQueryData(childKeys.detail(saved.id), saved)
      // nhóm quan tâm đổi thì gợi ý đổi theo
      queryClient.invalidateQueries({ queryKey: childKeys.recommendations(saved.id) })
      toast.success(child ? 'Đã lưu hồ sơ bé' : `Đã tạo hồ sơ cho bé ${saved.name}`)
      onClose()
    },
    onError: (e) => toast.error(e.message),
  })

  const remove = useMutation({
    mutationFn: () => childApi.remove(child!.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: childKeys.list })
      toast.success('Đã xoá hồ sơ bé')
      setConfirmDelete(false)
      onClose()
      onDeleted?.()
    },
    onError: (e) => toast.error(e.message),
  })

  const toggleSkill = (id: number) =>
    setSkillIds((prev) => (prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]))

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        variant="drawer"
        title={child ? 'Sửa hồ sơ bé' : 'Thêm hồ sơ bé'}
        footer={
          <div className="flex w-full gap-2.5">
            {child && (
              <Button variant="danger-outline" onClick={() => setConfirmDelete(true)}>
                Xoá hồ sơ
              </Button>
            )}
            <Button className="flex-1" loading={save.isPending} onClick={form.handleSubmit((v) => save.mutate(v))}>
              Lưu hồ sơ
            </Button>
          </div>
        }
      >
        <form className="space-y-5" noValidate onSubmit={form.handleSubmit((v) => save.mutate(v))}>
          <Field label="Tên gọi của bé" htmlFor="childName" required error={errors.name?.message}>
            <Input id="childName" placeholder="VD: Minh An" invalid={Boolean(errors.name)} {...form.register('name')} />
          </Field>
          <Field label="Ngày sinh" htmlFor="birthDate" required error={errors.birthDate?.message}>
            <Input
              id="birthDate"
              type="date"
              max={today()}
              invalid={Boolean(errors.birthDate)}
              {...form.register('birthDate')}
            />
          </Field>
          <Field label="Giới tính">
            <div className="flex flex-wrap gap-2">
              {(Object.keys(GENDER) as Gender[]).map((g) => (
                <Chip key={g} selected={gender === g} onClick={() => setGender(gender === g ? null : g)}>
                  {GENDER[g]}
                </Chip>
              ))}
            </div>
          </Field>
          <Field label="Nhóm kỹ năng quan tâm" hint="Dùng để ưu tiên gợi ý đồ chơi cho bé.">
            <div className="flex flex-wrap gap-2">
              {(skills.data ?? []).map((s) => (
                <Chip key={s.id} selected={skillIds.includes(s.id)} onClick={() => toggleSkill(s.id)}>
                  {s.name}
                </Chip>
              ))}
            </div>
          </Field>
          <Field label="Ghi chú (không bắt buộc)" htmlFor="note" error={errors.note?.message}>
            <Textarea id="note" placeholder="VD: Bé thích tô màu, xếp hình khối lớn." {...form.register('note')} />
          </Field>
          <p className="rounded-md bg-muted px-4 py-3 text-[13px] text-ink-muted">
            Thông tin của bé chỉ dùng để gợi ý đồ chơi và không chia sẻ cho bên thứ ba.
          </p>
          {/* Enter trong ô nhập cũng gửi form */}
          <button type="submit" hidden />
        </form>
      </Modal>
      {child && (
        <ConfirmDialog
          open={confirmDelete}
          title={`Xoá hồ sơ bé ${child.name}?`}
          message="Toàn bộ dữ liệu lộ trình kỹ năng của bé sẽ bị xoá và không khôi phục được. Lịch sử đơn hàng vẫn được giữ."
          confirmLabel="Xoá hồ sơ"
          loading={remove.isPending}
          onConfirm={() => remove.mutate()}
          onClose={() => setConfirmDelete(false)}
        />
      )}
    </>
  )
}
