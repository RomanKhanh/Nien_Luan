import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { cartApi } from '@/api/endpoints'
import type { ApiError } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { useToast } from '@/components/ui/Toast'

export const cartKey = ['cart'] as const

// giỏ hàng chỉ có với khách hàng đã đăng nhập
export function useCart() {
  const { isCustomer } = useAuth()
  return useQuery({ queryKey: cartKey, queryFn: cartApi.get, enabled: isCustomer })
}

export function useCartCount(): number {
  const { data } = useCart()
  return data?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0
}

// thêm vào giỏ: chưa đăng nhập thì chuyển tới trang đăng nhập rồi quay lại trang hiện tại
export function useAddToCart(options?: { silent?: boolean }) {
  const { session, isCustomer } = useAuth()
  const queryClient = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  return useMutation({
    mutationFn: async ({ productId, quantity }: { productId: number; quantity: number }) => {
      if (!session) {
        navigate('/login', { state: { from: location.pathname + location.search } })
        throw new Error('login-required')
      }
      if (!isCustomer) {
        throw new Error('customer-only')
      }
      return cartApi.add(productId, quantity)
    },
    onSuccess: (cart) => {
      queryClient.setQueryData(cartKey, cart)
      if (!options?.silent) toast.success('Đã thêm vào giỏ hàng')
    },
    onError: (error: Error) => {
      if (error.message === 'login-required') return
      if (error.message === 'customer-only') {
        toast.error('Tài khoản quản trị không dùng giỏ hàng.')
        return
      }
      toast.error((error as ApiError).message)
    },
  })
}
