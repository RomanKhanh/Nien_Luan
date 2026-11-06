import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ChildProfile } from '@/api/types'
import ChildrenPage from '@/pages/children/ChildrenPage'
import { loginAs, mockApi, renderWithApp } from './helpers'

const skills = [
  { id: 1, code: 'LOGIC', name: 'Tư duy Logic' },
  { id: 2, code: 'CREATIVE', name: 'Sáng tạo' },
]
const me = { id: 1, fullName: 'Phụ Huynh A', email: 'a@test.vn', createdAt: '2025-01-01T00:00:00' }

function child(over: Partial<ChildProfile> = {}): ChildProfile {
  return {
    id: 5,
    name: 'Minh An',
    birthDate: '2019-05-10',
    age: 7,
    gender: 'MALE',
    note: null,
    interestedSkills: [skills[1]],
    createdAt: '2026-01-01T00:00:00',
    ...over,
  }
}

const baseRoutes = {
  'GET /me': { data: me },
  'GET /skills': { data: skills },
  'GET /children/5/skill-profile': { data: { totalProducts: 2, skillScores: [] } },
}

const renderPage = () => renderWithApp(<ChildrenPage />, { route: '/children', path: '/children' })

afterEach(() => {
  vi.useRealTimers()
})

describe('Hồ sơ bé', () => {
  it('chưa có hồ sơ: hiện trạng thái trống và nút tạo hồ sơ đầu tiên', async () => {
    mockApi({ ...baseRoutes, 'GET /children': { data: [] } })
    loginAs()
    renderPage()
    expect(await screen.findByText('Chưa có hồ sơ bé nào')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tạo hồ sơ đầu tiên' })).toBeInTheDocument()
  })

  it('liệt kê hồ sơ bé với tuổi, nhóm kỹ năng quan tâm và số đồ chơi', async () => {
    mockApi({ ...baseRoutes, 'GET /children': { data: [child()] } })
    loginAs()
    renderPage()
    expect(await screen.findByText('Bé Minh An')).toBeInTheDocument()
    expect(screen.getByText(/7 tuổi/)).toBeInTheDocument()
    expect(await screen.findByText(/2 đồ chơi · quan tâm: Sáng tạo/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Xem lộ trình' })).toHaveAttribute('href', '/children/5')
  })

  it('tạo hồ sơ: bỏ trống tên + ngày sinh thì báo lỗi, không gọi API', async () => {
    const calls = mockApi({ ...baseRoutes, 'GET /children': { data: [] } })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tạo hồ sơ đầu tiên' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    expect(await within(dialog).findByText('Vui lòng nhập tên gọi của bé')).toBeInTheDocument()
    expect(within(dialog).getByText('Vui lòng chọn ngày sinh')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'POST' && c.url === '/children')).toBe(false)
  })

  it('tạo hồ sơ: ngày sinh ở tương lai bị từ chối', async () => {
    mockApi({ ...baseRoutes, 'GET /children': { data: [] } })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tạo hồ sơ đầu tiên' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Tên gọi của bé/), 'Bông')
    await user.type(within(dialog).getByLabelText(/Ngày sinh/), '2099-01-01')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    expect(await within(dialog).findByText('Ngày sinh không hợp lệ')).toBeInTheDocument()
  })

  it('tạo hồ sơ: bé sinh hôm nay (giờ địa phương) được chấp nhận, khớp @PastOrPresent của backend', async () => {
    const d = new Date()
    const todayLocal = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const calls = mockApi({
      ...baseRoutes,
      'GET /children': { data: [] },
      'POST /children': { data: child({ id: 6, name: 'Sơ sinh' }) },
      'GET /children/6/skill-profile': { data: { totalProducts: 0, skillScores: [] } },
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tạo hồ sơ đầu tiên' }))
    const dialog = await screen.findByRole('dialog')
    const birth = within(dialog).getByLabelText(/Ngày sinh/)
    expect(birth).toHaveAttribute('max', todayLocal)
    await user.type(within(dialog).getByLabelText(/Tên gọi của bé/), 'Sơ sinh')
    await user.type(birth, todayLocal)
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ birthDate: todayLocal }))
  })

  it('tạo hồ sơ hợp lệ: gửi đúng body (tên, ngày sinh, giới tính, kỹ năng quan tâm)', async () => {
    let created = false
    const calls = mockApi({
      ...baseRoutes,
      'GET /children': () => ({ data: created ? [child({ id: 6, name: 'Bông' })] : [] }),
      'POST /children': () => {
        created = true
        return { data: child({ id: 6, name: 'Bông' }) }
      },
      'GET /children/6/skill-profile': { data: { totalProducts: 0, skillScores: [] } },
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tạo hồ sơ đầu tiên' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Tên gọi của bé/), '  Bông  ')
    await user.type(within(dialog).getByLabelText(/Ngày sinh/), '2020-03-15')
    await user.click(within(dialog).getByRole('button', { name: 'Nữ' }))
    await user.click(await within(dialog).findByRole('button', { name: 'Tư duy Logic' }))
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url === '/children')).toBe(true))
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      name: 'Bông',
      birthDate: '2020-03-15',
      gender: 'FEMALE',
      note: null,
      interestedSkillIds: [1],
    })
    expect(await screen.findByText('Đã tạo hồ sơ cho bé Bông')).toBeInTheDocument()
    expect(await screen.findByText('Bé Bông')).toBeInTheDocument()
  })

  it('sửa hồ sơ: form điền sẵn dữ liệu và gọi PUT /children/{id}', async () => {
    const calls = mockApi({
      ...baseRoutes,
      'GET /children': { data: [child()] },
      'PUT /children/5': (call) => ({ data: { ...child(), ...(call.body as object) } }),
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Sửa' }))
    const dialog = await screen.findByRole('dialog')
    const name = within(dialog).getByLabelText(/Tên gọi của bé/)
    expect(name).toHaveValue('Minh An')
    await user.clear(name)
    await user.type(name, 'An Nhiên')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({
      name: 'An Nhiên',
      birthDate: '2019-05-10',
      gender: 'MALE',
      interestedSkillIds: [2],
    })
  })

  it('xoá hồ sơ: hỏi xác nhận rồi gọi DELETE /children/{id}', async () => {
    let deleted = false
    const calls = mockApi({
      ...baseRoutes,
      'GET /children': () => ({ data: deleted ? [] : [child()] }),
      'DELETE /children/5': () => {
        deleted = true
        return { data: null }
      },
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Sửa' }))
    const drawer = await screen.findByRole('dialog')
    await user.click(within(drawer).getByRole('button', { name: 'Xoá hồ sơ' }))
    const confirm = (await screen.findAllByRole('dialog')).find((d) => d.textContent?.includes('Xoá hồ sơ bé Minh An?'))!
    await user.click(within(confirm).getByRole('button', { name: 'Xoá hồ sơ' }))
    expect(await screen.findByText('Chưa có hồ sơ bé nào')).toBeInTheDocument()
    expect(calls.some((c) => c.method === 'DELETE' && c.url === '/children/5')).toBe(true)
  })

  it('backend trả 404 (hồ sơ của người khác) khi lưu: hiện lỗi đã dịch', async () => {
    mockApi({
      ...baseRoutes,
      'GET /children': { data: [child()] },
      'PUT /children/5': { status: 404, message: 'Child profile not found' },
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Sửa' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    expect(await screen.findByText('Không tìm thấy hồ sơ của bé.')).toBeInTheDocument()
  })

  // Lúc 06:00 giờ VN (UTC+7) ngày 04/11, ChildFormModal tính "hôm nay" bằng toISOString() (giờ UTC) nên ra 03/11.
  // Bé sinh ngày 03/11 (hôm qua theo giờ VN, hợp lệ với @Past của backend) bị form chặn nhầm.
  it('ngày sinh hôm qua theo giờ địa phương phải được chấp nhận (kể cả trước 7h sáng giờ VN)', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-03T23:00:00Z')) // = 06:00 ngày 04/11 giờ VN
    process.env.TZ = 'Asia/Ho_Chi_Minh'
    // bảo đảm môi trường test đúng là 06:00 ngày 04/11 giờ địa phương
    expect([new Date().getDate(), new Date().getHours()]).toEqual([4, 6])
    const calls = mockApi({
      ...baseRoutes,
      'GET /children': { data: [] },
      'POST /children': { data: child({ id: 6, name: 'Sơ sinh' }) },
      'GET /children/6/skill-profile': { data: { totalProducts: 0, skillScores: [] } },
    })
    loginAs()
    renderPage()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tạo hồ sơ đầu tiên' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/Tên gọi của bé/), 'Sơ sinh')
    await user.type(within(dialog).getByLabelText(/Ngày sinh/), '2026-11-03')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu hồ sơ' }))
    await waitFor(() => expect(within(dialog).queryByText('Ngày sinh không hợp lệ')).not.toBeInTheDocument())
    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url === '/children')).toBe(true))
  })
})
