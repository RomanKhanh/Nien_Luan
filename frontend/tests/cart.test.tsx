import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Cart, CartItem } from '@/api/types'
import CartPage from '@/pages/cart/CartPage'
import { loginAs, mockApi, renderWithApp } from './helpers'

function item(over: Partial<CartItem> = {}): CartItem {
  return {
    id: 11,
    productId: 101,
    productName: 'Bộ lắp ráp Robot',
    thumbnailUrl: null,
    price: 200000,
    quantity: 2,
    subtotal: 400000,
    stockQuantity: 10,
    active: true,
    ...over,
  }
}
const cart = (items: CartItem[]): Cart => ({
  id: 1,
  items,
  totalAmount: items.reduce((s, i) => s + i.subtotal, 0),
})

const renderCart = () => renderWithApp(<CartPage />, { route: '/cart', path: '/cart' })

describe('Giỏ hàng', () => {
  it('chưa đăng nhập: mời đăng nhập, không gọi API giỏ', () => {
    const calls = mockApi({})
    renderCart()
    expect(screen.getByText('Đăng nhập để xem giỏ hàng')).toBeInTheDocument()
    expect(calls.some((c) => c.url === '/cart')).toBe(false)
  })

  it('tài khoản admin: không dùng giỏ hàng', () => {
    const calls = mockApi({})
    loginAs('ADMIN')
    renderCart()
    expect(screen.getByText('Tài khoản quản trị không dùng giỏ hàng')).toBeInTheDocument()
    expect(calls.some((c) => c.url === '/cart')).toBe(false)
  })

  it('giỏ trống: hiện trạng thái trống', async () => {
    mockApi({ 'GET /cart': { data: cart([]) } })
    loginAs()
    renderCart()
    expect(await screen.findByText('Giỏ hàng đang trống')).toBeInTheDocument()
  })

  it('hiện sản phẩm, số lượng và tổng tiền', async () => {
    mockApi({
      'GET /cart': {
        data: cart([item(), item({ id: 12, productId: 102, productName: 'Tranh cát', price: 50000, quantity: 1, subtotal: 50000 })]),
      },
    })
    loginAs()
    renderCart()
    expect(await screen.findByText('Bộ lắp ráp Robot')).toBeInTheDocument()
    expect(screen.getByText('Tranh cát')).toBeInTheDocument()
    expect(screen.getByText('3 sản phẩm')).toBeInTheDocument()
    expect(screen.getByText('Tạm tính (3 sản phẩm)')).toBeInTheDocument()
    expect(screen.getAllByText('450.000₫').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByRole('link', { name: 'Tiến hành thanh toán' })).toHaveAttribute('href', '/checkout')
  })

  it('tăng số lượng: gọi PATCH /cart/items/{id} với quantity mới và cập nhật tổng', async () => {
    const calls = mockApi({
      'GET /cart': { data: cart([item()]) },
      'PATCH /cart/items/11': { data: cart([item({ quantity: 3, subtotal: 600000 })]) },
    })
    loginAs()
    renderCart()
    await screen.findByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Tăng' }))
    await waitFor(() => expect(screen.getAllByText('600.000₫').length).toBeGreaterThan(0))
    expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ quantity: 3 })
  })

  it('xoá một sản phẩm: gọi DELETE /cart/items/{id}', async () => {
    const calls = mockApi({
      'GET /cart': { data: cart([item()]) },
      'DELETE /cart/items/11': { data: cart([]) },
    })
    loginAs()
    renderCart()
    await screen.findByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Xoá' }))
    expect(await screen.findByText('Giỏ hàng đang trống')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'DELETE' && c.url === '/cart/items/11')).toBe(true)
  })

  it('xoá toàn bộ giỏ: hỏi xác nhận rồi gọi DELETE /cart', async () => {
    let cleared = false
    const calls = mockApi({
      'GET /cart': () => ({ data: cleared ? cart([]) : cart([item()]) }),
      'DELETE /cart': () => {
        cleared = true
        return { data: null }
      },
    })
    loginAs()
    renderCart()
    await screen.findByText('Bộ lắp ráp Robot')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Xoá toàn bộ giỏ hàng' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Xoá giỏ hàng' }))
    expect(await screen.findByText('Giỏ hàng đang trống')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'DELETE' && c.url === '/cart')).toBe(true)
  })

  it('sản phẩm vượt tồn kho: cảnh báo và khoá nút thanh toán', async () => {
    mockApi({ 'GET /cart': { data: cart([item({ quantity: 5, stockQuantity: 3, subtotal: 1000000 })]) } })
    loginAs()
    renderCart()
    expect(await screen.findByText('Chỉ còn 3 sản phẩm')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('vượt số lượng tồn kho')
    expect(screen.getByRole('button', { name: 'Tiến hành thanh toán' })).toBeDisabled()
  })

  it('sản phẩm ngừng bán: báo ngừng bán, khoá chỉnh số lượng và thanh toán', async () => {
    mockApi({ 'GET /cart': { data: cart([item({ active: false })]) } })
    loginAs()
    renderCart()
    expect(await screen.findByText('Sản phẩm đã ngừng bán')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tăng' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tiến hành thanh toán' })).toBeDisabled()
  })

  it('cập nhật số lượng bị backend từ chối: hiện thông báo lỗi đã dịch', async () => {
    mockApi({
      'GET /cart': { data: cart([item()]) },
      'PATCH /cart/items/11': { status: 400, message: 'Insufficient stock for: Bộ lắp ráp Robot' },
    })
    loginAs()
    renderCart()
    await screen.findByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Tăng' }))
    expect(await screen.findByText('Không đủ hàng trong kho cho: Bộ lắp ráp Robot')).toBeInTheDocument()
  })

  it('lỗi tải giỏ hàng: hiện trạng thái lỗi', async () => {
    mockApi({ 'GET /cart': { status: 500, message: 'boom' } })
    loginAs()
    renderCart()
    expect(await screen.findByText('Máy chủ gặp lỗi, vui lòng thử lại sau.')).toBeInTheDocument()
  })
})
