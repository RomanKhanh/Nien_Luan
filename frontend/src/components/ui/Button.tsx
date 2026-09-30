import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'

type Variant = 'primary' | 'secondary' | 'ghost' | 'accent' | 'danger' | 'danger-outline' | 'soft' | 'dark'
type Size = 'sm' | 'md' | 'lg'

const VARIANT: Record<Variant, string> = {
  // nút "kẹo" có gờ dưới, nhấn xuống khi bấm (phong cách đồ chơi)
  primary: 'bg-primary text-white shadow-[0_4px_0_#3a22b8] hover:bg-primary-hover',
  secondary: 'bg-surface text-ink border-2 border-line-strong shadow-[0_3px_0_var(--color-line)] hover:border-ink',
  ghost: 'bg-transparent text-primary hover:bg-primary-soft',
  accent: 'bg-sun text-ink shadow-[0_4px_0_#c98f00] hover:brightness-105',
  danger: 'bg-danger text-white shadow-[0_4px_0_#9f1c1c] hover:brightness-95',
  'danger-outline': 'bg-surface text-danger border-2 border-[#FBCFCF] hover:border-danger',
  soft: 'bg-primary-soft text-primary-hover hover:brightness-[0.97]',
  dark: 'bg-ink text-white shadow-[0_4px_0_#000] hover:bg-ink-2',
}

const SIZE: Record<Size, string> = {
  sm: 'text-[13px] px-4 py-2 rounded-full',
  md: 'text-[14px] px-5 py-[11px] rounded-full',
  lg: 'text-[15px] px-7 py-[15px] rounded-full',
}

const BASE =
  'inline-flex items-center justify-center gap-2 font-bold whitespace-nowrap transition select-none ' +
  'hover:-translate-y-px active:translate-y-[3px] active:shadow-none ' +
  'disabled:cursor-not-allowed disabled:bg-[#E8E6E1] disabled:text-ink-faint disabled:border-transparent ' +
  'disabled:shadow-none disabled:translate-y-0'

export function buttonClass(variant: Variant = 'primary', size: Size = 'md', extra = '') {
  return `${BASE} ${VARIANT[variant]} ${SIZE[size]} ${extra}`
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  block?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, block, className = '', disabled, children, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      className={buttonClass(variant, size, `${block ? 'w-full' : ''} ${className}`)}
      {...rest}
    >
      {loading && <Spinner />}
      {children}
    </button>
  )
})

interface ButtonLinkProps extends LinkProps {
  variant?: Variant
  size?: Size
  block?: boolean
}

export function ButtonLink({ variant = 'primary', size = 'md', block, className = '', ...rest }: ButtonLinkProps) {
  // màu chữ của nút nằm ở layer utilities nên thắng màu link mặc định ở layer base
  return <Link className={buttonClass(variant, size, `${block ? 'w-full' : ''} ${className}`)} {...rest} />
}

export function Spinner({ className = 'h-4 w-4' }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}
