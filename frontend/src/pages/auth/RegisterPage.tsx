import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toApiError } from '@/api/client'
import { authApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { useToast } from '@/components/ui/Toast'
import { AuthShell } from './AuthShell'

// khớp RegisterRequest ở backend: họ tên <= 100, mật khẩu 8-72 ký tự
const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Vui lòng nhập họ và tên').max(100, 'Tối đa 100 ký tự'),
    email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
    password: z
      .string()
      .min(8, 'Tối thiểu 8 ký tự')
      .max(72, 'Tối đa 72 ký tự')
      .regex(/[A-Za-z]/, 'Cần có ít nhất một chữ cái')
      .regex(/[0-9]/, 'Cần có ít nhất một chữ số'),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { message: 'Mật khẩu nhập lại không khớp', path: ['confirm'] })
type FormValues = z.infer<typeof schema>

export default function RegisterPage() {
  const { login, session } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const toast = useToast()
  const [error, setError] = useState<string | null>(null)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', confirm: '' },
  })
  const errors = form.formState.errors

  if (session && !form.formState.isSubmitting) return <Navigate to="/" replace />

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      await authApi.register({ fullName: values.fullName, email: values.email, password: values.password })
      // đăng ký xong đăng nhập luôn cho liền mạch
      await login(values.email, values.password)
      toast.success('Tạo tài khoản thành công. Bạn được tặng 3 voucher, xem trong Ví voucher.')
      navigate(from ?? '/children', { replace: true })
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.status === 409) {
        form.setError('email', { message: apiError.message })
      } else {
        setError(apiError.message)
      }
    }
  })

  return (
    <AuthShell>
      <h1 className="h1">Tạo tài khoản</h1>
      <p className="mt-1.5 text-[14px] text-ink-muted">
        Đã có tài khoản?{' '}
        <Link to="/login" state={location.state}>
          Đăng nhập
        </Link>
      </p>
      {error && (
        <div
          role="alert"
          className="mt-6 rounded-md border border-[#FBCFCF] bg-danger-soft px-4 py-3 text-[14px] text-danger"
        >
          {error}
        </div>
      )}
      <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
        <Field label="Họ và tên" htmlFor="fullName" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" invalid={Boolean(errors.fullName)} {...form.register('fullName')} />
        </Field>
        <Field label="Email" htmlFor="email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            invalid={Boolean(errors.email)}
            {...form.register('email')}
          />
        </Field>
        <Field
          label="Mật khẩu"
          htmlFor="password"
          error={errors.password?.message}
          hint="Tối thiểu 8 ký tự, gồm chữ và số"
        >
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(errors.password)}
            {...form.register('password')}
          />
        </Field>
        <Field label="Nhập lại mật khẩu" htmlFor="confirm" error={errors.confirm?.message}>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(errors.confirm)}
            {...form.register('confirm')}
          />
        </Field>
        <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
          Tạo tài khoản
        </Button>
      </form>
    </AuthShell>
  )
}
