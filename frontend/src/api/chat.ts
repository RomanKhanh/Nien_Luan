import { http, request } from './client'
import { adminChatMock, chatMock } from './mock/chatMock'
import type {
  AdminChatSessionDetail,
  AdminChatSessionSummary,
  ChatbotConfig,
  ChatbotConfigRequest,
  ChatReply,
  ChatSessionDetail,
  ChatSessionSummary,
  ChatStats,
  DocumentChunk,
  DocumentStatus,
  KnowledgeDocument,
  Page,
} from './types'

// Chatbot AI chưa có backend: mặc định dùng bản giả lập (mock/chatMock.ts) chạy ngay trên trình duyệt.
// Khi backend làm xong các endpoint trong docs/chatbot-api.md, đặt VITE_CHAT_MOCK=false trong frontend/.env
// là toàn bộ giao diện chuyển sang gọi API thật, không phải sửa component nào.
export const CHAT_MOCK = import.meta.env.VITE_CHAT_MOCK !== 'false'

export interface ChatApi {
  sessions: () => Promise<ChatSessionSummary[]>
  session: (id: number) => Promise<ChatSessionDetail>
  createSession: (body: { childProfileId: number | null }) => Promise<ChatSessionDetail>
  updateSession: (id: number, body: { childProfileId: number | null }) => Promise<ChatSessionSummary>
  deleteSession: (id: number) => Promise<void>
  sendMessage: (sessionId: number, content: string) => Promise<ChatReply>
  suggestions: (childProfileId: number | null) => Promise<string[]>
}

export interface AdminChatApi {
  stats: () => Promise<ChatStats>
  sessions: (params: { keyword?: string; page?: number; size?: number }) => Promise<Page<AdminChatSessionSummary>>
  session: (id: number) => Promise<AdminChatSessionDetail>

  documents: (params: {
    keyword?: string
    status?: DocumentStatus
    page?: number
    size?: number
  }) => Promise<Page<KnowledgeDocument>>
  uploadDocument: (body: { title: string; file: File }) => Promise<KnowledgeDocument>
  updateDocument: (id: number, body: { title: string }) => Promise<KnowledgeDocument>
  setDocumentStatus: (id: number, status: 'INDEXED' | 'DISABLED') => Promise<KnowledgeDocument>
  reindexDocument: (id: number) => Promise<KnowledgeDocument>
  deleteDocument: (id: number) => Promise<void>
  documentChunks: (id: number) => Promise<DocumentChunk[]>

  configs: () => Promise<ChatbotConfig[]>
  createConfig: (body: ChatbotConfigRequest) => Promise<ChatbotConfig>
  updateConfig: (id: number, body: ChatbotConfigRequest) => Promise<ChatbotConfig>
  activateConfig: (id: number) => Promise<ChatbotConfig>
}

const httpChatApi: ChatApi = {
  sessions: () => request<ChatSessionSummary[]>(http.get('/chat/sessions')),
  session: (id) => request<ChatSessionDetail>(http.get(`/chat/sessions/${id}`)),
  createSession: (body) => request<ChatSessionDetail>(http.post('/chat/sessions', body)),
  updateSession: (id, body) => request<ChatSessionSummary>(http.patch(`/chat/sessions/${id}`, body)),
  deleteSession: (id) => request<void>(http.delete(`/chat/sessions/${id}`)),
  // AI có thể trả lời chậm: nới timeout riêng cho request này
  sendMessage: (sessionId, content) =>
    request<ChatReply>(http.post(`/chat/sessions/${sessionId}/messages`, { content }, { timeout: 60_000 })),
  suggestions: (childProfileId) =>
    request<string[]>(http.get('/chat/suggestions', { params: { childProfileId: childProfileId ?? undefined } })),
}

const httpAdminChatApi: AdminChatApi = {
  stats: () => request<ChatStats>(http.get('/admin/chat/stats')),
  sessions: (params) => request<Page<AdminChatSessionSummary>>(http.get('/admin/chat/sessions', { params })),
  session: (id) => request<AdminChatSessionDetail>(http.get(`/admin/chat/sessions/${id}`)),

  documents: (params) => request<Page<KnowledgeDocument>>(http.get('/admin/knowledge-documents', { params })),
  uploadDocument: ({ title, file }) => {
    const form = new FormData()
    form.append('title', title)
    form.append('file', file)
    // để trình duyệt tự đặt Content-Type multipart kèm boundary
    return request<KnowledgeDocument>(
      http.post('/admin/knowledge-documents', form, { headers: { 'Content-Type': undefined } }),
    )
  },
  updateDocument: (id, body) => request<KnowledgeDocument>(http.put(`/admin/knowledge-documents/${id}`, body)),
  setDocumentStatus: (id, status) =>
    request<KnowledgeDocument>(http.patch(`/admin/knowledge-documents/${id}/status`, { status })),
  reindexDocument: (id) => request<KnowledgeDocument>(http.post(`/admin/knowledge-documents/${id}/reindex`)),
  deleteDocument: (id) => request<void>(http.delete(`/admin/knowledge-documents/${id}`)),
  documentChunks: (id) => request<DocumentChunk[]>(http.get(`/admin/knowledge-documents/${id}/chunks`)),

  configs: () => request<ChatbotConfig[]>(http.get('/admin/chatbot-configs')),
  createConfig: (body) => request<ChatbotConfig>(http.post('/admin/chatbot-configs', body)),
  updateConfig: (id, body) => request<ChatbotConfig>(http.put(`/admin/chatbot-configs/${id}`, body)),
  activateConfig: (id) => request<ChatbotConfig>(http.patch(`/admin/chatbot-configs/${id}/activate`)),
}

export const chatApi: ChatApi = CHAT_MOCK ? chatMock : httpChatApi
export const adminChatApi: AdminChatApi = CHAT_MOCK ? adminChatMock : httpAdminChatApi
