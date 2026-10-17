import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, toApiError } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Field, Input, PasswordInput } from '@/components/ui/Field'
import { LockIcon, MailIcon } from '@/components/ui/FormIcons'
import { useToast } from '@/components/ui/Toast'
import { AuthShell } from './AuthShell'
import { GoogleSignInButton } from './GoogleSignInButton'
import { OtpLoginForm } from './OtpLoginForm'

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const { login, loginWithGoogle, session } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const from = (location.state as { from?: string } | null)?.from
  const [error, setError] = useState<ApiError | null>(null)
  // email của tài khoản vừa bị từ chối vì bị khoá; đăng nhập Google không biết trước email nên để trống
  const [lockedEmail, setLockedEmail] = useState('')
  const [googleBusy, setGoogleBusy] = useState(false)
  // cách đăng nhập bằng email: mật khẩu hoặc mã 6 số gửi qua email
  const [mode, setMode] = useState<'password' | 'otp'>('password')

  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  if (session && !form.formState.isSubmitting && !googleBusy) {
    return <Navigate to={session.role === 'ADMIN' ? '/admin' : (from ?? '/')} replace />
  }

  const goHome = (role: string) => navigate(role === 'ADMIN' ? '/admin' : (from ?? '/'), { replace: true })

  const onGoogleCredential = async (idToken: string) => {
    setError(null)
    setGoogleBusy(true)
    try {
      goHome((await loginWithGoogle(idToken)).role)
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.rawMessage === 'Account is locked') {
        setLockedEmail('')
        setError(apiError)
      } else {
        toast.error(apiError.message)
      }
    } finally {
      setGoogleBusy(false)
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    try {
      goHome((await login(values.email, values.password)).role)
    } catch (e) {
      const apiError = toApiError(e)
      // sai thông tin đăng nhập: báo ngay dưới ô mật khẩu. Backend cố ý không nói sai email hay sai mật khẩu
      // (tránh dò email nào đã có tài khoản) nên giữ nguyên câu chung.
      if (apiError.rawMessage === 'Incorrect email or password') {
        form.setError('password', { message: apiError.message }, { shouldFocus: true })
      } else {
        setLockedEmail(values.email)
        setError(apiError)
      }
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
          {lockedEmail ? (
            <>
              Tài khoản <b>{lockedEmail}</b> đã bị quản trị viên khoá.
            </>
          ) : (
            'Tài khoản này đã bị quản trị viên khoá.'
          )}{' '}
          Nếu cho rằng đây là nhầm lẫn, vui lòng liên hệ bộ phận hỗ trợ của BrainBlocks.
        </p>
        <Button className="mt-6" variant="secondary" block onClick={() => setError(null)}>
          Đăng nhập tài khoản khác
        </Button>
        <ButtonLink to="/" variant="ghost" block className="mt-2">
          Quay lại cửa hàng
        </ButtonLink>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1 className="h1">Chào mừng trở lại</h1>
      <p className="mt-2 text-balance text-[14.5px] text-ink-2">
        Theo dõi đơn hàng và nhận gợi&nbsp;ý STEM phù hợp với từng bé.
      </p>

      {error && (
        <div
          role="alert"
          className="mt-6 rounded-md border border-[#FBCFCF] bg-danger-soft px-4 py-3 text-[14px] text-danger"
        >
          {error.message}
        </div>
      )}

      {mode === 'otp' ? (
        <OtpLoginForm
          onSuccess={(res) => goHome(res.role)}
          onLocked={(email) => {
            setLockedEmail(email)
            setError(new ApiError(403, 'Account is locked'))
          }}
        />
      ) : (
        <form onSubmit={onSubmit} noValidate className="mt-6 space-y-4">
          <Field label="Email" htmlFor="email" error={form.formState.errors.email?.message}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="phuhuynh@email.vn"
              leading={<MailIcon />}
              invalid={Boolean(form.formState.errors.email)}
              {...form.register('email')}
            />
          </Field>
          <Field label="Mật khẩu" htmlFor="password" error={form.formState.errors.password?.message}>
            <PasswordInput
              id="password"
              autoComplete="current-password"
              placeholder="Nhập mật khẩu"
              leading={<LockIcon />}
              invalid={Boolean(form.formState.errors.password)}
              {...form.register('password')}
            />
          </Field>
          <p className="-mt-2 text-right text-[13.5px] font-medium">
            <Link
              to="/forgot-password"
              state={{ email: form.getValues('email') }}
              className="underline-offset-2 hover:underline"
            >
              Quên mật khẩu?
            </Link>
          </p>
          <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </Button>
        </form>
      )}

      <div className="my-4 flex items-center gap-3 text-[13px] text-ink-muted">
        <span className="h-px flex-1 bg-line" aria-hidden />
        hoặc
        <span className="h-px flex-1 bg-line" aria-hidden />
      </div>

      {/* nút Google (ẩn khi chưa cấu hình VITE_GOOGLE_CLIENT_ID), rồi nút chuyển giữa mật khẩu và mã qua email */}
      <div aria-busy={googleBusy} className={googleBusy ? 'pointer-events-none opacity-60' : undefined}>
        <GoogleSignInButton
          onCredential={onGoogleCredential}
          onError={() => toast.error('Không đăng nhập được bằng Google, vui lòng thử lại.')}
        />
      </div>
      <button
        type="button"
        onClick={() => {
          setError(null)
          setMode(mode === 'password' ? 'otp' : 'password')
        }}
        className="mx-auto mt-2 block rounded-md px-2 py-1.5 text-[14.5px] font-medium text-primary underline-offset-2 hover:text-primary-hover hover:underline"
      >
        {mode === 'password' ? 'Đăng nhập bằng mã qua email' : 'Đăng nhập bằng mật khẩu'}
      </button>

      <p className="mt-4 text-center text-[14px] text-ink-muted">
        Chưa có tài khoản?{' '}
        <Link
          to="/register"
          state={location.state}
          className="font-semibold underline-offset-2 hover:underline"
        >
          Tạo tài khoản
        </Link>
      </p>
    </AuthShell>
  )
}
