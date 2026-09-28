import type { QueryClient } from '@tanstack/react-query'

export const childKeys = {
  list: ['children'] as const,
  detail: (id: number) => ['child', id] as const,
  products: (id: number) => ['child', id, 'products'] as const,
  skillProfile: (id: number) => ['skill-profile', id] as const,
  timeline: (id: number) => ['skill-timeline', id] as const,
  recommendations: (id: number) => ['recommendations', id] as const,
}

// đồ chơi của bé thay đổi -> hồ sơ kỹ năng, lộ trình và gợi ý đều phải tải lại
export function invalidateChildData(queryClient: QueryClient, id: number) {
  for (const key of [
    childKeys.detail(id),
    childKeys.skillProfile(id),
    childKeys.timeline(id),
    childKeys.recommendations(id),
  ]) {
    queryClient.invalidateQueries({ queryKey: key })
  }
}
