import { useEffect, useState, type FormEvent } from 'react'
import { authApi } from '@/api/endpoints'
import { toApiError } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Field, Input } from '@/components/ui/Field'

export function FacebookEmailForm({ accessToken, onSuccess, onCancel }: {
  accessToken: string; onSuccess: (role: string) => void; onCancel: () => void
}) {
  const { completeFacebookEmail } = useAuth()
  const [email, setEmail] = useState('')
  const [sentTo, setSentTo] = useState('')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!cooldown) return
    const timer = setTimeout(() => setCooldown((value) => value - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])
  const normalized = email.trim().toLowerCase()
  const send = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(''); setBusy(true)
    try {
      await authApi.sendFacebookEmailCode(accessToken, normalized)
      setSentTo(normalized); setCode(''); setCooldown(60)
    } catch (e) { setError(toApiError(e).message) }
    finally { setBusy(false) }
  }
  const complete = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (normalized !== sentTo) { setError('Hãy gửi mã xác minh cho địa chỉ email mới.'); return }
    setError(''); setBusy(true)
    try { onSuccess((await completeFacebookEmail(accessToken, normalized, code)).role) }
    catch (e) { setError(toApiError(e).message) }
    finally { setBusy(false) }
  }
  return <>
    <h1 className="h1">Xác minh email cho Facebook</h1>
    <p className="mt-3 text-ink-2">Xác minh email để hoàn tất đăng nhập. Nếu email đã có tài khoản BrainBlocks,
      xác nhận mã sẽ liên kết Facebook này với tài khoản đó; nếu chưa có, một tài khoản khách hàng sẽ được tạo.</p>
    {error && <p role="alert" className="mt-4 text-sm text-danger">{error}</p>}
    <form onSubmit={send} className="mt-5 space-y-3">
      <Field label="Email của bạn" htmlFor="facebook-email">
        <Input id="facebook-email" type="email" autoComplete="email" required maxLength={255}
          disabled={busy} value={email} onChange={(e) => { setEmail(e.target.value); setCode('') }} />
      </Field>
      <Button type="submit" variant="secondary" block disabled={busy || cooldown > 0}>
        {cooldown > 0 ? `Gửi lại sau ${cooldown}s` : sentTo ? 'Gửi lại mã' : 'Gửi mã xác minh'}
      </Button>
    </form>
    {sentTo && <form onSubmit={complete} className="mt-4 space-y-3">
      <p role="status" className="text-sm text-ink-2">Đã yêu cầu gửi mã tới {sentTo}. Kiểm tra cả thư mục spam. Mã có hiệu lực 5 phút.</p>
      <Field label="Mã xác minh 6 số" htmlFor="facebook-email-code">
        <Input id="facebook-email-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}"
          minLength={6} maxLength={6} required disabled={busy} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
      </Field>
      <Button type="submit" block loading={busy} disabled={normalized !== sentTo}>Xác minh và tiếp tục</Button>
    </form>}
    <Button variant="ghost" block className="mt-3" disabled={busy} onClick={onCancel}>Quay lại đăng nhập</Button>
  </>
}
