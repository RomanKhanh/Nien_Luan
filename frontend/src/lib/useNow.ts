import { useEffect, useState } from 'react'

// thời điểm hiện tại (ms) giữ trong state để render thuần, tự cập nhật mỗi intervalMs (mặc định 1 phút)
export function useNow(intervalMs = 60_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}
