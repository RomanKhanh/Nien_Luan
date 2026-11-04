import { describe, expect, it } from 'vitest'
import { cleanup, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import type { ProductDetail } from '@/api/types'
import ProductDetailPage from '@/pages/ProductDetailPage'
import { loginAs, mockApi, renderWithApp, type Call } from './helpers'

function detail(over: Partial<ProductDetail> = {}): ProductDetail {
  return {
    id: 101,
    name: 'Bộ lắp ráp Robot',
    price: 450000,
    minAge: 6,
    maxAge: 10,
    stockQuantity: 3,
    active: true,
    categoryId: 10,
    categoryName: 'Lắp ráp',
    skillImpacts: [
      { skillId: 1, skillCode: 'LOGIC', skillName: 'Tư duy Logic', impactIndex: 8 },
      { skillId: 4, skillCode: 'STEM', skillName: 'STEM', impactIndex: 6 },
    ],
    averageRating: 4.5,
    reviewCount: 2,
    description: 'Robot lắp ráp 120 chi tiết.',
    videoUrl: null,
    weightGrams: 650,
    lengthCm: 30,
    widthCm: 20,
    heightCm: 10,
    images: [],
    ratingBreakdown: { '5': 1, '4': 1 },
    soldCount: 0,
    createdAt: '2026-01-01T00:00:00',
    ...over,
  }
}
const emptyPage = { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }

function setup(product: ProductDetail | { status: number; message?: string } = detail(), extra = {}) {
  const calls = mockApi({
    'GET /products/101': 'status' in product ? product : { data: product },
    'GET /products/101/reviews': { data: emptyPage },
    'GET /products': { data: emptyPage },
    'POST /products/101/views': { data: null },
    'GET /cart': { data: { id: 1, items: [], totalAmount: 0 } },
    'GET /children': { data: [] },
    ...extra,
  })
  renderWithApp(<ProductDetailPage />, {
    route: '/products/101',
    path: '/products/:id',
    extraRoutes: (
      <>
        <Route path="/cart" element={<p>Trang giỏ hàng</p>} />
        <Route path="/login" element={<p>Form login</p>} />
      </>
    ),
  })
  return calls
}
const viewCalls = (calls: Call[]) => calls.filter((c) => c.method === 'POST' && c.url === '/products/101/views')

describe('Chi tiết sản phẩm', () => {
  it('hiện tên, giá, tồn kho, độ tuổi và chỉ số tác động từng nhóm kỹ năng', async () => {
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: 'Bộ lắp ráp Robot' })).toBeInTheDocument()
    expect(screen.getAllByText('450.000₫').length).toBeGreaterThan(0)
    expect(screen.getByText('Còn 3 sản phẩm trong kho')).toBeInTheDocument()
    expect(screen.getByText('Chỉ số tác động lên kỹ năng')).toBeInTheDocument()
    expect(screen.getAllByText(/Tư duy Logic/).length).toBeGreaterThan(0)
    expect(screen.getByText('Robot lắp ráp 120 chi tiết.')).toBeInTheDocument()
  })

  it('sản phẩm chưa có chỉ số kỹ năng: báo chưa thiết lập', async () => {
    setup(detail({ skillImpacts: [] }))
    expect(await screen.findByText('Sản phẩm chưa được thiết lập chỉ số kỹ năng.')).toBeInTheDocument()
  })

  it('id không tồn tại / đã ẩn (404): báo sản phẩm không tồn tại', async () => {
    setup({ status: 404, message: 'Product not found' })
    expect(await screen.findByText('Sản phẩm không tồn tại hoặc đã ngừng bán')).toBeInTheDocument()
  })

  it('hết hàng: báo tạm hết, khoá thêm vào giỏ và mua ngay', async () => {
    setup(detail({ stockQuantity: 0 }))
    expect(await screen.findByText('Tạm hết hàng')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mua ngay' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tăng' })).toBeDisabled()
  })

  it('không cho tăng số lượng vượt tồn kho', async () => {
    setup(detail({ stockQuantity: 2 }))
    await screen.findByText('Còn 2 sản phẩm trong kho')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Tăng' }))
    expect(screen.getByRole('button', { name: 'Tăng' })).toBeDisabled()
  })

  it('khách hàng "Mua ngay" với số lượng 2: thêm vào giỏ đúng số lượng rồi sang giỏ hàng', async () => {
    loginAs()
    const calls = setup(detail(), { 'POST /cart/items': { data: { id: 1, items: [], totalAmount: 0 } } })
    await screen.findByText('Còn 3 sản phẩm trong kho')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Tăng' }))
    await user.click(screen.getByRole('button', { name: 'Mua ngay' }))
    expect(await screen.findByText('Trang giỏ hàng')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'POST' && c.url === '/cart/items')?.body).toEqual({ productId: 101, quantity: 2 })
  })

  it('lượt xem: khách gửi 1 lần dù mở lại trang trong cùng phiên', async () => {
    sessionStorage.clear()
    const first = setup()
    await screen.findByText('Còn 3 sản phẩm trong kho')
    await waitFor(() => expect(viewCalls(first)).toHaveLength(1))
    expect(sessionStorage.getItem('brainblocks.viewed.101')).toBe('1')
    // mở lại trang trong cùng phiên trình duyệt
    cleanup()
    const second = setup()
    await screen.findByText('Còn 3 sản phẩm trong kho')
    expect(viewCalls(second)).toHaveLength(0)
  })

  it('lượt xem: admin xem thử trang không bị tính là lượt quan tâm', async () => {
    sessionStorage.clear()
    loginAs('ADMIN')
    const calls = setup()
    await screen.findByText('Còn 3 sản phẩm trong kho')
    expect(viewCalls(calls)).toHaveLength(0)
  })
})
