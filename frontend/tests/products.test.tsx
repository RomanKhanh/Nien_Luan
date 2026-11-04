import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import type { Page, ProductSummary } from '@/api/types'
import ProductListPage from '@/pages/ProductListPage'
import { loginAs, mockApi, renderWithApp, type Call } from './helpers'

const skills = [
  { id: 1, code: 'LOGIC', name: 'Tư duy Logic' },
  { id: 2, code: 'CREATIVE', name: 'Sáng tạo' },
  { id: 3, code: 'PROBLEM_SOLVING', name: 'Giải quyết vấn đề' },
  { id: 4, code: 'STEM', name: 'STEM' },
]
const categories = [
  { id: 10, name: 'Lắp ráp', description: null, productCount: 3 },
  { id: 11, name: 'Mỹ thuật', description: null, productCount: 1 },
]

function product(over: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: 101,
    name: 'Bộ lắp ráp Robot',
    price: 450000,
    minAge: 6,
    maxAge: 10,
    stockQuantity: 8,
    active: true,
    thumbnailUrl: null,
    categoryId: 10,
    categoryName: 'Lắp ráp',
    skillImpacts: [{ skillId: 1, skillCode: 'LOGIC', skillName: 'Tư duy Logic', impactIndex: 8 }],
    averageRating: 4.5,
    reviewCount: 2,
    ...over,
  }
}
const page = (content: ProductSummary[], over: Partial<Page<ProductSummary>> = {}): Page<ProductSummary> => ({
  content,
  page: 0,
  size: 12,
  totalElements: content.length,
  totalPages: 1,
  ...over,
})

function setup(products: (call: Call) => Page<ProductSummary> = () => page([product()]), route = '/products') {
  const calls = mockApi({
    'GET /skills': { data: skills },
    'GET /categories': { data: categories },
    'GET /products': (call) => ({ data: products(call) }),
    'GET /cart': { data: { id: 1, items: [], totalAmount: 0 } },
  })
  const view = renderWithApp(<ProductListPage />, {
    route,
    path: '/products',
    extraRoutes: <Route path="/login" element={<p>Form login</p>} />,
  })
  const productCalls = () => calls.filter((c) => c.url === '/products')
  const lastParams = () => productCalls().at(-1)!.params
  return { calls, view, productCalls, lastParams }
}

