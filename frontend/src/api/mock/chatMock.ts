// BẢN GIẢ LẬP chatbot + cơ sở tri thức, dùng tới khi backend có API thật (xem api/chat.ts, docs/chatbot-api.md).
// - Hội thoại, tài liệu, cấu hình lưu localStorage của trình duyệt này (không lên server, người khác không thấy).
// - Câu trả lời do luật đơn giản sinh ra, nhưng sản phẩm gợi ý lấy từ API catalog / hồ sơ bé THẬT,
//   nên luồng giao diện (gợi ý, lý do, thêm vào giỏ) chạy giống bản thật.
// Xoá được cả file này khi đã nối backend.
import type { AdminChatApi, ChatApi } from '../chat'
import { ApiError } from '../client'
import { catalogApi, childApi, meApi } from '../endpoints'
import type {
  AdminChatSessionDetail,
  ChatbotConfig,
  ChatMessage,
  ChatSource,
  ChatSuggestedProduct,
  DocumentChunk,
  KnowledgeDocument,
  Page,
  ProductSummary,
  SkillImpact,
} from '../types'
import { tokenStorage } from '@/auth/tokenStorage'

const STORE_KEY = 'brainblocks.chatMock.v1'

interface MockSession {
  id: number
  customerId: number
  customerName: string
  customerEmail: string
  childProfileId: number | null
  childName: string | null
  title: string | null
  startedAt: string
  messages: ChatMessage[]
}

interface MockDocument extends Omit<KnowledgeDocument, 'chunkCount' | 'status'> {
  status: KnowledgeDocument['status']
  // tài liệu đang "xử lý" tới thời điểm này thì coi như đã index xong
  processingUntil: number | null
  chunks: { content: string }[]
}

interface Store {
  seq: number
  sessions: MockSession[]
  documents: MockDocument[]
  configs: ChatbotConfig[]
}

// ---------- tiện ích ----------

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
const latency = () => sleep(250 + Math.random() * 250)

// backend trả LocalDateTime không kèm múi giờ -> giả lập đúng định dạng đó
function localIso(date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}
const daysAgo = (d: number, h = 10) => {
  const date = new Date()
  date.setDate(date.getDate() - d)
  date.setHours(h, (d * 7) % 60, 0, 0)
  return localIso(date)
}

// bỏ dấu tiếng Việt để so khớp từ khoá
const plain = (s: string) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')

