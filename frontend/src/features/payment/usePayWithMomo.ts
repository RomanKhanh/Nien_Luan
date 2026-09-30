import { useMutation } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { paymentApi } from '@/api/endpoints'

export const paymentKey = (orderId: number) => ['payment', orderId] as const

// Lấy link thanh toán từ backend rồi chuyển trình duyệt sang trang MoMo.
// Thành công thì trang hiện tại bị rời đi nên mutation giữ trạng thái "đang xử lý" tới lúc đó.
export function usePayWithMomo() {
  return useMutation({
    mutationFn: async (orderId: number) => {
      const url = await paymentApi.createUrl(orderId)
      // chỉ chuyển hướng tới địa chỉ https hợp lệ do backend trả về
      let target: URL
      try {
        target = new URL(url)
      } catch {
        throw new ApiError(-1, 'Link thanh toán không hợp lệ.')
      }
      if (target.protocol !== 'https:') throw new ApiError(-1, 'Link thanh toán không hợp lệ.')
      window.location.assign(target.href)
      // chờ trình duyệt rời trang; không resolve để nút vẫn ở trạng thái đang tải
      return new Promise<never>(() => {})
    },
  })
}
