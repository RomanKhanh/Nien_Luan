import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import { RequireAuth } from '@/auth/RequireAuth'
import { tokenStorage } from '@/auth/tokenStorage'
import LoginPage from '@/pages/auth/LoginPage'
import { loginAs, mockApi, renderWithApp } from './helpers'

const homeRoutes = (
  <>
    <Route path="/" element={<p>Trang chủ</p>} />
    <Route path="/admin" element={<p>Trang admin</p>} />
    <Route path="/cart" element={<p>Trang giỏ</p>} />
  </>
)

async function fillLogin(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Mật khẩu'), password)
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
}

describe('Đăng nhập', () => {
  it('bỏ trống email + mật khẩu: báo lỗi validate, không gọi API', async () => {
    const calls = mockApi({})
    renderWithApp(<LoginPage />, { route: '/login', path: '/login' })
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(await screen.findByText('Vui lòng nhập email')).toBeInTheDocument()
    expect(screen.getByText('Vui lòng nhập mật khẩu')).toBeInTheDocument()
    expect(calls.filter((c) => c.url === '/auth/login')).toHaveLength(0)
  })

  it('email sai định dạng: báo lỗi', async () => {
    mockApi({})
    renderWithApp(<LoginPage />, { route: '/login', path: '/login' })
    await fillLogin('khong-phai-email', 'secret123')
    expect(await screen.findByText('Email không đúng định dạng')).toBeInTheDocument()
  })

  it('khách hàng đăng nhập đúng: lưu token và về trang chủ', async () => {
    const calls = mockApi({
      'POST /auth/login': { data: { token: 'jwt-abc', role: 'CUSTOMER', userId: 3 } },
    })
    renderWithApp(<LoginPage />, { route: '/login', path: '/login', extraRoutes: homeRoutes })
    await fillLogin('phuhuynh@test.vn', 'secret123')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    expect(calls.find((c) => c.url === '/auth/login')?.body).toEqual({
      email: 'phuhuynh@test.vn',
      password: 'secret123',
    })
    expect(tokenStorage.get()).toEqual({ token: 'jwt-abc', role: 'CUSTOMER', userId: 3 })
    expect(JSON.parse(localStorage.getItem('brainblocks.auth')!).token).toBe('jwt-abc')
  })

  it('admin đăng nhập: chuyển tới /admin', async () => {
    mockApi({ 'POST /auth/login': { data: { token: 'jwt-admin', role: 'ADMIN', userId: 1 } } })
    renderWithApp(<LoginPage />, { route: '/login', path: '/login', extraRoutes: homeRoutes })
    await fillLogin('admin@test.vn', 'secret123')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/admin'))
  })

  it('sai mật khẩu: hiện lỗi dưới ô mật khẩu, không lưu token', async () => {
    mockApi({ 'POST /auth/login': { status: 401, message: 'Incorrect email or password' } })
    renderWithApp(<LoginPage />, { route: '/login', path: '/login' })
    await fillLogin('phuhuynh@test.vn', 'sai-mat-khau')
    expect(await screen.findByText('Email hoặc mật khẩu không đúng.')).toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })

  it('tài khoản bị khoá: hiện màn hình khoá kèm email', async () => {
    mockApi({ 'POST /auth/login': { status: 403, message: 'Account is locked' } })
    renderWithApp(<LoginPage />, { route: '/login', path: '/login' })
    await fillLogin('bikhoa@test.vn', 'secret123')
    expect(await screen.findByText('Tài khoản của bạn đang tạm bị khoá')).toBeInTheDocument()
    expect(screen.getByText('bikhoa@test.vn')).toBeInTheDocument()
  })
})

describe('Chặn route theo đăng nhập / vai trò (RequireAuth)', () => {
  it('chưa đăng nhập: chuyển về /login', async () => {
    mockApi({})
    renderWithApp(
      <RequireAuth role="CUSTOMER">
        <p>Nội dung riêng</p>
      </RequireAuth>,
      { route: '/children', path: '/children', extraRoutes: <Route path="/login" element={<p>Form login</p>} /> },
    )
    expect(await screen.findByText('Form login')).toBeInTheDocument()
    expect(screen.queryByText('Nội dung riêng')).not.toBeInTheDocument()
  })

  it('khách hàng vào trang admin: bị chặn', () => {
    mockApi({})
    loginAs('CUSTOMER')
    renderWithApp(
      <RequireAuth role="ADMIN">
        <p>Bảng điều khiển admin</p>
      </RequireAuth>,
      { route: '/admin', path: '/admin' },
    )
    expect(screen.getByText('Trang này không dành cho tài khoản của bạn')).toBeInTheDocument()
    expect(screen.getByText('Chỉ quản trị viên mới truy cập được.')).toBeInTheDocument()
    expect(screen.queryByText('Bảng điều khiển admin')).not.toBeInTheDocument()
  })

  it('đúng vai trò: hiện nội dung', () => {
    mockApi({})
    loginAs('ADMIN')
    renderWithApp(
      <RequireAuth role="ADMIN">
        <p>Bảng điều khiển admin</p>
      </RequireAuth>,
      { route: '/admin', path: '/admin' },
    )
    expect(screen.getByText('Bảng điều khiển admin')).toBeInTheDocument()
  })
})