function page<T>(items: T[], pageNo = 0, size = 10): Page<T> {
  return {
    content: items.slice(pageNo * size, pageNo * size + size),
    page: pageNo,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
}

function chunkText(text: string): { content: string }[] {
  const paragraphs = text
    .split(/\n\s*\n/)
    .map((p) => p.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
  const chunks: string[] = []
  let current = ''
  for (const p of paragraphs) {
    if ((current + ' ' + p).length > 420 && current) {
      chunks.push(current)
      current = p
    } else current = current ? `${current} ${p}` : p
  }
  if (current) chunks.push(current)
  return chunks.map((content) => ({ content }))
}

// ---------- dữ liệu mẫu ----------

const SAMPLE_DOCS: [string, string, string][] = [
  [
    'Giáo dục STEM cho trẻ mầm non và tiểu học',
    'PDF',
    `STEM là cách tiếp cận liên môn giữa Khoa học, Công nghệ, Kỹ thuật và Toán học. Với trẻ 3–6 tuổi, trọng tâm là khám phá bằng giác quan: quan sát, phân loại, so sánh và đặt câu hỏi "vì sao".

Từ 7–10 tuổi, trẻ bắt đầu làm được các thí nghiệm có nhiều bước, ghi lại kết quả và rút ra kết luận đơn giản. Đồ chơi lắp ráp, mạch điện và bộ thí nghiệm giúp trẻ nối kiến thức với thực tế.

Trẻ 11–12 tuổi có thể tiếp cận lập trình khối lệnh, robot và các dự án thiết kế nhỏ, rèn luyện tư duy hệ thống và kỹ năng làm việc nhóm.`,
  ],
  [
    'Phương pháp phát triển tư duy logic qua trò chơi',
    'DOCX',
    `Tư duy logic phát triển tốt nhất khi trẻ được giải các bài toán có quy luật rõ ràng: xếp hình theo mẫu, mê cung, cờ và trò chơi suy luận.

Phụ huynh nên chọn thử thách vừa sức: quá dễ gây chán, quá khó gây nản. Các bộ trò chơi có nhiều cấp độ giúp trẻ tiến bộ dần và tự tin hơn.

Khuyến khích trẻ nói ra cách mình suy nghĩ ("con làm thế nào để tìm ra?") giúp củng cố tư duy và khả năng diễn đạt.`,
  ],
  [
    'Hướng dẫn hoạt động sáng tạo với đồ chơi mở',
    'PDF',
    `Đồ chơi mở (không có một đáp án đúng duy nhất) như khối nam châm, tranh cát, đất nặn hay bộ lắp ráp tự do giúp trẻ phát triển khả năng sáng tạo và tưởng tượng.

Người lớn nên hạn chế làm mẫu, thay vào đó đặt câu hỏi gợi mở: "Con muốn xây gì hôm nay?", "Nếu thêm bánh xe thì sao?".

Kết hợp đồ chơi sáng tạo với đồ chơi logic giúp trẻ phát triển cân bằng giữa tư duy hội tụ và tư duy phân kỳ.`,
  ],
  [
    'Rèn kỹ năng giải quyết vấn đề cho trẻ 6–10 tuổi',
    'PDF',
    `Kỹ năng giải quyết vấn đề gồm các bước: nhận ra vấn đề, thử nhiều cách, đánh giá kết quả và điều chỉnh. Đồ chơi cơ khí và thử thách lắp ráp tạo nhiều cơ hội thử – sai an toàn.

Khi trẻ gặp khó, hãy chia nhỏ vấn đề thay vì giải hộ. Mỗi lần tự vượt qua một thử thách nhỏ giúp trẻ kiên nhẫn và bền bỉ hơn.`,
  ],
  [
    'Hướng dẫn sử dụng & an toàn: bộ mạch điện Snap',
    'TXT',
    `Bộ mạch điện dùng pin AA, điện áp thấp, an toàn cho trẻ từ 8 tuổi khi có người lớn hướng dẫn trong những lần đầu.

Không nối trực tiếp hai cực pin với nhau. Tháo pin khi không sử dụng trong thời gian dài. Bắt đầu từ các mạch đơn giản (đèn, còi) trước khi làm mạch có công tắc và cảm biến.`,
  ],
]

function seed(): Store {
  let seq = 1
  const next = () => seq++
  const documents: MockDocument[] = SAMPLE_DOCS.map(([title, fileType, text], i) => ({
    id: next(),
    title,
    fileType,
    fileUrl: `mock://tai-lieu-${i + 1}.${fileType.toLowerCase()}`,
    status: i === 4 ? 'DISABLED' : 'INDEXED',
    uploadedAt: daysAgo(20 - i * 3, 9),
    uploadedByName: 'Quản trị viên',
    processingUntil: null,
    chunks: chunkText(text),
  }))
  const configs: ChatbotConfig[] = [
    {
      id: next(),
      modelName: 'claude-sonnet-5-5',
      temperature: 0.3,
      maxTokens: 1024,
      topK: 4,
      systemPrompt:
        'Bạn là Bin, trợ lý tư vấn đồ chơi STEM của BrainBlocks. Trả lời bằng tiếng Việt, thân thiện, ngắn gọn. ' +
        'Chỉ gợi ý sản phẩm có trong dữ liệu được cung cấp và luôn giải thích lý do. ' +
        'Không đánh giá hay chẩn đoán năng lực, tâm lý của trẻ.',
      active: true,
      updatedAt: daysAgo(10),
      adminName: 'Quản trị viên',
    },
  ]
  const bot = (content: string, topic: string, at: string, products: ChatSuggestedProduct[] = []): ChatMessage => ({
    id: next(),
    sender: 'BOT',
    content,
    topic,
    createdAt: at,
    responseTimeMs: 1200 + Math.round(Math.random() * 900),
    suggestedProducts: products,
    sources: [],
  })
  const user = (content: string, topic: string, at: string): ChatMessage => ({
    id: next(),
    sender: 'USER',
    content,
    topic,
    createdAt: at,
    responseTimeMs: 0,
    suggestedProducts: [],
    sources: [],
  })
  // vài hội thoại mẫu của khách khác để trang giám sát của admin có dữ liệu
  const samples: [string, string, string | null, [string, string, string][]][] = [
    [
      'Trần Thu Hà',
      'thuha@example.com',
      'Na',
      [
        [
          'Bé 7 tuổi đã có bộ lắp ráp thì nên mua thêm gì để phát triển sáng tạo?',
          'Gợi ý theo kỹ năng',
          'Với bé 7 tuổi đã có bộ lắp ráp, nên bổ sung **đồ chơi mở** như khối nam châm hoặc tranh cát để bé tự do sáng tạo.',
        ],
      ],
    ],
    [
      'Lê Minh Quân',
      'quan.le@example.com',
      null,
      [
        [
          'Robot lập trình phù hợp với độ tuổi nào?',
          'Độ tuổi phù hợp',
          'Robot lập trình khối lệnh phù hợp nhất với bé **từ 7 tuổi**, khi bé đã đọc được hướng dẫn đơn giản.',
        ],
        [
          'Có an toàn không?',
          'Lợi ích sản phẩm',
          'Sản phẩm dùng pin điện áp thấp, an toàn khi có người lớn hướng dẫn trong những lần đầu.',
        ],
      ],
    ],
    [
      'Phạm Ngọc Lan',
      'lan.pham@example.com',
      'Su',
      [
        [
          'Phân tích giúp tôi các đồ chơi bé đã có',
          'Phân tích hồ sơ kỹ năng',
          'Hồ sơ của bé Su đang nghiêng về **Tư duy Logic**, nhóm **Sáng tạo** còn ít được khai thác.',
        ],
      ],
    ],
  ]
  const sessions: MockSession[] = samples.map(([name, email, child, turns], i) => {
    const messages: ChatMessage[] = []
    turns.forEach(([q, topic, a], t) => {
      messages.push(user(q, topic, daysAgo(i + 1, 9 + t)))
      messages.push(bot(a, topic, daysAgo(i + 1, 9 + t)))
    })
    return {
      id: next(),
      customerId: 1000 + i,
      customerName: name,
      customerEmail: email,
      childProfileId: null,
      childName: child,
      title: turns[0][0],
      startedAt: daysAgo(i + 1, 9),
      messages,
    }
  })
  return { seq, sessions, documents, configs }
}

let cache: Store | null = null

function load(): Store {
  if (cache) return cache
  try {
    const raw = localStorage.getItem(STORE_KEY)
    cache = raw ? (JSON.parse(raw) as Store) : seed()
  } catch {
    cache = seed()
  }
  return cache
}

function save() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify(load()))
  } catch {
    /* chế độ riêng tư chặn storage: dữ liệu chỉ sống tới khi tải lại trang */
  }
}

