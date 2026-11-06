import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import { tokenStorage } from '@/auth/tokenStorage'

afterEach(() => {
  cleanup()
  // tokenStorage giữ phiên trong biến module, phải xoá cả bộ nhớ lẫn localStorage giữa các test
  tokenStorage.clear()
  localStorage.clear()
})

// jsdom chưa có các API trình duyệt mà vài component dùng
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
if (!window.scrollTo || String(window.scrollTo).includes('not implemented')) {
  window.scrollTo = () => {}
}
class NoopObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return []
  }
}
Element.prototype.scrollIntoView ??= function () {}
globalThis.IntersectionObserver ??= NoopObserver as unknown as typeof IntersectionObserver
globalThis.ResizeObserver ??= NoopObserver as unknown as typeof ResizeObserver
