import { describe, expect, it, vi } from 'vitest'
import { ApiError, http, request, toApiError } from '@/api/client'
import { tokenStorage } from '@/auth/tokenStorage'
import { loginAs, mockApi } from './helpers'

describe('http client: JWT và xử lý lỗi', () => {
  it('gắn header Authorization Bearer khi đã đăng nhập', async () => {
    const calls = mockApi({ 'GET /me': { data: { id: 1 } } })
    loginAs('CUSTOMER', 7)
    await request(http.get('/me'))
    expect(calls[0].headers.Authorization).toBe('Bearer token-CUSTOMER-7')
  })

  it('không gắn Authorization khi chưa đăng nhập', async () => {
    const calls = mockApi({ 'GET /products': { data: [] } })
    await request(http.get('/products'))
    expect(calls[0].headers.Authorization).toBeUndefined()
  })

  it('request() bóc lớp ApiResponse và trả về data', async () => {
    mockApi({ 'GET /skills': { data: [{ id: 1, code: 'LOGIC', name: 'Logic' }] } })
    await expect(request(http.get('/skills'))).resolves.toEqual([{ id: 1, code: 'LOGIC', name: 'Logic' }])
  })

  it('401 khi đang có token: xoá phiên và phát sự kiện auth:expired', async () => {
    mockApi({ 'GET /cart': { status: 401, message: 'Unauthorized' } })
    loginAs()
    const onExpired = vi.fn()
    window.addEventListener('auth:expired', onExpired)
    await expect(request(http.get('/cart'))).rejects.toMatchObject({ status: 401 })
    window.removeEventListener('auth:expired', onExpired)
    expect(tokenStorage.get()).toBeNull()
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('401 khi không có token (sai mật khẩu): không phát auth:expired', async () => {
    mockApi({ 'POST /auth/login': { status: 401, message: 'Incorrect email or password' } })
    const onExpired = vi.fn()
    window.addEventListener('auth:expired', onExpired)
    await expect(request(http.post('/auth/login', {}))).rejects.toBeInstanceOf(ApiError)
    window.removeEventListener('auth:expired', onExpired)
    expect(onExpired).not.toHaveBeenCalled()
  })

  it('dịch thông báo lỗi backend sang tiếng Việt (khớp nguyên câu)', async () => {
    mockApi({ 'POST /auth/login': { status: 403, message: 'Account is locked' } })
    const err = await request(http.post('/auth/login', {})).catch((e: unknown) => e as ApiError)
    expect(err.status).toBe(403)
    expect(err.rawMessage).toBe('Account is locked')
    expect(err.message).toContain('đang bị khoá')
  })

  it('dịch thông báo lỗi có tiền tố (thiếu hàng trong kho)', async () => {
    mockApi({ 'POST /orders': { status: 400, message: 'Not enough stock for: Robot ABC' } })
    const err = await request(http.post('/orders', {})).catch((e: unknown) => e as ApiError)
    expect(err.message).toBe('Không đủ hàng trong kho cho: Robot ABC')
  })

  it('lỗi 5xx không có bản dịch thì hiện câu chung, không lộ chi tiết server', async () => {
    mockApi({ 'GET /orders': { status: 500, message: 'NullPointerException at OrderService' } })
    const err = await request(http.get('/orders')).catch((e: unknown) => e as ApiError)
    expect(err.message).toBe('Máy chủ gặp lỗi, vui lòng thử lại sau.')
  })

  it('mất mạng: ApiError status 0 với câu tiếng Việt', () => {
    const err = toApiError(Object.assign(new Error('Network Error'), { isAxiosError: true, config: {} }))
    expect(err.status).toBe(0)
    expect(err.message).toContain('Không kết nối được máy chủ')
  })
})
