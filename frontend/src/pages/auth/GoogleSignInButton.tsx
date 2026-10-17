import { useLayoutEffect, useRef, useState } from 'react'
import { GoogleLogin, GoogleOAuthProvider } from '@react-oauth/google'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? ''

// nút Google chỉ rộng 200-400px (giới hạn của Google Identity Services)
const MIN_WIDTH = 200
const MAX_WIDTH = 400

const googleSignInEnabled = CLIENT_ID !== ''

// Nút "Tiếp tục với Google" do Google vẽ (iframe): bấm chọn tài khoản xong trả về ID token (credential),
// trang đăng nhập gửi token đó cho backend kiểm tra. Provider đặt ở đây (không bọc cả app) để script của Google chỉ
// tải khi mở trang đăng nhập.
export function GoogleSignInButton({
  onCredential,
  onError,
}: {
  onCredential: (idToken: string) => void
  onError: () => void
}) {
  const boxRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(MAX_WIDTH)

  // nút của Google không tự co theo khung, đo bề ngang khung để nút rộng bằng các nút khác của form
  useLayoutEffect(() => {
    const box = boxRef.current
    if (!box) return
    const update = () => setWidth(Math.round(Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, box.clientWidth))))
    update()
    const observer = new ResizeObserver(update)
    observer.observe(box)
    return () => observer.disconnect()
  }, [])

  if (!googleSignInEnabled) return null

  return (
    <div ref={boxRef} className="flex min-h-12 w-full justify-center">
      <GoogleOAuthProvider clientId={CLIENT_ID} locale="vi">
        <GoogleLogin
          onSuccess={(res) => (res.credential ? onCredential(res.credential) : onError())}
          onError={onError}
          theme="outline"
          size="large"
          shape="pill"
          text="continue_with"
          width={width}
        />
      </GoogleOAuthProvider>
    </div>
  )
}
