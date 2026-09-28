// trang tính từ 0 như Spring Data; hiển thị từ 1
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  const pages = visiblePages(page, totalPages)
  const btn = 'grid h-9 min-w-9 place-items-center rounded-md px-2 text-[14px] font-semibold transition'
  return (
    <nav className="flex items-center justify-center gap-1.5" aria-label="Phân trang">
      <button
        type="button"
        className={`${btn} border border-line bg-surface disabled:opacity-40`}
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        aria-label="Trang trước"
      >
        ‹
      </button>
      {pages.map((p, i) =>
        p === -1 ? (
          <span key={`gap-${i}`} className="px-1 text-ink-muted">
            …
          </span>
        ) : (
          <button
            type="button"
            key={p}
            onClick={() => onChange(p)}
            aria-current={p === page ? 'page' : undefined}
            className={`${btn} ${p === page ? 'bg-ink text-white' : 'border border-line bg-surface hover:border-ink'}`}
          >
            {p + 1}
          </button>
        ),
      )}
      <button
        type="button"
        className={`${btn} border border-line bg-surface disabled:opacity-40`}
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        aria-label="Trang sau"
      >
        ›
      </button>
    </nav>
  )
}

// luôn có trang đầu, trang cuối và 1 trang quanh trang hiện tại; -1 = dấu …
function visiblePages(page: number, total: number): number[] {
  const set = new Set([0, total - 1, page - 1, page, page + 1].filter((p) => p >= 0 && p < total))
  const sorted = [...set].sort((a, b) => a - b)
  const result: number[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) result.push(-1)
    result.push(p)
  })
  return result
}
