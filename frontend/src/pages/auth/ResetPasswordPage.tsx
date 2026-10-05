import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/api/endpoints'
import { toApiError, type ApiError } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { useToast } from '@/components/ui/Toast'
import { newPasswordRule } from '@/lib/password'
import { AuthShell } from './AuthShell'

const schema = z
  .object({ password: newPasswordRule, confirm: z.string() })
  .refine((v) => v.password === v.confirm, { message: 'Mật khẩu nhập lại không khớp', path: ['confirm'] })
type FormValues = z.infer<typeof schema>

// Đặt mật khẩu mới từ link trong email (/reset-password?token=...). Đặt xong thì mọi phiên đăng nhập cũ
// bị đăng xuất (backend tăng tokenVersion), nên xoá phiên trên máy này rồi chuyển sang đăng nhập.
export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [error, setError] = useState<ApiError | null>(null)
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { password: '', confirm: '' } })
  const errors = form.formState.errors

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await authApi.resetPassword(token, values.password)
      if (session) logout()
      toast.success('Đã đặt mật khẩu mới, hãy đăng nhập lại')
      navigate('/login', { replace: true })
    } catch (e) {
      setError(toApiError(e))
    }
  })

  // link thiếu token, hoặc backend báo link sai / hết hạn / đã dùng
  if (!token || error?.rawMessage === 'Password reset link is invalid or has expired') {
    return (
      <AuthShell>
        <div className="grid h-14 w-14 place-items-center rounded-lg bg-warning-soft text-[26px]" aria-hidden>
          ⏳
        </div>
        <h1 className="h1 mt-5">Link không còn dùng được</h1>
        <p className="mt-3 text-ink-2">
          Link đặt lại mật khẩu không hợp lệ, đã hết hạn (30 phút) hoặc đã được dùng. Bạn hãy yêu cầu một link mới.
        </p>
        <ButtonLink to="/forgot-password" block className="mt-6">
          Gửi lại link đặt lại mật khẩu
        </ButtonLink>
        <ButtonLink to="/login" variant="ghost" block className="mt-2">
          Quay lại đăng nhập
        </ButtonLink>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1 className="h1">Đặt mật khẩu mới</h1>
      <p className="mt-1.5 text-[14px] text-ink-muted">
        Sau khi đặt, bạn sẽ được đăng xuất khỏi mọi thiết bị và đăng nhập lại bằng mật khẩu mới.
      </p>

      {error && (
        <div
          role="alert"
          className="mt-6 rounded-md border border-[#FBCFCF] bg-danger-soft px-4 py-3 text-[14px] text-danger"
        >
          {error.message}
        </div>
      )}

      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
        <Field
          label="Mật khẩu mới"
          htmlFor="password"
          hint="Tối thiểu 8 ký tự, có cả chữ và số."
          error={errors.password?.message}
        >
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(errors.password)}
            {...form.register('password')}
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
        <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
          Đặt mật khẩu mới
        </Button>
      </form>
    </AuthShell>
  )
}
