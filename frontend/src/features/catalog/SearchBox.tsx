import { useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { catalogApi } from '@/api/endpoints'
import type { SearchSuggestion } from '@/api/types'
import { ProductArt } from '@/components/product/ProductArt'
import { formatPrice } from '@/lib/format'
import { matchRanges } from '@/lib/search'
import { useDebounced } from '@/lib/useDebounced'

// dòng cuối danh sách: tìm theo đúng chữ đã gõ
type Option = SearchSuggestion | { type: 'KEYWORD'; label: string }

const GROUP_LABEL: Record<SearchSuggestion['type'], string> = {
  PRODUCT: 'Sản phẩm',
  CATEGORY: 'Danh mục',
  SKILL: 'Nhóm kỹ năng',
}

function optionPath(option: Option): string {
  switch (option.type) {
    case 'PRODUCT':
      return `/products/${option.id}`
    case 'CATEGORY':
      return `/products?categoryId=${option.id}`
    case 'SKILL':
      return `/products?skills=${encodeURIComponent(option.code)}`
    case 'KEYWORD':
      return `/products?keyword=${encodeURIComponent(option.label)}`
  }
}

// Ô tìm kiếm ở thanh đầu trang. Gõ đến đâu gợi ý đến đó (sản phẩm, danh mục, nhóm kỹ năng),
// gõ không dấu vẫn ra tên có dấu; phần khớp được tô đậm. Lên / xuống để chọn, Enter để mở, Esc để đóng.
export function SearchBox({ className = '' }: { className?: string }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const [value, setValue] = useState(keyword)
  // từ khoá trên URL đổi (bấm link khác, xoá bộ lọc) thì ô tìm kiếm theo
  const [synced, setSynced] = useState(keyword)
  if (synced !== keyword) {
    setSynced(keyword)
    setValue(keyword)
  }
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()

  const typed = value.trim()
  const query = useDebounced(typed, 150)
  const suggestions = useQuery({
    queryKey: ['search-suggestions', query],
    queryFn: () => catalogApi.suggestions(query),
    enabled: open && query.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  })
  const options: Option[] = typed ? [...(suggestions.data ?? []), { type: 'KEYWORD', label: typed }] : []
  const showList = open && options.length > 0

  const go = (path: string) => {
    // mở sản phẩm / danh mục / nhóm kỹ năng thì xoá chữ đã gõ; tìm theo từ khoá thì ô giữ từ khoá (theo URL)
    if (!path.includes('keyword=')) setValue('')
    setOpen(false)
    setActive(-1)
    inputRef.current?.blur()
    navigate(path)
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const chosen = showList && active >= 0 ? options[active] : null
    if (chosen) go(optionPath(chosen))
    else go(typed ? `/products?keyword=${encodeURIComponent(typed)}` : '/products')
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
      return
    }
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
    e.preventDefault()
    if (!showList) {
      setOpen(true)
      return
    }
    const step = e.key === 'ArrowDown' ? 1 : -1
    // -1 = chưa chọn dòng nào (Enter sẽ tìm theo chữ đã gõ)
    setActive((i) => ((i + step + 1 + options.length + 1) % (options.length + 1)) - 1)
  }

  return (
    <form role="search" onSubmit={submit} className={`relative ${className}`}>
      <label className="relative block">
        <span className="sr-only">Tìm sản phẩm</span>
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" aria-hidden>
          ⌕
        </span>
        <input
          ref={inputRef}
          type="search"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
          autoComplete="off"
          value={value}
          onChange={(e) => {
            setValue(e.target.value)
            setOpen(true)
            setActive(-1)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          placeholder="Tìm đồ chơi, kỹ năng…"
          className="w-full rounded-full border-2 border-sky/30 bg-sky-soft/60 py-2 pl-9 pr-4 text-[14px] outline-none transition focus:border-sky focus:bg-surface"
        />
      </label>
      {showList && (
        <ul
          id={listId}
          role="listbox"
          aria-label="Gợi ý tìm kiếm"
          // giữ focus ở ô nhập khi bấm vào gợi ý, để onBlur không đóng danh sách trước khi bấm xong
          onMouseDown={(e) => e.preventDefault()}
          className="absolute inset-x-0 top-full z-50 mt-1.5 max-h-[70vh] overflow-y-auto rounded-[18px] border border-line bg-surface py-1.5 shadow-[0_12px_32px_rgba(15,23,42,0.14)] md:left-auto md:right-0 md:w-[min(28rem,calc(100vw-2rem))]"
        >
          {options.map((option, i) => {
            const groupStart = option.type !== 'KEYWORD' && options[i - 1]?.type !== option.type
            return (
              <li key={`${option.type}-${option.label}-${i}`} role="presentation">
                {groupStart && (
                  <p className="px-4 pb-1 pt-2 text-[11.5px] font-bold uppercase tracking-wide text-ink-faint">
                    {GROUP_LABEL[option.type]}
                  </p>
                )}
                <div
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={active === i}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => go(optionPath(option))}
                  className={`flex cursor-pointer items-center gap-3 px-4 py-2 text-[14px] ${
                    active === i ? 'bg-sky-soft' : ''
                  } ${option.type === 'KEYWORD' ? 'mt-1 border-t border-line pt-2.5' : ''}`}
                >
                  <OptionContent option={option} typed={typed} />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </form>
  )
}

function OptionContent({ option, typed }: { option: Option; typed: string }) {
  if (option.type === 'KEYWORD') {
    return (
      <>
        <span className="grid h-8 w-8 shrink-0 place-items-center text-ink-faint" aria-hidden>
          ⌕
        </span>
        <span className="min-w-0 flex-1 truncate text-ink-2">
          Tìm tất cả “<b className="text-ink">{option.label}</b>”
        </span>
      </>
    )
  }
  return (
    <>
      {option.type === 'PRODUCT' ? (
        <span className="h-8 w-11 shrink-0 overflow-hidden rounded-md">
          <ProductArt url={option.thumbnailUrl} name="" className="h-full" />
        </span>
      ) : (
        <span className="grid h-8 w-11 shrink-0 place-items-center rounded-md bg-muted text-[16px]" aria-hidden>
          {option.type === 'CATEGORY' ? '🗂️' : '🧠'}
        </span>
      )}
      <span className="line-clamp-2 min-w-0 flex-1 leading-snug">
        <Highlighted text={option.label} query={typed} />
      </span>
      {option.type === 'PRODUCT' && (
        <span className="shrink-0 text-[12.5px] font-semibold text-coral">{formatPrice(option.price)}</span>
      )}
    </>
  )
}

// tô đậm phần tên khớp với chữ đã gõ (so không dấu), phần còn lại giữ nguyên tên gốc có dấu
function Highlighted({ text, query }: { text: string; query: string }) {
  const parts: { text: string; match: boolean }[] = []
  let cursor = 0
  for (const [start, end] of matchRanges(text, query)) {
    if (start > cursor) parts.push({ text: text.slice(cursor, start), match: false })
    parts.push({ text: text.slice(start, end), match: true })
    cursor = end
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), match: false })
  return (
    <>
      {parts.map((p, i) =>
        p.match ? (
          <mark key={i} className="bg-transparent font-bold text-primary">
            {p.text}
          </mark>
        ) : (
          <span key={i}>{p.text}</span>
        ),
      )}
    </>
  )
}
