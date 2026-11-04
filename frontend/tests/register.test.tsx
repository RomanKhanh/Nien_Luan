import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import { tokenStorage } from '@/auth/tokenStorage'
import RegisterPage from '@/pages/auth/RegisterPage'
import { mockApi, renderWithApp, type Call } from './helpers'

const renderRegister = () =>
  renderWithApp(<RegisterPage />, {
    route: '/register',
    path: '/register',
    extraRoutes: <Route path="/children" element={<p>Trang hồ sơ bé</p>} />,
  })

async function fill(user: ReturnType<typeof userEvent.setup>, values: Partial<Record<string, string>> = {}) {
  const v = { name: 'Phụ Huynh Mới', email: 'moi@test.vn', password: 'matkhau123', confirm: 'matkhau123', ...values }
  if (v.name) await user.type(screen.getByLabelText('Họ và tên'), v.name)
  if (v.email) await user.type(screen.getByLabelText('Email'), v.email)
  if (v.password) await user.type(screen.getByLabelText('Mật khẩu'), v.password)
  if (v.confirm) await user.type(screen.getByLabelText('Nhập lại mật khẩu'), v.confirm)
}
const sendCode = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Gửi mã' }))
const submit = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
const registerCall = (calls: Call[]) => calls.find((c) => c.url === '/auth/register')

describe('Đăng ký tài khoản', () => {
  it('chưa bấm "Gửi mã": không cho tạo tài khoản, không gọi API đăng ký', async () => {
    const calls = mockApi({})
    renderRegister()
    const user = userEvent.setup()
    await fill(user)
    await user.type(screen.getByLabelText('Mã xác nhận'), '123456')
    await submit(user)
    expect(await screen.findByText('Bấm "Gửi mã" để nhận mã xác nhận cho email này')).toBeInTheDocument()
    expect(registerCall(calls)).toBeUndefined()
  })

  it.each([
    ['ngắn hơn 8 ký tự', 'abc123', 'Tối thiểu 8 ký tự'],
    ['không có chữ số', 'matkhaudai', 'Cần có ít nhất một chữ số'],
    ['không có chữ cái', '12345678', 'Cần có ít nhất một chữ cái'],
  ])('mật khẩu %s: báo lỗi', async (_case, password, message) => {
    mockApi({})
    renderRegister()
    const user = userEvent.setup()
    await fill(user, { password, confirm: password })
    await submit(user)
    expect(await screen.findByText(message)).toBeInTheDocument()
  })

  it('nhập lại mật khẩu không khớp: báo lỗi', async () => {
    mockApi({})
    renderRegister()
    const user = userEvent.setup()
    await fill(user, { confirm: 'matkhau999' })
    await submit(user)
    expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument()
  })

  it('gửi mã: báo đã gửi và chặn gửi lại trong thời gian chờ', async () => {
    const calls = mockApi({ 'POST /auth/register/send-code': { data: null } })
    renderRegister()
    const user = userEvent.setup()
    await fill(user)
    await sendCode(user)
    expect(await screen.findByText('Đã gửi mã xác nhận tới moi@test.vn')).toBeInTheDocument()
    expect(calls.find((c) => c.url === '/auth/register/send-code')?.body).toEqual({ email: 'moi@test.vn' })
    expect(screen.getByRole('button', { name: /^Gửi lại \(\d+s\)$/ })).toBeDisabled()
  })

  it('gửi mã cho email đã đăng ký (409): báo lỗi ngay dưới ô email', async () => {
    mockApi({ 'POST /auth/register/send-code': { status: 409, message: 'Email already in use' } })
    renderRegister()
    const user = userEvent.setup()
    await fill(user)
    await sendCode(user)
    expect(await screen.findByText('Email này đã được đăng ký.')).toBeInTheDocument()
  })

  it('đổi email sau khi đã gửi mã: phải gửi mã lại cho email mới', async () => {
    const calls = mockApi({ 'POST /auth/register/send-code': { data: null } })
    renderRegister()
    const user = userEvent.setup()
    await fill(user)
    await sendCode(user)
    await screen.findByText('Đã gửi mã xác nhận tới moi@test.vn')
    const email = screen.getByLabelText('Email')
    await user.clear(email)
    await user.type(email, 'khac@test.vn')
    expect(screen.getByRole('button', { name: 'Gửi mã' })).toBeEnabled()
    await user.type(screen.getByLabelText('Mã xác nhận'), '123456')
    await submit(user)
    expect(await screen.findByText('Bấm "Gửi mã" để nhận mã xác nhận cho email này')).toBeInTheDocument()
    expect(registerCall(calls)).toBeUndefined()
  })

  it('mã xác nhận sai: báo lỗi dưới ô mã, không đăng nhập', async () => {
    mockApi({
      'POST /auth/register/send-code': { data: null },
      'POST /auth/register': { status: 400, message: 'Verification code is incorrect' },
    })
    renderRegister()
    const user = userEvent.setup()
    await fill(user)
    await sendCode(user)
    await screen.findByText('Đã gửi mã xác nhận tới moi@test.vn')
    await user.type(screen.getByLabelText('Mã xác nhận'), '000000')
    await submit(user)
    expect(await screen.findByText('Mã xác nhận không đúng.')).toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })

  const successRoutes = {
    'POST /auth/register/send-code': { data: null },
    'POST /auth/register': { data: null },
    'POST /auth/login': { data: { token: 'jwt-new', role: 'CUSTOMER', userId: 77 } },
  }

  async function registerSuccessfully(user: ReturnType<typeof userEvent.setup>) {
    await fill(user)
    await sendCode(user)
    await screen.findByText('Đã gửi mã xác nhận tới moi@test.vn')
    await user.type(screen.getByLabelText('Mã xác nhận'), '123456')
    await submit(user)
    await screen.findByText('Tạo tài khoản thành công. Bạn được tặng 3 voucher, xem trong Ví voucher.')
  }

  it('đăng ký thành công: gửi đúng body, tự đăng nhập và lưu token', async () => {
    const calls = mockApi(successRoutes)
    renderRegister()
    await registerSuccessfully(userEvent.setup())
    expect(registerCall(calls)?.body).toEqual({
      fullName: 'Phụ Huynh Mới',
      email: 'moi@test.vn',
      password: 'matkhau123',
      verificationCode: '123456',
    })
    expect(calls.find((c) => c.url === '/auth/login')?.body).toEqual({ email: 'moi@test.vn', password: 'matkhau123' })
    await waitFor(() => expect(tokenStorage.get()?.token).toBe('jwt-new'))
  })

  // hồi quy bug 8 (đã sửa): trước đây `if (session && !isSubmitting) return <Navigate to="/" />` của RegisterPage
  // thắng navigate('/children') (chạy dạng transition) nên đăng ký xong luôn bị đưa về trang chủ.
  it('đăng ký thành công: chuyển tới trang hồ sơ bé (không về trang chủ)', async () => {
    mockApi(successRoutes)
    renderRegister()
    await registerSuccessfully(userEvent.setup())
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/children'))
  })

  // hồi quy bug 8: trước đây mất trang khách đang xem dở (vd bấm "Thêm vào giỏ" khi chưa có tài khoản)
  it('đăng ký từ trang yêu cầu đăng nhập (state.from): quay lại đúng trang đó', async () => {
    mockApi(successRoutes)
    renderWithApp(<RegisterPage />, {
      route: { pathname: '/register', state: { from: '/products/101' } },
      path: '/register',
      extraRoutes: <Route path="/products/:id" element={<p>Trang sản phẩm</p>} />,
    })
    await registerSuccessfully(userEvent.setup())
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/products/101'))
  })
})
