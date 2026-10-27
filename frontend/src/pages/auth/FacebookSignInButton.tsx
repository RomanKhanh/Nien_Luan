import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/Button'

const APP_ID = import.meta.env.VITE_FACEBOOK_APP_ID?.trim() ?? ''
const VERSION = import.meta.env.VITE_FACEBOOK_API_VERSION?.trim() || 'v24.0'
type FacebookSdk = {
  init: (options: { appId: string; version: string; cookie: boolean; xfbml: boolean; fedCM: boolean }) => void
  login: (callback: (response: { authResponse?: { accessToken?: string } }) => void,
    options: { scope: string; return_scopes: boolean; auth_type: string }) => void
}
declare global {
  interface Window { FB?: FacebookSdk; fbAsyncInit?: () => void }
}

let sdkPromise: Promise<FacebookSdk> | undefined
let sdkReady = false
function loadSdk(): Promise<FacebookSdk> {
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise<FacebookSdk>((resolve, reject) => {
    const script = document.createElement('script')
    let timer: ReturnType<typeof setTimeout>
    const fail = () => {
      clearTimeout(timer)
      script.remove()
      reject(new Error('Không tải được Facebook. Hãy kiểm tra mạng hoặc trình chặn quảng cáo.'))
    }
    const ready = () => {
      clearTimeout(timer)
      if (!window.FB) { fail(); return }
      try {
        // Keep the explicit popup flow; app-config can otherwise default to browser-managed FedCM.
        window.FB.init({ appId: APP_ID, version: VERSION, cookie: false, xfbml: false, fedCM: false })
        sdkReady = true
        resolve(window.FB)
      } catch { fail() }
    }
    timer = setTimeout(fail, 15000)
    if (sdkReady && window.FB) { ready(); return }
    // sdk.js first installs a stub and loads its bundle asynchronously. onload alone is too early.
    window.fbAsyncInit = ready
    script.src = 'https://connect.facebook.net/vi_VN/sdk.js'
    script.async = true
    script.defer = true
    script.onerror = fail
    document.head.appendChild(script)
  }).catch((error: unknown) => { sdkPromise = undefined; throw error })
  return sdkPromise
}

export function FacebookSignInButton({ onCredential, disabled = false }: {
  onCredential: (accessToken: string) => Promise<void>
  disabled?: boolean
}) {
  const [sdk, setSdk] = useState<FacebookSdk | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [waitingForFacebook, setWaitingForFacebook] = useState(false)
  const loginAttempt = useRef(0)
  const waitTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => () => {
    loginAttempt.current += 1
    clearTimeout(waitTimer.current)
  }, [])
  useEffect(() => {
    if (!APP_ID) return
    let active = true
    loadSdk().then((loaded) => { if (active) setSdk(loaded) })
      .catch((e: Error) => { if (active) setError(e.message) })
    return () => { active = false }
  }, [attempt])
  if (!APP_ID) return null

  const stopWaiting = (message: string) => {
    loginAttempt.current += 1
    clearTimeout(waitTimer.current)
    setWaitingForFacebook(false)
    setBusy(false)
    setError(message)
  }
  const login = () => {
    if (!sdk || busy) return
    if (window.location.protocol !== 'https:') {
      setError('Hãy mở website bằng HTTPS để đăng nhập Facebook. Bạn vẫn có thể đăng nhập bằng email trên trang này.')
      return
    }
    setError('')
    setBusy(true)
    setWaitingForFacebook(true)
    const currentAttempt = ++loginAttempt.current
    waitTimer.current = setTimeout(() => {
      if (loginAttempt.current === currentAttempt) {
        stopWaiting('Facebook chưa phản hồi. Hãy cho phép cửa sổ bật lên cho website này rồi thử lại.')
      }
    }, 60000)
    try {
      // Call synchronously from the click so browsers do not block the login popup.
      sdk.login((response) => {
        // Ignore duplicate callbacks and callbacks from cancelled/timed-out attempts.
        if (loginAttempt.current !== currentAttempt) return
        loginAttempt.current += 1
        clearTimeout(waitTimer.current)
        setWaitingForFacebook(false)
        const token = response.authResponse?.accessToken
        if (!token) {
          setBusy(false)
          setError('Đăng nhập Facebook đã bị hủy hoặc chưa được cho phép. Bạn có thể thử lại.')
          return
        }
        void onCredential(token).catch(() => setError('Không đăng nhập được bằng Facebook. Vui lòng thử lại.'))
          .finally(() => setBusy(false))
      }, { scope: 'public_profile,email', return_scopes: true, auth_type: 'rerequest' })
    } catch {
      stopWaiting('Không mở được Facebook. Hãy cho phép cửa sổ bật lên và thử lại.')
    }
  }
  return (
    <div className="mt-3">
      <Button block variant="secondary" disabled={disabled || (!sdk && !error)} loading={busy}
        onClick={!sdk && error ? () => { setError(''); setAttempt((value) => value + 1) } : login}>
        <span aria-hidden className="text-xl font-bold text-[#1877F2]">f</span>
        {busy ? (waitingForFacebook ? 'Đang chờ Facebook...' : 'Đang xác minh tài khoản...')
          : !sdk && error ? 'Thử tải lại Facebook' : !sdk ? 'Đang tải Facebook...' : 'Tiếp tục với Facebook'}
      </Button>
      {waitingForFacebook && (
        <div className="mt-2 text-sm text-ink-2" role="status">
          Hoàn tất đăng nhập trong cửa sổ Facebook. Nếu không thấy cửa sổ, hãy kiểm tra biểu tượng chặn popup trên thanh địa chỉ.
          <button type="button" className="ml-1 underline" onClick={() => stopWaiting('Đã hủy chờ Facebook. Bạn có thể thử lại.')}>
            Hủy chờ
          </button>
        </div>
      )}
      {error && <p role="alert" className="mt-2 text-sm text-danger">{error}</p>}
    </div>
  )
}
