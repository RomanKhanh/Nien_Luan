import { Fragment, type ReactNode } from 'react'

// Hiển thị câu trả lời của bot với markdown tối giản: **đậm**, *nghiêng*, dòng "- " thành gạch đầu dòng,
// dòng trống tách đoạn. Dựng thành phần tử React (không dùng innerHTML) nên nội dung AI trả về không chèn được HTML.
export function RichText({ text }: { text: string }) {
  const blocks = text.trim().split(/\n\s*\n/)
  return (
    <div className="space-y-2">
      {blocks.flatMap((block, i) => groups(block).map((g, j) => renderGroup(g, `${i}-${j}`)))}
    </div>
  )
}

const BULLET = /^\s*[-•]\s+/

interface Group {
  list: boolean
  lines: string[]
}

// trong một đoạn: các dòng gạch đầu dòng liền nhau gom thành danh sách, các dòng khác thành đoạn văn
function groups(block: string): Group[] {
  const result: Group[] = []
  for (const line of block.split('\n')) {
    const list = BULLET.test(line)
    const content = list ? line.replace(BULLET, '') : line
    const last = result.at(-1)
    if (last && last.list === list) last.lines.push(content)
    else result.push({ list, lines: [content] })
  }
  return result
}

function renderGroup(g: Group, key: string) {
  if (g.list) {
    return (
      <ul key={key} className="space-y-1">
        {g.lines.map((l, j) => (
          <li key={j} className="flex gap-2">
            <span className="mt-[0.55em] h-1.5 w-1.5 shrink-0 rounded-full bg-sky" aria-hidden />
            <span>{inline(l)}</span>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <p key={key}>
      {g.lines.map((l, j) => (
        <Fragment key={j}>
          {j > 0 && <br />}
          {inline(l)}
        </Fragment>
      ))}
    </p>
  )
}

function inline(line: string): ReactNode[] {
  return line.split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g).map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return (
        <b key={i} className="font-bold text-ink">
          {part.slice(2, -2)}
        </b>
      )
    }
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) return <i key={i}>{part.slice(1, -1)}</i>
    return part
  })
}
