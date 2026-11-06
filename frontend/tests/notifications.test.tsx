import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route } from 'react-router-dom'
import type { AppNotification } from '@/api/types'
import NotificationsPage from '@/pages/account/NotificationsPage'
import { loginAs, mockApi, renderWithApp } from './helpers'

const me = { id: 1, fullName: 'Phụ Huynh A', email: 'a@test.vn', createdAt: '2025-01-01T00:00:00' }

function noti(id: number, over: Partial<AppNotification> = {}): AppNotification {
  return {
    id,
    type: 'ORDER_STATUS',
    title: `Đơn BB${id} đang được giao`,
    message: 'Shipper sẽ liên hệ bạn trong hôm nay.',
    link: `/orders/${id}`,
    read: false,
    createdAt: new Date().toISOString(),
    ...over,
  }
}
const page = (content: AppNotification[], over = {}) => ({
  content,
  page: 0,
  size: 15,
  totalElements: content.length,
  totalPages: 1,
  ...over,
})

function setup(list: AppNotification[], unread: number, extra = {}) {
  const calls = mockApi({
    'GET /me': { data: me },
    'GET /notifications': { data: page(list) },
    'GET /notifications/unread-count': { data: { total: unread, byType: {} } },
    ...extra,
  })
  loginAs()
  renderWithApp(<NotificationsPage />, {
    route: '/notifications',
    path: '/notifications',
    extraRoutes: <Route path="/orders/:id" element={<p>Trang chi tiết đơn</p>} />,
  })
  return calls
}

describe('Thông báo', () => {
  it('chưa có thông báo: trạng thái trống, không có nút "đã đọc tất cả"', async () => {
    setup([], 0)
    expect(await screen.findByText('Chưa có thông báo nào')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đánh dấu đã đọc tất cả' })).not.toBeInTheDocument()
  })

  it('hiện tiêu đề, nội dung và chấm "chưa đọc" chỉ ở thông báo chưa đọc', async () => {
    setup([noti(1), noti(2, { read: true, title: 'Đơn BB2 đã giao' })], 1)
    expect(await screen.findByText('Đơn BB1 đang được giao')).toBeInTheDocument()
    expect(screen.getByText('Đơn BB2 đã giao')).toBeInTheDocument()
    expect(screen.getAllByLabelText('Chưa đọc')).toHaveLength(1)
  })

  it('gọi API danh sách với đúng phân trang (page 0, size 15)', async () => {
    const calls = setup([noti(1)], 1)
    await screen.findByText('Đơn BB1 đang được giao')
    expect(calls.find((c) => c.url === '/notifications')?.params).toEqual({ page: 0, size: 15 })
  })

  it('bấm thông báo chưa đọc: đánh dấu đã đọc rồi mở trang liên quan', async () => {
    const calls = setup([noti(7)], 1, { 'PATCH /notifications/7/read': { data: null } })
    await userEvent.setup().click(await screen.findByRole('button', { name: /Đơn BB7 đang được giao/ }))
    expect(await screen.findByText('Trang chi tiết đơn')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PATCH' && c.url === '/notifications/7/read')).toBe(true)
  })

  it('bấm thông báo đã đọc: không gọi lại API đánh dấu', async () => {
    const calls = setup([noti(8, { read: true })], 0)
    await userEvent.setup().click(await screen.findByRole('button', { name: /Đơn BB8 đang được giao/ }))
    expect(await screen.findByText('Trang chi tiết đơn')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'PATCH')).toBe(false)
  })

  it('"Đánh dấu đã đọc tất cả": gọi PATCH read-all không lọc loại, rồi tải lại', async () => {
    let unread = 2
    const calls = setup([noti(1), noti(2)], 2, {
      'GET /notifications/unread-count': () => ({ data: { total: unread, byType: {} } }),
      'PATCH /notifications/read-all': () => {
        unread = 0
        return { data: 2 }
      },
    })
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Đánh dấu đã đọc tất cả' }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Đánh dấu đã đọc tất cả' })).not.toBeInTheDocument())
    const readAll = calls.find((c) => c.url === '/notifications/read-all')!
    expect(readAll.params.types).toBeUndefined()
  })

  it('bảo mật: link thông báo trỏ ra domain khác bị React Router chặn, người dùng ở lại website', async () => {
    // lỗi ném ra trong onClick được React báo qua sự kiện "error" của window; bắt lại để kiểm tra thay vì làm vỡ test
    const blocked: string[] = []
    const onError = (e: ErrorEvent) => {
      blocked.push(e.message)
      e.preventDefault()
    }
    window.addEventListener('error', onError)
    try {
      setup([noti(9, { link: 'https://evil.example/phish' })], 1, { 'PATCH /notifications/9/read': { data: null } })
      await userEvent.setup().click(await screen.findByRole('button', { name: /Đơn BB9/ }))
      await waitFor(() => expect(blocked.join(' ')).toContain('External navigation is not allowed'))
      expect(screen.getByTestId('location')).toHaveTextContent('/notifications')
    } finally {
      window.removeEventListener('error', onError)
    }
  })

  it('lỗi tải danh sách: hiện trạng thái lỗi', async () => {
    setup([], 0, { 'GET /notifications': { status: 500 } })
    expect(await screen.findByText('Máy chủ gặp lỗi, vui lòng thử lại sau.')).toBeInTheDocument()
  })
})
