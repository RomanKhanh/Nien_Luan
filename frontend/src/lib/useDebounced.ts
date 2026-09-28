import { useEffect, useState } from 'react'

// trễ giá trị một khoảng để ô tìm kiếm không gọi API theo từng phím gõ
export function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
