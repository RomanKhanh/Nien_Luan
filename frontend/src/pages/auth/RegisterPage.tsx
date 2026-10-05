import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toApiError } from '@/api/client'
import { authApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { useToast } from '@/components/ui/Toast'
import { newPasswordRule } from '@/lib/password'
import { AuthShell } from './AuthShell'

// thời gian chờ gửi lại mã, khớp app.registration.code-cooldown-seconds ở backend
const RESEND_SECONDS = 60
// lỗi về mã xác nhận (backend) hiện ngay dưới ô nhập mã thay vì khung lỗi chung
const CODE_ERRORS = [
  'Verification code is incorrect',
  'Verification code is invalid or has expired',
  'Too many wrong codes, please request a new code',
]

// khớp RegisterRequest ở backend: họ tên <= 100, mật khẩu 8-72 ký tự, mã xác nhận 6 số gửi tới email
const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Vui lòng nhập họ và tên').max(100, 'Tối đa 100 ký tự'),
    email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
    verificationCode: z
      .string()
      .trim()
      .regex(/^\d{6}$/, 'Nhập mã 6 số trong email'),
    password: newPasswordRule,
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
  // email đã được gửi mã; đổi email thì phải gửi mã lại
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)
  const [sending, setSending] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', verificationCode: '', password: '', confirm: '' },
  })
  const errors = form.formState.errors
  const email = useWatch({ control: form.control, name: 'email' }).trim()
  const codeSentToThisEmail = sentTo !== null && sentTo.toLowerCase() === email.toLowerCase()

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  if (session && !form.formState.isSubmitting) return <Navigate to="/" replace />

  const sendCode = async () => {
    setError(null)
    if (!(await form.trigger('email'))) return
    setSending(true)
    try {
      await authApi.sendVerificationCode(email)
      setSentTo(email)
      setCooldown(RESEND_SECONDS)
      form.setValue('verificationCode', '')
      form.clearErrors('verificationCode')
      toast.success(`Đã gửi mã xác nhận tới ${email}`)
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.status === 409) form.setError('email', { message: apiError.message })
      else setError(apiError.message)
    } finally {
      setSending(false)
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    if (!codeSentToThisEmail) {
      form.setError('verificationCode', { message: 'Bấm "Gửi mã" để nhận mã xác nhận cho email này' })
      return
    }
    try {
      await authApi.register({
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        verificationCode: values.verificationCode,
      })
      // đăng ký xong đăng nhập luôn cho liền mạch
      await login(values.email, values.password)
      toast.success('Tạo tài khoản thành công')
      navigate(from ?? '/children', { replace: true })
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.status === 409) {
        form.setError('email', { message: apiError.message })
      } else if (CODE_ERRORS.includes(apiError.rawMessage ?? '')) {
        form.setError('verificationCode', { message: apiError.message })
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
        <Field
          label="Email"
          htmlFor="email"
          error={errors.email?.message}
          hint="Dùng email bạn mở được: mã xác nhận và link lấy lại mật khẩu sẽ gửi tới đây."
        >
          <div className="flex gap-2">
            <Input
              id="email"
              type="email"
              autoComplete="email"
              className="min-w-0 flex-1"
              invalid={Boolean(errors.email)}
              {...form.register('email')}
            />
            <Button
              variant="secondary"
              className="shrink-0"
              loading={sending}
              disabled={codeSentToThisEmail && cooldown > 0}
              onClick={sendCode}
            >
              {codeSentToThisEmail ? (cooldown > 0 ? `Gửi lại (${cooldown}s)` : 'Gửi lại mã') : 'Gửi mã'}
            </Button>
          </div>
        </Field>
        <Field
          label="Mã xác nhận"
          htmlFor="verificationCode"
          error={errors.verificationCode?.message}
          hint={
            codeSentToThisEmail
              ? `Nhập mã 6 số vừa gửi tới ${sentTo}. Mã có hiệu lực 10 phút; không thấy thì xem thư mục Thư rác.`
              : 'Bấm "Gửi mã" để nhận mã 6 số qua email.'
          }
        >
          <Input
            id="verificationCode"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="______"
            className="font-mono tracking-[0.4em]"
            invalid={Boolean(errors.verificationCode)}
            {...form.register('verificationCode')}
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