const nextId = () => {
  const s = load()
  return s.seq++
}

// ---------- người dùng hiện tại ----------

function currentCustomerId(): number {
  const auth = tokenStorage.get()
  if (!auth || auth.role !== 'CUSTOMER') throw new ApiError(401, 'Unauthorized')
  return auth.userId
}

async function currentCustomer() {
  const id = currentCustomerId()
  try {
    const me = await meApi.get()
    return { id, name: me.fullName, email: me.email }
  } catch {
    return { id, name: 'Phụ huynh', email: '' }
  }
}

function ownSession(id: number): MockSession {
  const s = load().sessions.find((x) => x.id === id && x.customerId === currentCustomerId())
  if (!s) throw new ApiError(404, 'Chat session not found')
  return s
}

const summary = (s: MockSession) => ({
  id: s.id,
  title: s.title,
  startedAt: s.startedAt,
  lastMessageAt: s.messages.at(-1)?.createdAt ?? null,
  messageCount: s.messages.length,
  childProfileId: s.childProfileId,
  childName: s.childName,
})

const adminSummary = (s: MockSession) => ({
  ...summary(s),
  customerId: s.customerId,
  customerName: s.customerName,
  customerEmail: s.customerEmail,
})

// ---------- "bộ não" giả lập ----------

const SKILL_WORDS: [string, RegExp][] = [
  ['LOGIC', /logic|tu duy|suy luan|toan/],
  ['CREATIVE', /sang tao|tuong tuong|nghe thuat|ve|thiet ke/],
  ['PROBLEM_SOLVING', /giai quyet|van de|thu thach|me cung/],
  ['STEM', /stem|khoa hoc|thi nghiem|robot|lap trinh|mach dien|ky thuat/],
]

