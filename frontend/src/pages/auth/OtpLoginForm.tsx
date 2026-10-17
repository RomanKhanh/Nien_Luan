import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { authApi } from '@/api/endpoints'
import { toApiError } from '@/api/client'
import type { LoginResponse } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { KeyIcon, MailIcon } from '@/components/ui/FormIcons'
import { useToast } from '@/components/ui/Toast'

// khớp app.login-otp.cooldown-seconds và app.login-otp.expiry-minutes ở backend
const RESEND_SECONDS = 60
const CODE_VALID_MINUTES = 5

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, 'Nhập mã 6 số trong email'),
})
type FormValues = z.infer<typeof schema>

// Đăng nhập bằng mã 6 số gửi qua email: nhập email → gửi mã (60 giây mới gửi lại được) → nhập mã → đăng nhập.
// Lỗi từ backend báo bằng toast; tài khoản bị khoá giao cho trang đăng nhập hiện màn hình riêng.
export function OtpLoginForm({
  onSuccess,
  onLocked,
}: {
  onSuccess: (res: LoginResponse) => void
  onLocked: (email: string) => void
}) {
  const { loginWithOtp } = useAuth()
  const toast = useToast()
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [cooldown, setCooldown] = useState(0)

  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: '', code: '' } })
  const errors = form.formState.errors
  const email = useWatch({ control: form.control, name: 'email' }).trim()
  const codeSentToThisEmail = sentTo !== null && sentTo.toLowerCase() === email.toLowerCase()

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const sendCode = async () => {
    if (!(await form.trigger('email'))) return
    setSending(true)
    try {
      await authApi.sendLoginOtp(email)
      setSentTo(email)
      setCooldown(RESEND_SECONDS)
      form.setValue('code', '')
      form.clearErrors('code')
      form.setFocus('code')
      // backend không cho biết email có tài khoản hay không, nên câu báo cũng không khẳng định
      toast.success(`Nếu ${email} đã đăng ký, mã đăng nhập vừa được gửi tới hộp thư này.`)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setSending(false)
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    if (!codeSentToThisEmail) {
      form.setError('code', { message: 'Bấm "Gửi mã" để nhận mã đăng nhập cho email này' })
      return
    }
    try {
      onSuccess(await loginWithOtp(values.email, values.code))
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.rawMessage === 'Account is locked') {
        onLocked(values.email)
      } else {
        toast.error(apiError.message)
      }
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
      <Field label="Email" htmlFor="otp-email" error={errors.email?.message}>
        <div className="flex gap-2">
          <div className="min-w-0 flex-1">
            <Input
              id="otp-email"
              type="email"
              autoComplete="email"
              placeholder="phuhuynh@email.vn"
              leading={<MailIcon />}
              invalid={Boolean(errors.email)}
              {...form.register('email')}
            />
          </div>
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
        label="Mã đăng nhập"
        htmlFor="otp-code"
        error={errors.code?.message}
        hint={
          codeSentToThisEmail
            ? `Nhập mã 6 số trong email. Mã có hiệu lực ${CODE_VALID_MINUTES} phút; không thấy thì xem thư mục Thư rác.`
            : 'Bấm "Gửi mã" để nhận mã 6 số qua email.'
        }
      >
        <Input
          id="otp-code"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="______"
          leading={<KeyIcon />}
          className="font-mono tracking-[0.4em]"
          invalid={Boolean(errors.code)}
          {...form.register('code')}
        />
      </Field>
      <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
        {form.formState.isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
      </Button>
    </form>
  )
}
