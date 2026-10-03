// Bỏ dấu tiếng Việt từng ký tự và giữ nguyên độ dài chuỗi, để vị trí khớp trên chuỗi đã bỏ dấu
// cũng là vị trí trên tên gốc có dấu (dùng để tô đậm phần khớp trong gợi ý tìm kiếm).
function fold(text: string): string {
  return text
    .split('')
    .map((ch) => {
      const base = ch.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()
      return base.length === 1 ? base : ch
    })
    .join('')
}

// các đoạn [start, end) của text khớp với query, so không dấu, không phân biệt hoa thường.
// Khớp nguyên cụm thì tô cả cụm, không thì tô từng từ đã gõ.
export function matchRanges(text: string, query: string): [number, number][] {
  const folded = fold(text)
  const q = fold(query).replace(/\s+/g, ' ').trim()
  if (!q) return []
  const whole = folded.indexOf(q)
  if (whole >= 0) return [[whole, whole + q.length]]
  const ranges: [number, number][] = []
  for (const token of q.split(' ')) {
    const at = folded.indexOf(token)
    if (at >= 0) ranges.push([at, at + token.length])
  }
  ranges.sort((a, b) => a[0] - b[0])
  // gộp các đoạn chồng nhau
  return ranges.reduce<[number, number][]>((merged, r) => {
    const last = merged[merged.length - 1]
    if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1])
    else merged.push([...r])
    return merged
  }, [])
}

// chữ để so khớp không dấu: "  Thành phố  Cần Thơ " -> "thanh pho can tho"
export function normalizeSearch(text: string): string {
  return fold(text).replace(/\s+/g, ' ').trim()
}
