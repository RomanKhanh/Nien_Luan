import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAuth } from '@/auth/AuthContext'
import { tokenStorage } from '@/auth/tokenStorage'
import { loginAs, mockApi, renderWithApp } from './helpers'

function LogoutButton() {
  const { session, logout } = useAuth()
  return session ? <button onClick={logout}>Đăng xuất</button> : <p>Đã đăng xuất</p>
}

describe('Đăng xuất', () => {
  it('gọi POST /auth/logout kèm token của thiết bị này rồi xoá phiên ở client', async () => {
    const calls = mockApi({ 'POST /auth/logout': { data: null } })
    loginAs('CUSTOMER', 9)
    renderWithApp(<LogoutButton />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(await screen.findByText('Đã đăng xuất')).toBeInTheDocument()
    await waitFor(() => expect(calls.some((c) => c.url === '/auth/logout')).toBe(true))
    expect(calls.find((c) => c.url === '/auth/logout')!.headers.Authorization).toBe('Bearer token-CUSTOMER-9')
    expect(tokenStorage.get()).toBeNull()
    expect(localStorage.getItem('brainblocks.auth')).toBeNull()
  })

  it('server lỗi / mất mạng: vẫn đăng xuất ở client', async () => {
    mockApi({ 'POST /auth/logout': { status: 500 } })
    loginAs()
    renderWithApp(<LogoutButton />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(await screen.findByText('Đã đăng xuất')).toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })
})
