import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { UserProfile } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { Pill } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { ConfirmDialog } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { formatDate } from '@/lib/format'
import { useDebounced } from '@/lib/useDebounced'

export default function UsersPage() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const debounced = useDebounced(keyword, 300)
  const params = { keyword: debounced || undefined, page, size: 15 }
  const users = useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => adminApi.users(params),
    placeholderData: keepPreviousData,
  })
  const [target, setTarget] = useState<UserProfile | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()
  const toggle = useMutation({
    mutationFn: (u: UserProfile) => adminApi.updateUserStatus(u.id, !u.enabled),
    onSuccess: (u) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      toast.success(u.enabled ? `Đã mở khoá ${u.fullName}` : `Đã khoá ${u.fullName}`)
      setTarget(null)
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <AdminHeader
        title="Người dùng"
        description="Danh sách khách hàng; khoá tài khoản sẽ đăng xuất khách khỏi mọi thiết bị."
      />
      <div className="card overflow-hidden">
        <div className="border-b border-line p-4">
          <Input
            type="search"
            placeholder="Tìm theo tên hoặc email…"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
          />
        </div>
        {users.isPending ? (
          <PageLoader />
        ) : users.isError ? (
          <ErrorState message={users.error.message} onRetry={() => users.refetch()} />
        ) : users.data.content.length === 0 ? (
          <EmptyState title="Không có khách hàng phù hợp" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-[14px]">
              <thead className="bg-muted/60 text-[12.5px] uppercase tracking-[0.04em] text-ink-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Khách hàng</th>
                  <th className="px-4 py-3 font-semibold">Điện thoại</th>
                  <th className="px-4 py-3 font-semibold">Ngày tham gia</th>
                  <th className="px-4 py-3 font-semibold">Trạng thái</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {users.data.content.map((u) => (
                  <tr key={u.id}>
                    <td className="px-4 py-3">
                      <p className="font-semibold">{u.fullName}</p>
                      <p className="text-[12.5px] text-ink-muted">{u.email}</p>
                    </td>
                    <td className="px-4 py-3 text-ink-2">{u.phone ?? '—'}</td>
                    <td className="px-4 py-3 text-ink-2">{formatDate(u.createdAt)}</td>
                    <td className="px-4 py-3">
                      {u.enabled ? <Pill tone="green">Hoạt động</Pill> : <Pill tone="red">Đã khoá</Pill>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        size="sm"
                        variant={u.enabled ? 'danger-outline' : 'secondary'}
                        onClick={() => setTarget(u)}
                      >
                        {u.enabled ? 'Khoá' : 'Mở khoá'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {users.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={users.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>
      <ConfirmDialog
        open={Boolean(target)}
        title={target?.enabled ? 'Khoá tài khoản?' : 'Mở khoá tài khoản?'}
        message={
          target?.enabled ? (
            <>
              <b>{target.fullName}</b> ({target.email}) sẽ không đăng nhập và đặt hàng được cho tới khi được mở khoá.
            </>
          ) : (
            <>
              <b>{target?.fullName}</b> sẽ đăng nhập lại được bình thường.
            </>
          )
        }
        confirmLabel={target?.enabled ? 'Khoá tài khoản' : 'Mở khoá'}
        tone={target?.enabled ? 'danger' : 'primary'}
        loading={toggle.isPending}
        onConfirm={() => target && toggle.mutate(target)}
        onClose={() => setTarget(null)}
      />
    </>
  )
}
