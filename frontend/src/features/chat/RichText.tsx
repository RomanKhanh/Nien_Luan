import { Fragment, useMemo, type ReactNode } from 'react'
import { parseRichText, richTextLength, type Group, type Part } from './richTextParse'

// Hiển thị câu trả lời của bot với markdown tối giản: **đậm**, *nghiêng*, dòng "- " thành gạch đầu dòng,
// dòng trống tách đoạn. Dựng thành phần tử React (không dùng innerHTML) nên nội dung AI trả về không chèn được HTML.
// visibleChars: chỉ hiện bấy nhiêu ký tự đầu (đếm trên chữ hiển thị, không tính dấu markdown) kèm con trỏ ở cuối,
// dùng cho hiệu ứng gõ chữ; chữ đậm / gạch đầu dòng hiện đúng định dạng ngay khi đang gõ, không lộ dấu "**".
export function RichText({ text, visibleChars }: { text: string; visibleChars?: number }) {
  const groups = useMemo(() => parseRichText(text), [text])
  const typing = visibleChars !== undefined && visibleChars < richTextLength(groups)
  // số ký tự còn được hiện, trừ dần khi dựng từng đoạn theo thứ tự
  const budget: Budget = { left: visibleChars ?? Infinity, caret: typing }
  const nodes: ReactNode[] = []
  groups.forEach((g, i) => {
    if (budget.left > 0) nodes.push(renderGroup(g, String(i), budget))
  })
  return <div className="space-y-2">{nodes.length > 0 ? nodes : typing && <Caret />}</div>
}

interface Budget {
  left: number
  caret: boolean
}

function renderGroup(g: Group, key: string, budget: Budget) {
  // dòng chưa tới lượt gõ thì chưa hiện (không để gạch đầu dòng trống chờ sẵn)
  const lines: ReactNode[][] = []
  for (const line of g.lines) {
    if (budget.left <= 0) break
    lines.push(renderParts(line, budget))
  }
  if (g.list) {
    return (
      <ul key={key} className="space-y-1">
        {lines.map((nodes, j) => (
          <li key={j} className="flex gap-2">
            <span className="mt-[0.55em] h-1.5 w-1.5 shrink-0 rounded-full bg-sky" aria-hidden />
            <span>{nodes}</span>
          </li>
        ))}
      </ul>
    )
  }
  return (
    <p key={key}>
      {lines.map((nodes, j) => (
        <Fragment key={j}>
          {j > 0 && <br />}
          {nodes}
        </Fragment>
      ))}
    </p>
  )
}

function renderParts(parts: Part[], budget: Budget): ReactNode[] {
  const nodes: ReactNode[] = []
  for (const [i, part] of parts.entries()) {
    if (budget.left <= 0) break
    const shown = part.text.slice(0, budget.left)
    budget.left -= shown.length
    if (part.style === 'b') {
      nodes.push(
        <b key={i} className="font-bold text-ink">
          {shown}
        </b>,
      )
    } else if (part.style === 'i') nodes.push(<i key={i}>{shown}</i>)
    else nodes.push(<Fragment key={i}>{shown}</Fragment>)
  }
  if (budget.left <= 0 && budget.caret) {
    nodes.push(<Caret key="caret" />)
    budget.caret = false
  }
  return nodes
}

function Caret() {
  return (
    <span
      className="ml-0.5 inline-block h-[1.05em] w-[2px] translate-y-[0.18em] animate-caret rounded-full bg-primary"
      aria-hidden
    />
  )
}