const SKILL_NAMES: Record<string, string> = {
  LOGIC: 'Tư duy Logic',
  CREATIVE: 'Sáng tạo',
  PROBLEM_SOLVING: 'Giải quyết vấn đề',
  STEM: 'STEM',
}

function detectSkill(q: string): string | null {
  return SKILL_WORDS.find(([, re]) => re.test(q))?.[0] ?? null
}

function impactOf(impacts: SkillImpact[], code: string | null) {
  return code ? (impacts.find((i) => i.skillCode === code)?.impactIndex ?? 0) : 0
}

function toSuggestion(p: ProductSummary, reason: string): ChatSuggestedProduct {
  return {
    productId: p.id,
    name: p.name,
    price: p.price,
    minAge: p.minAge,
    maxAge: p.maxAge,
    thumbnailUrl: p.thumbnailUrl,
    skillImpacts: p.skillImpacts,
    reason,
  }
}

function pickSources(q: string, skill: string | null): ChatSource[] {
  const docs = load().documents.filter((d) => docStatus(d) === 'INDEXED')
  const wanted = plain(`${q} ${skill ? SKILL_NAMES[skill] : ''}`)
  const scored = docs
    .map((d) => {
      const words = plain(d.title)
        .split(/\W+/)
        .filter((w) => w.length > 3)
      return { d, score: words.filter((w) => wanted.includes(w)).length }
    })
    .sort((a, b) => b.score - a.score)
  return scored.slice(0, scored[0]?.score ? 2 : 1).map(({ d }) => ({
    documentId: d.id,
    documentTitle: d.title,
    snippet: d.chunks[0]?.content.slice(0, 180) + '…',
  }))
}

interface BotAnswer {
  content: string
  topic: string
  products: ChatSuggestedProduct[]
  sources: ChatSource[]
}

