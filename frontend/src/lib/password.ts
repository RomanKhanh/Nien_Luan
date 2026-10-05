import { z } from 'zod'

// mật khẩu mới khi đăng ký / đặt lại mật khẩu; backend chỉ kiểm tra 8-72 ký tự
export const newPasswordRule = z
  .string()
  .min(8, 'Tối thiểu 8 ký tự')
  .max(72, 'Tối đa 72 ký tự')
  .regex(/[A-Za-z]/, 'Cần có ít nhất một chữ cái')
  .regex(/[0-9]/, 'Cần có ít nhất một chữ số')
