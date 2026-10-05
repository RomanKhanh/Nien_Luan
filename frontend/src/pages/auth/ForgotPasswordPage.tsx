import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useLocation } from 'react-router-dom'
import { authApi } from '@/api/endpoints'
import { toApiError } from '@/api/client'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'
import { AuthShell } from './AuthShell'

const schema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
})
type FormValues = z.infer<typeof schema>

// Gửi link đặt lại mật khẩu. Email chưa có tài khoản: báo dưới ô email kèm link đăng ký (backend trả 404);
// tài khoản bị khoá hoặc vừa yêu cầu chưa đủ 1 phút: báo ở khung lỗi chung.
export default function ForgotPasswordPage() {
  const location = useLocation()
  const initialEmail = (location.state as { email?: string } | null)?.email ?? ''
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: initialEmail } })

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null)
    setNotFound(false)
    try {
      await authApi.forgotPassword(values.email)
      setSentTo(values.email)
    } catch (e) {
      const apiError = toApiError(e)
      if (apiError.status === 404) setNotFound(true)
      else setError(apiError.message)
    }
  })

  if (sentTo) {
    return (
      <AuthShell>
        <div className="grid h-14 w-14 place-items-center rounded-lg bg-sky-soft text-[26px]" aria-hidden>
          ✉️
        </div>
        <h1 className="h1 mt-5">Kiểm tra hộp thư của bạn</h1>
        <p className="mt-3 text-ink-2">
          BrainBlocks vừa gửi link đặt lại mật khẩu tới <b>{sentTo}</b>. Link hết hạn sau 30 phút và chỉ dùng được 1
          lần.
        </p>
        <p className="mt-3 text-[14px] text-ink-muted">
          Không thấy email? Hãy xem thư mục Thư rác / Quảng cáo, hoặc đợi 1 phút rồi gửi lại.
        </p>
        <Button className="mt-6" variant="secondary" block onClick={() => setSentTo(null)}>
          Dùng email khác
        </Button>
        <ButtonLink to="/login" variant="ghost" block className="mt-2">
          Quay lại đăng nhập
        </ButtonLink>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1 className="h1">Quên mật khẩu</h1>
      <p className="mt-1.5 text-[14px] text-ink-muted">
        Nhập email đã đăng ký, BrainBlocks sẽ gửi cho bạn link để đặt mật khẩu mới.
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
        <Field label="Email" htmlFor="email" error={form.formState.errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="phuhuynh@email.vn"
            invalid={Boolean(form.formState.errors.email) || notFound}
            {...form.register('email', { onChange: () => setNotFound(false) })}
          />
        </Field>
        {notFound && (
          <p role="alert" className="-mt-2 text-[13px] font-medium text-danger">
            Tài khoản không tồn tại: email này chưa đăng ký BrainBlocks.{' '}
            <Link to="/register" className="font-semibold">
              Tạo tài khoản mới
            </Link>
          </p>
        )}
        <Button type="submit" size="lg" block loading={form.formState.isSubmitting}>
          Gửi link đặt lại mật khẩu
        </Button>
      </form>
      <p className="mt-5 text-center text-[14px] text-ink-muted">
        Nhớ ra mật khẩu rồi? <Link to="/login">Đăng nhập</Link>
      </p>
    </AuthShell>
  )
}
