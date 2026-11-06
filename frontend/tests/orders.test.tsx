import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Order, OrderStatus } from '@/api/types'
import OrderDetailPage from '@/pages/orders/OrderDetailPage'
import OrdersPage from '@/pages/orders/OrdersPage'
import { loginAs, mockApi, renderWithApp } from './helpers'

const me = { id: 1, fullName: 'Phụ Huynh A', email: 'a@test.vn', createdAt: '2025-01-01T00:00:00' }

function order(id: number, status: OrderStatus, over: Partial<Order> = {}): Order {
  return {
    id,
    orderCode: `BB${id}`,
    receiverName: 'Phụ Huynh A',
    receiverPhone: '0901234567',
    shippingAddress: '12 Lê Lợi, Quận 1, TP Hồ Chí Minh',
    address: null,
    subtotal: 400000,
    shippingFee: 30000,
    shippingZone: null,
    shippingZoneLabel: null,
    parcelCount: 1,
    shippingDiscount: 0,
    discountAmount: 0,
    totalAmount: 430000,
    status,
    paymentMethod: 'COD',
    cancelledByCustomer: false,
    paymentDeadline: null,
    items: [
      { id: 1, productId: 101, productName: 'Bộ lắp ráp Robot', quantity: 2, unitPrice: 200000, subtotal: 400000, childProfileId: null, childName: null },
    ],
    createdAt: '2026-11-01T10:00:00',
    ...over,
  }
}

describe('Lịch sử đơn hàng', () => {
  it('chưa có đơn: trạng thái trống và nút mua sắm', async () => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders': { data: [] } })
    loginAs()
    renderWithApp(<OrdersPage />, { route: '/orders', path: '/orders' })
    expect(await screen.findByText('Bạn chưa có đơn hàng nào')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mua sắm ngay' })).toHaveAttribute('href', '/products')
  })

  it('đếm số đơn theo từng trạng thái trên tab và lọc khi chọn tab', async () => {
    mockApi({
      'GET /me': { data: me },
      'GET /orders': { data: [order(1, 'PENDING'), order(2, 'DELIVERED'), order(3, 'DELIVERED')] },
    })
    loginAs()
    renderWithApp(<OrdersPage />, { route: '/orders', path: '/orders' })
    expect(await screen.findByRole('tab', { name: 'Tất cả (3)' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Đã giao (2)' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Đang giao' })).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('tab', { name: 'Đã giao (2)' }))
    expect(screen.queryByRole('link', { name: /BB1/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /BB[23]/ })).toHaveLength(2)
  })

  it('tab không có đơn: báo không có đơn ở trạng thái này', async () => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders': { data: [order(1, 'PENDING')] } })
    loginAs()
    renderWithApp(<OrdersPage />, { route: '/orders', path: '/orders' })
    await userEvent.setup().click(await screen.findByRole('tab', { name: 'Đã huỷ' }))
    expect(screen.getByText('Không có đơn ở trạng thái này')).toBeInTheDocument()
  })
})

describe('Chi tiết đơn hàng', () => {
  const renderDetail = (state?: object) =>
    renderWithApp(<OrderDetailPage />, { route: { pathname: '/orders/5', state }, path: '/orders/:id' })

  it('vừa đặt xong (được chuyển từ checkout): hiện banner đặt hàng thành công', async () => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders/5': { data: order(5, 'PENDING') }, 'GET /reviews/mine': { data: [] } })
    loginAs()
    renderDetail({ placed: true })
    expect(await screen.findByText('Đặt hàng thành công!')).toBeInTheDocument()
  })

  it('mở thẳng URL đơn cũ: không hiện banner đặt hàng thành công', async () => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders/5': { data: order(5, 'PENDING') }, 'GET /reviews/mine': { data: [] } })
    loginAs()
    renderDetail()
    await screen.findByText('Bộ lắp ráp Robot', { exact: false })
    expect(screen.queryByText('Đặt hàng thành công!')).not.toBeInTheDocument()
  })

  it('đơn chờ xác nhận: huỷ được sau khi xác nhận, gọi PATCH /orders/{id}/cancel và cập nhật trạng thái', async () => {
    const calls = mockApi({
      'GET /me': { data: me },
      'GET /orders/5': { data: order(5, 'PENDING') },
      'GET /reviews/mine': { data: [] },
      'PATCH /orders/5/cancel': { data: order(5, 'CANCELLED', { cancelledByCustomer: true }) },
    })
    loginAs()
    renderDetail()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Huỷ đơn hàng' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Huỷ đơn hàng này?')
    await user.click(within(dialog).getByRole('button', { name: 'Huỷ đơn' }))
    expect(await screen.findByText('Đã huỷ đơn hàng, sản phẩm đã được đưa lại vào giỏ hàng')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PATCH' && c.url === '/orders/5/cancel')).toBe(true)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Huỷ đơn hàng' })).not.toBeInTheDocument())
  })

  it.each(['CONFIRMED', 'SHIPPING', 'DELIVERED'] as OrderStatus[])('đơn %s: không có nút huỷ', async (status) => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders/5': { data: order(5, status) }, 'GET /reviews/mine': { data: [] } })
    loginAs()
    renderDetail()
    await screen.findByText('Bộ lắp ráp Robot', { exact: false })
    expect(screen.queryByRole('button', { name: 'Huỷ đơn hàng' })).not.toBeInTheDocument()
  })

  it('khách tự huỷ đơn: không hiện nút huỷ và không cho gửi phản hồi', async () => {
    mockApi({
      'GET /me': { data: me },
      'GET /orders/5': { data: order(5, 'CANCELLED', { cancelledByCustomer: true }) },
      'GET /reviews/mine': { data: [] },
    })
    loginAs()
    renderDetail()
    await screen.findByText('Bộ lắp ráp Robot', { exact: false })
    expect(screen.queryByRole('button', { name: 'Huỷ đơn hàng' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /phản hồi|khiếu nại|yêu cầu/i })).not.toBeInTheDocument()
  })

  it('huỷ bị backend từ chối (đơn vừa chuyển sang đang giao): hiện lỗi đã dịch', async () => {
    mockApi({
      'GET /me': { data: me },
      'GET /orders/5': { data: order(5, 'PENDING') },
      'GET /reviews/mine': { data: [] },
      'PATCH /orders/5/cancel': { status: 400, message: "Can't cancel this order because it is already SHIPPING" },
    })
    loginAs()
    renderDetail()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Huỷ đơn hàng' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Huỷ đơn' }))
    expect(await screen.findByText('Không thể huỷ đơn ở trạng thái SHIPPING')).toBeInTheDocument()
  })

  it('đơn của người khác / không tồn tại (404): báo không tìm thấy', async () => {
    mockApi({ 'GET /me': { data: me }, 'GET /orders/5': { status: 404, message: 'Order not found' }, 'GET /reviews/mine': { data: [] } })
    loginAs()
    renderDetail()
    expect(await screen.findByText('Không tìm thấy đơn hàng')).toBeInTheDocument()
  })
})
