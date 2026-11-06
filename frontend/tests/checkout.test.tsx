import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import type { CartItem } from '@/api/types'
import CheckoutPage from '@/pages/cart/CheckoutPage'
import { loginAs, mockApi, renderWithApp, type Call } from './helpers'

const item: CartItem = {
  id: 11,
  productId: 101,
  productName: 'Bộ lắp ráp Robot',
  thumbnailUrl: null,
  price: 200000,
  quantity: 2,
  subtotal: 400000,
  stockQuantity: 10,
  active: true,
}
const address = {
  provinceCode: 79,
  provinceName: 'TP Hồ Chí Minh',
  districtCode: 760,
  districtName: 'Quận 1',
  wardCode: 26734,
  wardName: 'Phường Bến Nghé',
  addressDetail: '12 Lê Lợi',
  fullAddress: '12 Lê Lợi, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh',
}
const me = {
  id: 1,
  fullName: 'Phụ Huynh A',
  email: 'a@test.vn',
  phone: '0901234567',
  role: 'CUSTOMER',
  enabled: true,
  defaultAddress: address.fullAddress,
  defaultShippingAddress: address,
  createdAt: '2025-01-01T00:00:00',
}
const quote = {
  provinceCode: 79,
  provinceName: 'TP Hồ Chí Minh',
  zone: 'SAME_REGION',
  zoneLabel: 'Nội miền',
  actualWeightGrams: 800,
  volumetricWeightGrams: 600,
  chargeableWeightGrams: 800,
  parcelCount: 1,
  baseFee: 30000,
  bulkySurcharge: 0,
  totalFee: 30000,
}
const order = (over: object = {}) => ({ id: 55, orderCode: 'BB55', paymentMethod: 'COD', status: 'PENDING', ...over })

function routes(extra: Record<string, unknown> = {}) {
  return {
    'GET /cart': { data: { id: 1, items: [item], totalAmount: 400000 } },
    'GET /me': { data: me },
    'GET /children': { data: [] },
    'GET /vouchers': { data: [] },
    'GET /locations/provinces': { data: [{ code: 79, name: 'TP Hồ Chí Minh' }] },
    'GET /locations/provinces/79/districts': { data: [{ code: 760, name: 'Quận 1' }] },
    'GET /locations/districts/760/wards': { data: [{ code: 26734, name: 'Phường Bến Nghé' }] },
    'POST /shipping/quote': { data: quote },
    ...extra,
  } as Parameters<typeof mockApi>[0]
}

const extraRoutes = (
  <>
    <Route path="/cart" element={<p>Trang giỏ hàng</p>} />
    <Route path="/orders/:id" element={<p>Trang chi tiết đơn</p>} />
  </>
)
const renderCheckout = () => renderWithApp(<CheckoutPage />, { route: '/checkout', path: '/checkout', extraRoutes })
const orderCall = (calls: Call[]) => calls.find((c) => c.method === 'POST' && c.url === '/orders')

afterEach(() => vi.unstubAllGlobals())

