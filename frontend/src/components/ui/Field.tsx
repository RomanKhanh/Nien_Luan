import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'

interface FieldProps {
  label?: ReactNode
  error?: string
  hint?: ReactNode
  required?: boolean
  htmlFor?: string
  className?: string
  children: ReactNode
}

export function Field({ label, error, hint, required, htmlFor, className = '', children }: FieldProps) {
  return (
    <div className={className}>
      {label && (
        <label htmlFor={htmlFor} className="label">
          {label}
          {required && <span className="ml-0.5 text-danger">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p role="alert" className="mt-1.5 text-[12.5px] font-medium text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="mt-1.5 text-[12.5px] text-ink-muted">{hint}</p>
      )}
    </div>
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid, className = '', ...rest },
  ref,
) {
  return <input ref={ref} className={`input ${invalid ? 'input-error' : ''} ${className}`} {...rest} />
})

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { invalid, className = '', children, ...rest },
  ref,
) {
  return (
    <select
      ref={ref}
      className={`input appearance-none bg-[length:10px] bg-[right_14px_center] bg-no-repeat pr-9 ${invalid ? 'input-error' : ''} ${className}`}
      style={{
        backgroundImage:
          "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 6'%3E%3Cpath d='M1 1l4 4 4-4' stroke='%236B6862' stroke-width='1.6' fill='none' stroke-linecap='round'/%3E%3C/svg%3E\")",
      }}
      {...rest}
    >
      {children}
    </select>
  )
})

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { invalid, className = '', ...rest },
  ref,
) {
  return (
    <textarea
      ref={ref}
      className={`input min-h-[96px] resize-y ${invalid ? 'input-error' : ''} ${className}`}
      {...rest}
    />
  )
})

// chip chọn nhiều (nhóm kỹ năng quan tâm, bộ lọc)
export function Chip({
  selected,
  onClick,
  children,
  className = '',
}: {
  selected: boolean
  onClick: () => void
  children: ReactNode
  className?: string
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border-[1.5px] px-3.5 py-1.5 text-[13px] font-semibold transition ${
        selected
          ? 'border-primary bg-primary-soft text-primary-hover'
          : 'border-line-strong bg-surface text-ink-2 hover:border-ink'
      } ${className}`}
    >
      {selected && <span aria-hidden>✓ </span>}
      {children}
    </button>
  )
}
