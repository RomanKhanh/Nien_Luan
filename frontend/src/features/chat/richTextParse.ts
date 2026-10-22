// Phân tích markdown tối giản của câu trả lời bot (**đậm**, *nghiêng*, dòng "- ", dòng trống tách đoạn)
// thành cấu trúc để RichText dựng phần tử React và đếm số ký tự hiển thị cho hiệu ứng gõ chữ.

export interface Part {
  style: 'b' | 'i' | null
  text: string
}

export interface Group {
  list: boolean
  lines: Part[][]
}

const BULLET = /^\s*[-•]\s+/

export function parseRichText(text: string): Group[] {
  return text
    .trim()
    .split(/\n\s*\n/)
    .flatMap(groups)
}

// tổng số ký tự hiển thị (độ dài đích của hiệu ứng gõ chữ)
export function richTextLength(groups: Group[]): number {
  return groups.reduce((sum, g) => sum + g.lines.reduce((s, l) => s + l.reduce((n, p) => n + p.text.length, 0), 0), 0)
}

// trong một đoạn: các dòng gạch đầu dòng liền nhau gom thành danh sách, các dòng khác thành đoạn văn
function groups(block: string): Group[] {
  const result: Group[] = []
  for (const line of block.split('\n')) {
    const list = BULLET.test(line)
    const content = inline(list ? line.replace(BULLET, '') : line)
    const last = result.at(-1)
    if (last && last.list === list) last.lines.push(content)
    else result.push({ list, lines: [content] })
  }
  return result
}

function inline(line: string): Part[] {
  return line
    .split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g)
    .filter((part) => part.length > 0)
    .map((part) => {
      if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return { style: 'b', text: part.slice(2, -2) }
      if (part.startsWith('*') && part.endsWith('*') && part.length > 2) return { style: 'i', text: part.slice(1, -1) }
      return { style: null, text: part }
    })
}

