import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { normalizeSearch } from '@/lib/search'
import { MENU_ITEM, MENU_PANEL } from './menuStyles'

export interface DropdownOption<T extends string> {
  value: T
  label: string
}

/**
 * Ô chọn một giá trị có danh sách xổ xuống cùng kiểu với menu tài khoản ở header, thay cho <select> gốc
 * (danh sách do trình duyệt / hệ điều hành tự vẽ nên lệch giao diện).
 * Bàn phím: ↑ ↓ Home End di chuyển, Enter / Space chọn, Esc đóng, gõ chữ để nhảy tới lựa chọn bắt đầu bằng chữ đó
 * (không phân biệt dấu). Bấm ra ngoài thì đóng; gần đáy màn hình thì danh sách mở lên trên.
 *
 * variant "field": ô nhập trong form (rộng hết khung, có trạng thái lỗi); "pill": nút bo tròn nhỏ đặt giữa dòng chữ.
 * placeholder: chữ mờ khi value không khớp lựa chọn nào (vd value "" = chưa chọn).
 */
export function DropdownSelect<T extends string>({
  value,
  options,
  onChange,
  onBlur,
  id,
  label,
  placeholder,
  invalid = false,
  disabled = false,
  variant = 'field',
  size = 'md',
  align = 'left',
  className = '',
}: {
  value: T
  options: DropdownOption<T>[]
  onChange: (value: T) => void
  onBlur?: () => void
  id?: string
  // tên đọc cho trình đọc màn hình khi không có <label htmlFor> đi kèm
  label?: string
  placeholder?: string
  invalid?: boolean
  disabled?: boolean
  variant?: 'field' | 'pill'
  // sm: ô thấp, chữ nhỏ (thanh công cụ như ô sắp xếp)
  size?: 'sm' | 'md'
  align?: 'left' | 'right'
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const [up, setUp] = useState(false)
  const [active, setActive] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const typed = useRef({ text: '', at: 0 })
  const listId = useId()
  const selected = options.find((o) => o.value === value)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  // giữ dòng đang trỏ trong vùng nhìn thấy khi danh sách dài phải cuộn
  useEffect(() => {
    if (open) listRef.current?.children[active]?.scrollIntoView({ block: 'nearest' })
  }, [open, active])

  const show = (at?: number) => {
    if (disabled || options.length === 0) return
    const rect = buttonRef.current?.getBoundingClientRect()
    // danh sách cao tối đa ~300px: dưới không đủ chỗ mà trên rộng hơn thì mở lên trên
    setUp(rect ? window.innerHeight - rect.bottom < 300 && rect.top > window.innerHeight - rect.bottom : false)
    setActive(at ?? Math.max(0, options.findIndex((o) => o.value === value)))
    setOpen(true)
  }
  const close = (focus: boolean) => {
    setOpen(false)
    if (focus) buttonRef.current?.focus()
  }
  const choose = (option: DropdownOption<T>) => {
    close(true)
    if (option.value !== value) onChange(option.value)
  }

  // gõ chữ: nhảy tới lựa chọn đầu tiên (tính từ sau dòng hiện tại) có nhãn bắt đầu bằng các chữ vừa gõ
  const typeAhead = (key: string, now: number) => {
    typed.current = { text: (now - typed.current.at < 700 ? typed.current.text : '') + key, at: now }
    const q = normalizeSearch(typed.current.text)
    const from = typed.current.text.length > 1 ? active : active + 1
    const order = [...options.slice(from), ...options.slice(0, from)]
    const match = order.find((o) => normalizeSearch(o.label).startsWith(q))
    if (!match) return
    const index = options.indexOf(match)
    if (open) setActive(index)
    else show(index)
  }

  const onKeyDown = (e: KeyboardEvent) => {
    if (disabled) return
    if (e.key.length === 1 && e.key !== ' ' && !e.ctrlKey && !e.metaKey && !e.altKey) {
      typeAhead(e.key, e.timeStamp)
      return
    }
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(e.key)) {
        e.preventDefault()
        show()
      }
      return
    }
    const last = options.length - 1
    if (e.key === 'ArrowDown') setActive((i) => Math.min(last, i + 1))
    else if (e.key === 'ArrowUp') setActive((i) => Math.max(0, i - 1))
    else if (e.key === 'Home') setActive(0)
    else if (e.key === 'End') setActive(last)
    else if (e.key === 'Enter' || e.key === ' ') choose(options[active])
    else if (e.key === 'Escape') close(true)
    else if (e.key === 'Tab') {
      close(false)
      return
    } else return
    e.preventDefault()
  }

  const trigger =
    variant === 'pill'
      ? 'inline-flex max-w-full items-center gap-1.5 rounded-full border-2 border-line bg-sky-soft/60 px-3 py-1 font-bold text-ink outline-none transition hover:border-line-strong focus-visible:border-sky disabled:cursor-not-allowed disabled:opacity-60'
      : `input flex items-center gap-2 text-left ${size === 'sm' ? '!py-2 text-[13px]' : ''} ${invalid ? 'input-error' : ''} ${open ? '!border-sky' : ''}`

  return (
    <div
      ref={ref}
      className={`relative ${variant === 'pill' ? 'inline-block max-w-full' : ''} ${className}`}
      onKeyDown={onKeyDown}
      onBlur={(e) => {
        if (!ref.current?.contains(e.relatedTarget as Node)) onBlur?.()
      }}
    >
      <button
        ref={buttonRef}
        id={id}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-invalid={invalid || undefined}
        aria-label={label ? `${label}: ${selected?.label ?? placeholder ?? ''}` : undefined}
        aria-activedescendant={open ? `${listId}-${active}` : undefined}
        onClick={() => (open ? close(false) : show())}
        className={`w-full ${trigger}`}
      >
        <span className={`min-w-0 truncate ${selected ? '' : 'text-ink-faint'}`}>{selected?.label ?? placeholder}</span>
        <svg
          viewBox="0 0 10 6"
          className={`ml-auto h-1.5 w-2.5 shrink-0 text-ink-muted transition-transform ${open ? 'rotate-180' : ''}`}
          aria-hidden
        >
          <path d="M1 1l4 4 4-4" stroke="currentColor" strokeWidth="1.6" fill="none" strokeLinecap="round" />
        </svg>
      </button>
      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-label={label}
          className={`absolute z-40 max-h-72 w-max min-w-full max-w-[min(22rem,calc(100vw-2rem))] overflow-y-auto font-normal ${
            up ? 'bottom-full mb-2' : 'top-full mt-2'
          } ${align === 'right' ? 'right-0' : 'left-0'} ${MENU_PANEL}`}
        >
          {options.map((option, i) => {
            const isSelected = option.value === value
            return (
              <li
                key={option.value}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(option)}
                className={`${MENU_ITEM} flex cursor-pointer items-center gap-2 ${i === active ? 'bg-muted text-ink' : ''} ${isSelected ? 'font-semibold !text-primary' : ''}`}
              >
                <span className="flex-1">{option.label}</span>
                <svg viewBox="0 0 16 16" className={`h-3.5 w-3.5 shrink-0 ${isSelected ? '' : 'invisible'}`} aria-hidden>
                  <path d="M3 8.5l3.2 3L13 4.5" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
