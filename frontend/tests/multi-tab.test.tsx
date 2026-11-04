import { describe, expect, it } from 'vitest'
import { act, screen } from '@testing-library/react'
import { Route } from 'react-router-dom'
import { http, request } from '@/api/client'
import { RequireAuth } from '@/auth/RequireAuth'
import { loginAs, mockApi, renderWithApp } from './helpers'

const KEY = 'brainblocks.auth'

// Tab hiện tại (tab 2) đang đăng nhập; tab 1 đổi localStorage. Trình duyệt báo cho tab 2 bằng sự kiện "storage".
function otherTabWrites(newValue: string | null) {
  act(() => {
    if (newValue === null) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, newValue)
    window.dispatchEvent(new StorageEvent('storage', { key: KEY, newValue, storageArea: localStorage }))
  })
}

function renderPrivatePage() {
  return renderWithApp(
    <RequireAuth role="CUSTOMER">
      <p>Hồ sơ bé riêng tư</p>
    </RequireAuth>,
    { route: '/children', path: '/children', extraRoutes: <Route path="/login" element={<p>Form login</p>} /> },
  )
}

describe('Đồng bộ phiên đăng nhập giữa nhiều tab', () => {
  // BUG: tokenStorage chỉ đọc localStorage lúc tải trang, không nghe sự kiện "storage"
  it('tab khác đăng xuất: tab này cũng phải đăng xuất, không gửi token cũ nữa', async () => {
    const calls = mockApi({ 'GET /me': { data: { id: 1 } } })
    loginAs('CUSTOMER', 1)
    renderPrivatePage()
    expect(screen.getByText('Hồ sơ bé riêng tư')).toBeInTheDocument()

    otherTabWrites(null)

    expect(await screen.findByText('Form login')).toBeInTheDocument()
    await request(http.get('/me')).catch(() => {})
    expect(calls.at(-1)!.headers.Authorization).toBeUndefined()
  })

  // BUG: cùng nguyên nhân; tab này vẫn chạy bằng token của tài khoản cũ
  it('tab khác đăng nhập tài khoản khác: tab này không được tiếp tục dùng token tài khoản cũ', async () => {
    const calls = mockApi({ 'GET /me': { data: { id: 2 } } })
    loginAs('CUSTOMER', 1)
    renderPrivatePage()

    otherTabWrites(JSON.stringify({ token: 'token-CUSTOMER-2', role: 'CUSTOMER', userId: 2 }))

    await request(http.get('/me'))
    expect(calls.at(-1)!.headers.Authorization).not.toBe('Bearer token-CUSTOMER-1')
  })
})
