import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '@/api/types'
import { EmptyState } from '@/components/ui/States'
import { ButtonLink } from '@/components/ui/Button'
import { useAuth } from './AuthContext'

// chặn route theo đăng nhập / vai trò; backend vẫn kiểm tra quyền, đây chỉ để điều hướng cho đúng
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  if (role && session.role !== role) {
    return (
      <div className="container-page">
        <EmptyState
          icon="⛔"
          title="Trang này không dành cho tài khoản của bạn"
          description={
            role === 'ADMIN' ? 'Chỉ quản trị viên mới truy cập được.' : 'Chức năng này dành cho tài khoản khách hàng.'
          }
          action={<ButtonLink to={session.role === 'ADMIN' ? '/admin' : '/'}>Về trang chính</ButtonLink>}
        />
      </div>
    )
  }
  return <>{children}</>
}