describe('Danh sách sản phẩm', () => {
  it('hiện danh sách sản phẩm với tên và giá', async () => {
    setup(() => page([product(), product({ id: 102, name: 'Tranh cát', price: 90000 })]))
    expect(await screen.findByText('Tranh cát')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Bộ lắp ráp Robot' })[0]).toHaveAttribute('href', '/products/101')
    expect(screen.getByText('450.000₫')).toBeInTheDocument()
    expect(screen.getByText('90.000₫')).toBeInTheDocument()
  })

  it('mặc định gửi sort=relevance, trang 0, 12 sản phẩm/trang', async () => {
    const { lastParams } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    expect(lastParams()).toMatchObject({ sort: 'relevance', page: 0, size: 12 })
  })

  it('từ khoá trên URL được gửi lên API và hiện trong tiêu đề', async () => {
    const { lastParams } = setup(undefined, '/products?keyword=robot')
    expect(await screen.findByText('Kết quả cho “robot”')).toBeInTheDocument()
    expect(lastParams()).toMatchObject({ keyword: 'robot' })
  })

  it('lọc theo độ tuổi: gửi ageFrom/ageTo', async () => {
    const { lastParams } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('radio', { name: '6 – 8 tuổi' }))
    await waitFor(() => expect(lastParams()).toMatchObject({ ageFrom: 6, ageTo: 8 }))
  })

  it('lọc theo nhóm kỹ năng: gửi skills dạng LOGIC,CREATIVE', async () => {
    const { lastParams } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Tư duy Logic' }))
    await waitFor(() => expect(lastParams()).toMatchObject({ skills: 'LOGIC' }))
    await user.click(screen.getByRole('checkbox', { name: 'Sáng tạo' }))
    await waitFor(() => expect(lastParams()).toMatchObject({ skills: 'LOGIC,CREATIVE' }))
  })

  it('lọc theo khoảng giá và danh mục', async () => {
    const { lastParams } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    const user = userEvent.setup()
    await user.click(screen.getByRole('radio', { name: '200.000₫ – 500.000₫' }))
    await waitFor(() => expect(lastParams()).toMatchObject({ minPrice: 200000, maxPrice: 500000 }))
    await user.click(await screen.findByRole('radio', { name: /Mỹ thuật/ }))
    await waitFor(() => expect(lastParams()).toMatchObject({ categoryId: 11, minPrice: 200000 }))
  })

  it('sắp xếp theo giá tăng dần: gửi sort=price_asc', async () => {
    const { lastParams } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /^Sắp xếp:/ }))
    await user.click(await screen.findByRole('option', { name: 'Giá tăng dần' }))
    await waitFor(() => expect(lastParams()).toMatchObject({ sort: 'price_asc' }))
  })

  it('không có kết quả: hiện trạng thái trống, bấm "Xoá bộ lọc" thì bỏ hết bộ lọc', async () => {
    const { lastParams } = setup((call) => (call.params.ageFrom ? page([]) : page([product()])), '/products?ageFrom=3&ageTo=5')
    expect(await screen.findByText('Chưa có sản phẩm nào')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Xoá bộ lọc' }))
    expect(await screen.findAllByText('Bộ lắp ráp Robot')).not.toHaveLength(0)
    expect(lastParams().ageFrom).toBeUndefined()
  })

  it('phân trang: bấm trang 2 gửi page=1', async () => {
    const { lastParams } = setup((call) =>
      page([product({ id: 200 + Number(call.params.page ?? 0), name: `SP trang ${Number(call.params.page ?? 0) + 1}` })], {
        page: Number(call.params.page ?? 0),
        totalPages: 3,
        totalElements: 30,
      }),
    )
    expect(await screen.findByText('SP trang 1')).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Phân trang' })
    await userEvent.setup().click(within(nav).getByRole('button', { name: '2' }))
    expect(await screen.findByText('SP trang 2')).toBeInTheDocument()
    expect(lastParams()).toMatchObject({ page: 1 })
  })

  it('sản phẩm hết hàng: nút giỏ hàng hiện "Hết hàng"', async () => {
    setup(() => page([product({ stockQuantity: 0 })]))
    await screen.findAllByText('Bộ lắp ráp Robot')
    expect(screen.getByRole('button', { name: 'Hết hàng' })).toBeDisabled()
  })

  it('chưa đăng nhập bấm "Thêm vào giỏ": chuyển sang trang đăng nhập, không gọi API giỏ', async () => {
    const { calls } = setup()
    await screen.findAllByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thêm vào giỏ' }))
    expect(await screen.findByText('Form login')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST' && c.url === '/cart/items')).toBe(false)
  })

  it('khách hàng bấm "Thêm vào giỏ": gọi POST /cart/items số lượng 1', async () => {
    loginAs()
    const calls = mockApi({
      'GET /skills': { data: skills },
      'GET /categories': { data: categories },
      'GET /children': { data: [] },
      'GET /products': { data: page([product()]) },
      'GET /cart': { data: { id: 1, items: [], totalAmount: 0 } },
      'POST /cart/items': { data: { id: 1, items: [], totalAmount: 0 } },
    })
    renderWithApp(<ProductListPage />, { route: '/products', path: '/products' })
    await screen.findAllByText('Bộ lắp ráp Robot')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Thêm vào giỏ' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'POST' && c.url === '/cart/items')?.body).toEqual({ productId: 101, quantity: 1 }))
    expect(await screen.findByText('Đã thêm vào giỏ hàng')).toBeInTheDocument()
  })

  it('lỗi tải danh sách: hiện trạng thái lỗi', async () => {
    mockApi({ 'GET /skills': { data: skills }, 'GET /categories': { data: categories }, 'GET /products': { status: 500 } })
    renderWithApp(<ProductListPage />, { route: '/products', path: '/products' })
    expect(await screen.findByText('Máy chủ gặp lỗi, vui lòng thử lại sau.')).toBeInTheDocument()
  })
})