async function answer(question: string, session: MockSession): Promise<BotAnswer> {
  const q = plain(question)
  let skill = detectSkill(q)
  const ageMatch = q.match(/(\d{1,2})\s*tuoi/)
  let age = ageMatch ? Number(ageMatch[1]) : null

  // ngữ cảnh hồ sơ bé đang chọn: tuổi, nhóm yếu nhất, đồ chơi đã có
  let childName: string | null = null
  let weakest: string | null = null
  let strongest: string | null = null
  let owned = new Set<number>()
  let totalToys = 0
  if (session.childProfileId) {
    try {
      const [child, profile, toys] = await Promise.all([
        childApi.get(session.childProfileId),
        childApi.skillProfile(session.childProfileId),
        childApi.products(session.childProfileId),
      ])
      childName = child.name
      age ??= child.age
      weakest = profile.weakestSkill?.skillCode ?? null
      strongest = profile.strongestSkill?.skillCode ?? null
      totalToys = profile.totalProducts
      owned = new Set(toys.map((t) => t.productId))
      if (!skill && profile.totalProducts === 0 && child.interestedSkills[0]) skill = child.interestedSkills[0].code
    } catch {
      /* hồ sơ bé không đọc được: tư vấn chung */
    }
  }

  // 1) chào hỏi
  if (/^(xin )?chao|^hello|^hi\b/.test(q) && q.length < 30) {
    return {
      content:
        'Chào phụ huynh! Mình là **Bin** 🤖 — trợ lý tư vấn đồ chơi STEM của BrainBlocks.\n\n' +
        'Mình có thể:\n- Gợi ý đồ chơi theo **độ tuổi** và **nhóm kỹ năng**\n- Giải thích **lợi ích giáo dục** của từng sản phẩm\n- Phân tích **hồ sơ kỹ năng** của bé và đề xuất món tiếp theo\n\nPhụ huynh muốn bắt đầu từ đâu ạ?',
      topic: 'Chào hỏi',
      products: [],
      sources: [],
    }
  }

  // 2) hỏi về một sản phẩm cụ thể (nút "Hỏi Bin" ở trang sản phẩm gửi tên trong ngoặc kép)
  const quoted = question.match(/[“"]([^”"]{3,})[”"]/)?.[1]
  if (quoted) {
    const found = (await catalogApi.products({ keyword: quoted, size: 1 }).catch(() => null))?.content[0]
    if (found) {
      const top = [...found.skillImpacts].sort((a, b) => b.impactIndex - a.impactIndex)
      const lines = top.map((s) => `- **${s.skillName}**: ${s.impactIndex}/10`).join('\n')
      const main = top[0]
      return {
        content:
          `**${found.name}** phù hợp với bé **${found.minAge}–${found.maxAge} tuổi**. Chỉ số tác động lên từng nhóm kỹ năng:\n${lines}\n\n` +
          (main
            ? `Sản phẩm tác động mạnh nhất đến **${main.skillName}**: bé được thực hành ${main.skillCode === 'CREATIVE' ? 'tự thiết kế và thể hiện ý tưởng riêng' : main.skillCode === 'LOGIC' ? 'suy luận, nhận biết quy luật' : main.skillCode === 'STEM' ? 'khám phá khoa học – kỹ thuật qua thực hành' : 'thử – sai và tìm cách vượt qua thử thách'}.`
            : ''),
        topic: 'Lợi ích sản phẩm',
        products: [toSuggestion(found, `Sản phẩm phụ huynh đang hỏi · phù hợp ${found.minAge}–${found.maxAge} tuổi`)],
        sources: pickSources(question, main?.skillCode ?? null),
      }
    }
  }

  // 3) phân tích hồ sơ kỹ năng
  if (/phan tich|ho so|da co|da mua|dang thieu/.test(q) && !/nen mua|goi y|tiep theo/.test(q)) {
    if (!childName) {
      return {
        content:
          'Để phân tích, phụ huynh hãy **chọn hồ sơ bé** ở phía trên khung chat nhé. Mình sẽ dựa vào các đồ chơi bé đã có để xem nhóm kỹ năng nào đang được chú trọng và nhóm nào chưa khai thác nhiều.',
        topic: 'Phân tích hồ sơ kỹ năng',
        products: [],
        sources: [],
      }
    }
    if (totalToys === 0) {
      return {
        content: `Hồ sơ của bé **${childName}** chưa có đồ chơi nào nên mình chưa phân tích được. Phụ huynh có thể thêm đồ chơi bé đã có trong trang **Hồ sơ bé**, hoặc hỏi mình gợi ý món đầu tiên nhé!`,
        topic: 'Phân tích hồ sơ kỹ năng',
        products: [],
        sources: [],
      }
    }
    return {
      content:
        `Từ **${totalToys} món đồ chơi** của bé **${childName}**:\n` +
        (strongest ? `- Nhóm đang được chú trọng: **${SKILL_NAMES[strongest] ?? strongest}**\n` : '') +
        (weakest ? `- Nhóm chưa khai thác nhiều: **${SKILL_NAMES[weakest] ?? weakest}**\n` : '') +
        `\nPhụ huynh có thể hỏi *"Nên mua gì tiếp theo?"* để mình gợi ý món bổ sung cho nhóm còn thiếu. Lưu ý: đây chỉ là tham khảo khi chọn đồ chơi, không phải đánh giá năng lực của bé.`,
      topic: 'Phân tích hồ sơ kỹ năng',
      products: [],
      sources: pickSources(question, weakest),
    }
  }

  // 4) gợi ý sản phẩm (mặc định)
  const wantsNext = /tiep theo|bo sung|them gi|nen mua|mua gi|mo rong/.test(q)
  if (!skill && wantsNext && weakest) skill = weakest
  const isRecommend = Boolean(skill || age || wantsNext || /goi y|tu van|phu hop|nen chon|do choi/.test(q))

  if (isRecommend) {
    const res = await catalogApi
      .products({
        age: age ?? undefined,
        skills: skill ? [skill] : undefined,
        sort: 'relevance',
        inStock: true,
        size: 8,
      })
      .catch(() => null)
    const picks = (res?.content ?? [])
      .filter((p) => !owned.has(p.id))
      .sort((a, b) => impactOf(b.skillImpacts, skill) - impactOf(a.skillImpacts, skill))
      .slice(0, 3)
    // câu hỏi tự nêu tuổi (có thể khác bé đang chọn) thì nói theo tuổi trong câu hỏi
    const who =
      childName && !ageMatch ? `bé **${childName}**${age ? ` (${age} tuổi)` : ''}` : age ? `bé **${age} tuổi**` : 'bé'
    const skillText = skill ? ` để phát triển **${SKILL_NAMES[skill] ?? skill}**` : ''
    if (picks.length === 0) {
      return {
        content: `Hiện mình chưa tìm thấy sản phẩm còn hàng phù hợp cho ${who}${skillText}. Phụ huynh thử nới độ tuổi hoặc chọn nhóm kỹ năng khác nhé.`,
        topic: skill ? 'Gợi ý theo kỹ năng' : 'Độ tuổi phù hợp',
        products: [],
        sources: pickSources(question, skill),
      }
    }
    const products = picks.map((p) => {
      const parts: string[] = []
      if (skill) parts.push(`Tác động ${impactOf(p.skillImpacts, skill)}/10 vào ${SKILL_NAMES[skill] ?? skill}`)
      if (age) parts.push(`phù hợp độ tuổi ${p.minAge}–${p.maxAge}`)
      if (skill && skill === weakest) parts.push('bổ sung nhóm bé đang ít được khai thác')
      return toSuggestion(p, parts.join(' · ') || `Được phụ huynh quan tâm nhiều · ${p.minAge}–${p.maxAge} tuổi`)
    })
    return {
      content:
        `Với ${who}${skillText}, mình gợi ý ${products.length} món dưới đây.` +
        (skill && skill === weakest && childName
          ? ` Đây là nhóm kỹ năng hồ sơ của bé đang **ít được khai thác**, nên bổ sung sẽ giúp lộ trình cân bằng hơn.`
          : '') +
        `\n\nMỗi gợi ý đều kèm **lý do** — phụ huynh bấm vào sản phẩm để xem chi tiết chỉ số kỹ năng nhé.`,
      topic: skill ? 'Gợi ý theo kỹ năng' : 'Độ tuổi phù hợp',
      products,
      sources: pickSources(question, skill),
    }
  }

  // 5) câu hỏi chung về giáo dục STEM: trả lời từ tài liệu
  const sources = pickSources(question, null)
  return {
    content:
      'Theo tài liệu trong cơ sở tri thức của BrainBlocks:\n\n' +
      (sources[0]?.snippet ?? 'Đồ chơi STEM giúp trẻ học qua trải nghiệm thực tế.') +
      '\n\nPhụ huynh có thể cho mình biết **tuổi của bé** và **nhóm kỹ năng quan tâm** để mình gợi ý sản phẩm cụ thể.',
    topic: 'Kiến thức giáo dục STEM',
    products: [],
    sources,
  }
}

// ---------- API khách hàng ----------

const DEFAULT_SUGGESTIONS = [
  'Bé 7 tuổi đã có bộ lắp ráp thì nên mua thêm gì để phát triển sáng tạo?',
  'Gợi ý đồ chơi phát triển tư duy logic cho bé 5 tuổi',
  'Đồ chơi STEM giúp trẻ phát triển những gì?',
  'Robot lập trình phù hợp với độ tuổi nào?',
]

export const chatMock: ChatApi = {
  async sessions() {
    await latency()
    const id = currentCustomerId()
    return load()
      .sessions.filter((s) => s.customerId === id)
      .map(summary)
      .sort((a, b) => (b.lastMessageAt ?? b.startedAt).localeCompare(a.lastMessageAt ?? a.startedAt))
  },
  async session(id) {
    await latency()
    const s = ownSession(id)
    return { ...summary(s), messages: s.messages }
  },
  async createSession({ childProfileId }) {
    await latency()
    const me = await currentCustomer()
    const childName = childProfileId ? ((await childApi.get(childProfileId).catch(() => null))?.name ?? null) : null
    const s: MockSession = {
      id: nextId(),
      customerId: me.id,
      customerName: me.name,
      customerEmail: me.email,
      childProfileId,
      childName,
      title: null,
      startedAt: localIso(),
      messages: [],
    }
    load().sessions.push(s)
    save()
    return { ...summary(s), messages: [] }
  },
  async updateSession(id, { childProfileId }) {
    await latency()
    const s = ownSession(id)
    s.childProfileId = childProfileId
    s.childName = childProfileId ? ((await childApi.get(childProfileId).catch(() => null))?.name ?? null) : null
    save()
    return summary(s)
  },
  async deleteSession(id) {
    await latency()
    ownSession(id)
    const store = load()
    store.sessions = store.sessions.filter((s) => s.id !== id)
    save()
  },
  async sendMessage(sessionId, content) {
    const s = ownSession(sessionId)
    const text = content.trim()
    if (!text) throw new ApiError(400, 'Message must not be empty')
    const started = Date.now()
    const result = await answer(text, s)
    // AI thật mất vài giây: giả lập độ trễ để thấy hiệu ứng "đang soạn"
    await sleep(Math.max(0, 900 + Math.random() * 700 - (Date.now() - started)))
    const userMessage: ChatMessage = {
      id: nextId(),
      sender: 'USER',
      content: text,
      topic: result.topic,
      createdAt: localIso(new Date(started)),
      responseTimeMs: 0,
      suggestedProducts: [],
      sources: [],
    }
    const botMessage: ChatMessage = {
      id: nextId(),
      sender: 'BOT',
      content: result.content,
      topic: result.topic,
      createdAt: localIso(),
      responseTimeMs: Date.now() - started,
      suggestedProducts: result.products,
      sources: result.sources,
    }
    s.messages.push(userMessage, botMessage)
    s.title ??= text.length > 60 ? `${text.slice(0, 57)}…` : text
    save()
    return { userMessage, botMessage }
  },
  async suggestions(childProfileId) {
    await latency()
    if (!childProfileId) return DEFAULT_SUGGESTIONS
    const child = await childApi.get(childProfileId).catch(() => null)
    if (!child) return DEFAULT_SUGGESTIONS
    return [
      `Phân tích các đồ chơi bé ${child.name} đã có`,
      `Bé ${child.name} nên mua gì tiếp theo?`,
      `Gợi ý đồ chơi sáng tạo cho bé ${child.age} tuổi`,
      'Đồ chơi STEM giúp trẻ phát triển những gì?',
    ]
  },
}

// ---------- API quản trị ----------

function docStatus(d: MockDocument): KnowledgeDocument['status'] {
  if (d.status === 'PROCESSING' && d.processingUntil && Date.now() >= d.processingUntil) {
    d.status = 'INDEXED'
    d.processingUntil = null
    save()
  }
  return d.status
}

const docView = (d: MockDocument): KnowledgeDocument => ({
  id: d.id,
  title: d.title,
  fileUrl: d.fileUrl,
  fileType: d.fileType,
  status: docStatus(d),
  uploadedAt: d.uploadedAt,
  uploadedByName: d.uploadedByName,
  chunkCount: d.chunks.length,
})

function findDoc(id: number): MockDocument {
  const d = load().documents.find((x) => x.id === id)
  if (!d) throw new ApiError(404, 'Document not found')
  return d
}

const TEXT_TYPES = ['TXT', 'MD', 'CSV']

export const adminChatMock: AdminChatApi = {
  async stats() {
    await latency()
    const sessions = load().sessions
    const questions = sessions.flatMap((s) => s.messages.filter((m) => m.sender === 'USER'))
    const answers = sessions.flatMap((s) => s.messages.filter((m) => m.sender === 'BOT'))
    const count = <T>(items: T[], key: (t: T) => string | null) => {
      const map = new Map<string, number>()
      items.forEach((i) => {
        const k = key(i)
        if (k) map.set(k, (map.get(k) ?? 0) + 1)
      })
      return [...map.entries()].sort((a, b) => b[1] - a[1])
    }
    const weekAgo = localIso(new Date(Date.now() - 7 * 864e5))
    const products = new Map<number, { productName: string; total: number }>()
    answers.forEach((m) =>
      m.suggestedProducts.forEach((p) => {
        const cur = products.get(p.productId) ?? { productName: p.name, total: 0 }
        cur.total++
        products.set(p.productId, cur)
      }),
    )
    const daily = Array.from({ length: 14 }, (_, i) => {
      const date = localIso(new Date(Date.now() - (13 - i) * 864e5)).slice(0, 10)
      return { date, total: questions.filter((q) => q.createdAt.startsWith(date)).length }
    })
    return {
      totalSessions: sessions.length,
      totalQuestions: questions.length,
      questionsLast7Days: questions.filter((q) => q.createdAt >= weekAgo).length,
      avgResponseTimeMs: answers.length
        ? Math.round(answers.reduce((s, m) => s + m.responseTimeMs, 0) / answers.length)
        : 0,
      topTopics: count(questions, (q) => q.topic)
        .slice(0, 6)
        .map(([topic, total]) => ({ topic, total })),
      topQuestions: count(questions, (q) => q.content.trim())
        .slice(0, 6)
        .map(([question, total]) => ({ question, total })),
      topSuggestedProducts: [...products.entries()]
        .sort((a, b) => b[1].total - a[1].total)
        .slice(0, 5)
        .map(([productId, v]) => ({ productId, ...v })),
      dailyQuestions: daily,
    }
  },
  async sessions({ keyword, page: p = 0, size = 10 }) {
    await latency()
    const k = keyword ? plain(keyword) : ''
    const list = load()
      .sessions.filter(
        (s) =>
          !k ||
          plain(`${s.customerName} ${s.customerEmail} ${s.title ?? ''} ${s.childName ?? ''}`).includes(k) ||
          s.messages.some((m) => plain(m.content).includes(k)),
      )
      .map(adminSummary)
      .sort((a, b) => (b.lastMessageAt ?? b.startedAt).localeCompare(a.lastMessageAt ?? a.startedAt))
    return page(list, p, size)
  },
  async session(id): Promise<AdminChatSessionDetail> {
    await latency()
    const s = load().sessions.find((x) => x.id === id)
    if (!s) throw new ApiError(404, 'Chat session not found')
    return { ...adminSummary(s), messages: s.messages }
  },

  async documents({ keyword, status, page: p = 0, size = 10 }) {
    await latency()
    const k = keyword ? plain(keyword) : ''
    const list = load()
      .documents.map(docView)
      .filter((d) => (!status || d.status === status) && (!k || plain(d.title).includes(k)))
      .sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
    return page(list, p, size)
  },
  async uploadDocument({ title, file }) {
    await sleep(600)
    const ext = (file.name.split('.').pop() ?? '').toUpperCase()
    const text = TEXT_TYPES.includes(ext)
      ? await file.text()
      : `Nội dung của "${file.name}" sẽ được backend trích xuất (${ext}) rồi tách đoạn và tạo embedding.\n\n` +
        '(Bản giả lập không đọc được tệp nhị phân nên chỉ tạo đoạn mẫu.)'
    const d: MockDocument = {
      id: nextId(),
      title: title.trim(),
      fileType: ext || 'FILE',
      fileUrl: `mock://${file.name}`,
      status: 'PROCESSING',
      uploadedAt: localIso(),
      uploadedByName: 'Quản trị viên',
      processingUntil: Date.now() + 5000,
      chunks: chunkText(text).slice(0, 50),
    }
    load().documents.push(d)
    save()
    return docView(d)
  },
  async updateDocument(id, { title }) {
    await latency()
    const d = findDoc(id)
    d.title = title.trim()
    save()
    return docView(d)
  },
  async setDocumentStatus(id, status) {
    await latency()
    const d = findDoc(id)
    if (docStatus(d) === 'PROCESSING') throw new ApiError(409, 'Document is still being processed')
    d.status = status
    save()
    return docView(d)
  },
  async reindexDocument(id) {
    await latency()
    const d = findDoc(id)
    d.status = 'PROCESSING'
    d.processingUntil = Date.now() + 4000
    save()
    return docView(d)
  },
  async deleteDocument(id) {
    await latency()
    findDoc(id)
    const store = load()
    store.documents = store.documents.filter((d) => d.id !== id)
    save()
  },
  async documentChunks(id): Promise<DocumentChunk[]> {
    await latency()
    const d = findDoc(id)
    const embedded = docStatus(d) === 'INDEXED' || d.status === 'DISABLED'
    return d.chunks.map((c, i) => ({ id: d.id * 1000 + i, chunkIndex: i, content: c.content, embedded }))
  },

  async configs() {
    await latency()
    return [...load().configs].sort((a, b) => Number(b.active) - Number(a.active) || b.id - a.id)
  },
  async createConfig(body) {
    await latency()
    const c: ChatbotConfig = { ...body, id: nextId(), active: false, updatedAt: localIso(), adminName: 'Quản trị viên' }
    load().configs.push(c)
    save()
    return c
  },
  async updateConfig(id, body) {
    await latency()
    const c = load().configs.find((x) => x.id === id)
    if (!c) throw new ApiError(404, 'Config not found')
    Object.assign(c, body, { updatedAt: localIso() })
    save()
    return c
  },
  async activateConfig(id) {
    await latency()
    const configs = load().configs
    const c = configs.find((x) => x.id === id)
    if (!c) throw new ApiError(404, 'Config not found')
    // chỉ một cấu hình active tại một thời điểm (giống ràng buộc ở entity ChatbotConfig)
    configs.forEach((x) => (x.active = x.id === id))
    c.updatedAt = localIso()
    save()
    return c
  },
}
