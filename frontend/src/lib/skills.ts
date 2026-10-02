import type { SkillImpact } from '@/api/types'

// màu & nhãn ngắn của 4 nhóm kỹ năng gốc (mockup màn 01); nhóm admin thêm sau dùng màu trung tính
export interface SkillTheme {
  color: string
  soft: string
  text: string
  bg: string
  short: string
}

const THEMES: Record<string, SkillTheme> = {
  LOGIC: { color: '#3D7BFF', soft: '#EBF1FF', text: 'text-skill-logic', bg: 'bg-skill-logic-soft', short: 'Logic' },
  CREATIVE: {
    color: '#E8467C',
    soft: '#FDEBF2',
    text: 'text-skill-creative',
    bg: 'bg-skill-creative-soft',
    short: 'Sáng tạo',
  },
  PROBLEM_SOLVING: {
    color: '#E08700',
    soft: '#FEF3E0',
    text: 'text-skill-solve',
    bg: 'bg-skill-solve-soft',
    short: 'GQVĐ',
  },
  STEM: { color: '#0F9E7A', soft: '#E6F6F1', text: 'text-skill-stem', bg: 'bg-skill-stem-soft', short: 'STEM' },
}

const FALLBACK: SkillTheme = {
  color: '#6B6862',
  soft: '#F1EFEA',
  text: 'text-skill-other',
  bg: 'bg-skill-other-soft',
  short: '',
}

export function skillTheme(code: string, name?: string): SkillTheme {
  return THEMES[code] ?? { ...FALLBACK, short: name ?? code }
}

// mô tả ngắn cho khối "Chọn theo nhóm kỹ năng" ở trang chủ
export const SKILL_HINTS: Record<string, string> = {
  LOGIC: 'Xếp hình, cờ, lập trình khối lệnh',
  CREATIVE: 'Thiết kế, mô hình, nghệ thuật kỹ thuật',
  PROBLEM_SOLVING: 'Mê cung, thử thách cơ khí, gỡ rối',
  STEM: 'Thí nghiệm, mạch điện, robot',
}

// Mức của điểm hồ sơ kỹ năng (0-10, lợi ích giảm dần). Hiển thị bằng chữ chứ không "x/10" để phụ huynh
// không so ngang với chỉ số tác động in trên từng sản phẩm: đây là độ phủ của cả bộ đồ chơi, không phải
// đánh giá năng lực của bé. Với alpha 0.5: 1 món 6 điểm ≈ 3, 1 món 10 điểm = 5, 3 món 8 điểm ≈ 7,8.
export function skillLevel(score: number): string {
  if (score <= 0) return 'Chưa có'
  if (score < 3.5) return 'Mới bắt đầu'
  if (score < 7) return 'Đang phát triển'
  return 'Phong phú'
}

// tối đa n nhóm có chỉ số cao nhất (quy ước product card: tối đa 2 badge)
export function topImpacts(impacts: SkillImpact[], n = 2): SkillImpact[] {
  return [...impacts]
    .filter((i) => i.impactIndex > 0)
    .sort((a, b) => b.impactIndex - a.impactIndex)
    .slice(0, n)
}

export function dominantSkill(impacts: SkillImpact[]): string | null {
  return topImpacts(impacts, 1)[0]?.skillCode ?? null
}

// nhóm tuổi dùng cho bộ lọc / trang chủ; giá trị lọc là tuổi đại diện ở giữa khoảng
export const AGE_GROUPS = [
  { label: '3 – 5 tuổi', from: 3, to: 5 },
  { label: '6 – 8 tuổi', from: 6, to: 8 },
  { label: '9 – 10 tuổi', from: 9, to: 10 },
  { label: '11 – 12 tuổi', from: 11, to: 12 },
]
