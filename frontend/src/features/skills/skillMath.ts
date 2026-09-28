import type { SkillScore } from '@/api/types'

/**
 * Backend lưu score = tổng impactIndex (0-10) của mọi đồ chơi bé có vào nhóm kỹ năng đó.
 * Hiển thị dạng thang 0-10 như mockup ("Logic 8,0/10") bằng mức tác động trung bình mỗi món.
 */
export function averageImpact(score: SkillScore, totalProducts: number): number {
  if (totalProducts <= 0) return 0
  return Math.round((score.score / totalProducts) * 10) / 10
}
