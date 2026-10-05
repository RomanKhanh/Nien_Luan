import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toApiError, type ApiError } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { AuthShell } from './AuthShell'

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const { login, session } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const [error, setError] = useState<ApiError | null>(null)

  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  if (session && !form.formState.isSubmitting) {
    return <Navigate to={session.role === 'ADMIN' ? '/admin' : (from ?? '/')} replace />
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      const res = await login(values.email, values.password)
      navigate(res.role === 'ADMIN' ? '/admin' : (from ?? '/'), { replace: true })
    } catch (e) {
      setError(toApiError(e))
    }
  })

  // backend trả 403 "Account is locked" cho tài khoản bị admin khoá
  if (error?.rawMessage === 'Account is locked') {
    return (
      <AuthShell>
        <div
          className="grid h-14 w-14 place-items-center rounded-lg bg-danger-soft text-[24px] text-danger"
          aria-hidden
        >
          ⛔
        </div>
        <h1 className="h1 mt-5">Tài khoản của bạn đang tạm bị khoá</h1>
        <p className="mt-3 text-ink-2">
          Tài khoản <b>{form.getValues('email')}</b> đã bị quản trị viên khoá. Nếu cho rằng đây là nhầm lẫn, vui lòng
          liên hệ bộ phận hỗ trợ của BrainBlocks.
        </p>
        <Button className="mt-6" variant="secondary" block onClick={() => setError(null)}>
          Đăng nhập tài khoản khác
        </Button>
        <ButtonLink to="/" variant="ghost" block className="mt-2">
          Về trang chủ
        </ButtonLink>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1 className="h1">Chào mừng trở lại</h1>
      <p className="mt-1.5 text-[14px] text-ink-muted">
        Chưa có tài khoản?{' '}
        <Link to="/register" state={location.state}>
          Đăng ký
        </Link>
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
        <Field label="Email" htmlFor="email" error={form.formState.errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="phuhuynh@email.vn"
            invalid={Boolean(form.formState.errors.email)}
            {...form.register('email')}
          />
        </Field>
        <Field label="Mật khẩu" htmlFor="password" error={form.formState.errors.password?.message}>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            invalid={Boolean(form.formState.errors.password)}
            {...form.register('password')}
          />
        </Field>
        <p className="-mt-1 text-right text-[13.5px]">
          <Link to="/forgot-password" state={{ email: form.getValues('email') }}>
            Quên mật khẩu?
          </Link>
        </p>
        <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
          Đăng nhập
        </Button>
      </form>
    </AuthShell>
  )
}
