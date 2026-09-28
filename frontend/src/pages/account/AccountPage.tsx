import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { meApi } from '@/api/endpoints'
import type { UserProfile } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Field, Input, Textarea } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { AccountShell } from './AccountShell'

// khớp UpdateProfileRequest: điện thoại để trống hoặc 9-14 chữ số, có thể bắt đầu bằng +
const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ và tên').max(100, 'Tối đa 100 ký tự'),
  phone: z
    .string()
    .trim()
    .regex(/^$|^\+?[0-9]{9,14}$/, 'Số điện thoại gồm 9–14 chữ số'),
  defaultAddress: z.string().trim().max(255, 'Tối đa 255 ký tự'),
})

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: z.string().min(8, 'Tối thiểu 8 ký tự').max(72, 'Tối đa 72 ký tự'),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, { message: 'Mật khẩu nhập lại không khớp', path: ['confirm'] })

export default function AccountPage() {
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })
  return (
    <AccountShell>
      <h1 className="h1 mb-6">Thông tin tài khoản</h1>
      {me.isPending ? (
        <PageLoader />
      ) : me.isError ? (
        <ErrorState message={me.error.message} onRetry={() => me.refetch()} />
      ) : (
        <div className="space-y-6">
          <ProfileForm profile={me.data} />
          <PasswordForm />
        </div>
      )}
    </AccountShell>
  )
}

function ProfileForm({ profile }: { profile: UserProfile }) {
  const { isCustomer } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const form = useForm<z.infer<typeof profileSchema>>({ resolver: zodResolver(profileSchema) })
  const errors = form.formState.errors

  useEffect(() => {
    form.reset({ fullName: profile.fullName, phone: profile.phone ?? '', defaultAddress: profile.defaultAddress ?? '' })
  }, [profile, form])

  const save = useMutation({
    mutationFn: meApi.update,
    onSuccess: (data) => {
      queryClient.setQueryData(['me'], data)
      toast.success('Đã lưu thông tin')
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <form onSubmit={form.handleSubmit((v) => save.mutate(v))} noValidate className="card p-6">
      <h2 className="h2 mb-5">Thông tin cá nhân</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Họ và tên" htmlFor="fullName" error={errors.fullName?.message} required>
          <Input id="fullName" invalid={Boolean(errors.fullName)} {...form.register('fullName')} />
        </Field>
        <Field label="Email" htmlFor="email" hint="Email dùng để đăng nhập, không thay đổi được.">
          <Input id="email" value={profile.email} disabled readOnly />
        </Field>
        <Field label="Số điện thoại" htmlFor="phone" error={errors.phone?.message}>
          <Input id="phone" type="tel" inputMode="tel" invalid={Boolean(errors.phone)} {...form.register('phone')} />
        </Field>
        {isCustomer && (
          <Field
            label="Địa chỉ giao hàng mặc định"
            htmlFor="defaultAddress"
            error={errors.defaultAddress?.message}
            className="sm:col-span-2"
            hint="Tự điền sẵn ở bước thanh toán."
          >
            <Textarea id="defaultAddress" className="!min-h-[72px]" {...form.register('defaultAddress')} />
          </Field>
        )}
      </div>
      <div className="mt-6 flex justify-end gap-2.5">
        <Button variant="secondary" disabled={!form.formState.isDirty} onClick={() => form.reset()}>
          Huỷ
        </Button>
        <Button type="submit" loading={save.isPending} disabled={!form.formState.isDirty}>
          Lưu thay đổi
        </Button>
      </div>
    </form>
  )
}

function PasswordForm() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const form = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  })
  const errors = form.formState.errors

  const change = useMutation({
    mutationFn: (v: z.infer<typeof passwordSchema>) =>
      meApi.changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword }),
    // backend vô hiệu hoá mọi token cũ sau khi đổi mật khẩu, nên đăng xuất và đăng nhập lại
    onSuccess: () => {
      toast.success('Đã đổi mật khẩu, vui lòng đăng nhập lại')
      logout()
      navigate('/login')
    },
    onError: (e) => {
      if (e.rawMessage === 'Current password is incorrect') {
        form.setError('currentPassword', { message: e.message })
      } else {
        toast.error(e.message)
      }
    },
  })

  return (
    <form onSubmit={form.handleSubmit((v) => change.mutate(v))} noValidate className="card p-6">
      <h2 className="h2 mb-5">Đổi mật khẩu</h2>
      <div className="grid max-w-md gap-4">
        <Field label="Mật khẩu hiện tại" htmlFor="currentPassword" error={errors.currentPassword?.message}>
          <Input
            id="currentPassword"
            type="password"
            autoComplete="current-password"
            invalid={Boolean(errors.currentPassword)}
            {...form.register('currentPassword')}
          />
        </Field>
        <Field label="Mật khẩu mới" htmlFor="newPassword" error={errors.newPassword?.message}>
          <Input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(errors.newPassword)}
            {...form.register('newPassword')}
          />
        </Field>
        <Field label="Nhập lại mật khẩu mới" htmlFor="confirm" error={errors.confirm?.message}>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(errors.confirm)}
            {...form.register('confirm')}
          />
        </Field>
      </div>
      <Button type="submit" className="mt-6" loading={change.isPending}>
        Cập nhật mật khẩu
      </Button>
    </form>
  )
}
