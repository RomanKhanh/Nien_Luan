import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '@/api/endpoints'

// danh mục & nhóm kỹ năng ít thay đổi: giữ cache lâu, dùng chung cho bộ lọc, form, trang chủ
export function useSkills() {
  return useQuery({ queryKey: ['skills'], queryFn: catalogApi.skills, staleTime: 10 * 60_000 })
}

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: catalogApi.categories, staleTime: 5 * 60_000 })
}