describe('Thanh toán (checkout)', () => {
  it('giỏ trống: chuyển về trang giỏ hàng', async () => {
    mockApi(routes({ 'GET /cart': { data: { id: 1, items: [], totalAmount: 0 } } }))
    loginAs()
    renderCheckout()
    expect(await screen.findByText('Trang giỏ hàng')).toBeInTheDocument()
  })

  it('tự điền người nhận, SĐT từ hồ sơ và hiện phí ship báo giá theo tỉnh', async () => {
    mockApi(routes())
    loginAs()
    renderCheckout()
    expect(await screen.findByDisplayValue('Phụ Huynh A')).toBeInTheDocument()
    expect(screen.getByDisplayValue('0901234567')).toBeInTheDocument()
    expect(screen.getByDisplayValue('12 Lê Lợi')).toBeInTheDocument()
    expect((await screen.findAllByText('30.000₫')).length).toBeGreaterThan(0)
  })

  it('đặt COD: gửi đúng body (địa chỉ 3 cấp, phí ship đã báo, không gán bé) rồi sang trang đơn', async () => {
    const calls = mockApi(routes({ 'POST /orders': { data: order() } }))
    loginAs()
    renderCheckout()
    await screen.findByDisplayValue('Phụ Huynh A')
    await screen.findAllByText('30.000₫')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đặt hàng' }))
    expect(await screen.findByText('Trang chi tiết đơn')).toBeInTheDocument()
    expect(orderCall(calls)?.body).toMatchObject({
      receiverName: 'Phụ Huynh A',
      receiverPhone: '0901234567',
      provinceCode: 79,
      districtCode: 760,
      wardCode: 26734,
      addressDetail: '12 Lê Lợi',
      paymentMethod: 'COD',
      expectedShippingFee: 30000,
      childAssignments: [],
    })
  })

  it('SĐT sai định dạng: báo lỗi, không gọi API tạo đơn', async () => {
    const calls = mockApi(routes())
    loginAs()
    renderCheckout()
    const phone = await screen.findByDisplayValue('0901234567')
    const user = userEvent.setup()
    await user.clear(phone)
    await user.type(phone, '09ab')
    await user.click(screen.getByRole('button', { name: 'Đặt hàng' }))
    expect(await screen.findByText('Số điện thoại gồm 9–14 chữ số')).toBeInTheDocument()
    expect(orderCall(calls)).toBeUndefined()
  })

  it('xoá tên người nhận: báo lỗi bắt buộc', async () => {
    const calls = mockApi(routes())
    loginAs()
    renderCheckout()
    const name = await screen.findByDisplayValue('Phụ Huynh A')
    const user = userEvent.setup()
    await user.clear(name)
    await user.click(screen.getByRole('button', { name: 'Đặt hàng' }))
    expect(await screen.findByText('Vui lòng nhập tên người nhận')).toBeInTheDocument()
    expect(orderCall(calls)).toBeUndefined()
  })

  it('server báo phí ship đã đổi (409): hiện thông báo, ở lại trang để khách xác nhận lại', async () => {
    mockApi(routes({ 'POST /orders': { status: 409, message: 'Shipping fee has changed, please review the new fee' } }))
    loginAs()
    renderCheckout()
    await screen.findByDisplayValue('Phụ Huynh A')
    await screen.findAllByText('30.000₫')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đặt hàng' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Shipping fee has changed')
    expect(screen.queryByText('Trang chi tiết đơn')).not.toBeInTheDocument()
  })

  it('hết hàng lúc đặt (400): hiện lỗi đã dịch, không rời trang', async () => {
    mockApi(routes({ 'POST /orders': { status: 400, message: 'Not enough stock for: Bộ lắp ráp Robot' } }))
    loginAs()
    renderCheckout()
    await screen.findByDisplayValue('Phụ Huynh A')
    await screen.findAllByText('30.000₫')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đặt hàng' }))
    expect(await screen.findByText('Không đủ hàng trong kho cho: Bộ lắp ráp Robot')).toBeInTheDocument()
    expect(screen.queryByText('Trang chi tiết đơn')).not.toBeInTheDocument()
  })

  it('chọn MoMo: tạo đơn rồi chuyển trình duyệt sang link https của MoMo', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    const calls = mockApi(
      routes({
        'POST /orders': { data: order({ paymentMethod: 'MOMO' }) },
        'POST /payments/55/create-url': { data: { paymentUrl: 'https://test-payment.momo.vn/pay?x=1' } },
      }),
    )
    loginAs()
    renderCheckout()
    await screen.findByDisplayValue('Phụ Huynh A')
    await screen.findAllByText('30.000₫')
    const user = userEvent.setup()
    await user.click(screen.getByRole('radio', { name: /Ví MoMo/ }))
    await user.click(screen.getByRole('button', { name: 'Đặt hàng & thanh toán MoMo' }))
    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://test-payment.momo.vn/pay?x=1'))
    expect(orderCall(calls)?.body).toMatchObject({ paymentMethod: 'MOMO' })
  })

  it('bảo mật: link thanh toán không phải https (vd javascript:) thì KHÔNG chuyển hướng, vào trang đơn', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    mockApi(
      routes({
        'POST /orders': { data: order({ paymentMethod: 'MOMO' }) },
        'POST /payments/55/create-url': { data: { paymentUrl: 'javascript:alert(1)' } },
      }),
    )
    loginAs()
    renderCheckout()
    await screen.findByDisplayValue('Phụ Huynh A')
    await screen.findAllByText('30.000₫')
    const user = userEvent.setup()
    await user.click(screen.getByRole('radio', { name: /Ví MoMo/ }))
    await user.click(screen.getByRole('button', { name: 'Đặt hàng & thanh toán MoMo' }))
    expect(await screen.findByText('Trang chi tiết đơn')).toBeInTheDocument()
    expect(screen.getByText('Link thanh toán không hợp lệ.')).toBeInTheDocument()
    expect(assign).not.toHaveBeenCalled()
  })
})
