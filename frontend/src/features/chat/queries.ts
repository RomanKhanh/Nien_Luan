import { useQuery } from '@tanstack/react-query'
import { chatApi } from '@/api/chat'
import { useAuth } from '@/auth/AuthContext'

export const chatKeys = {
  sessions: ['chat', 'sessions'] as const,
  session: (id: number) => ['chat', 'session', id] as const,
  suggestions: (childId: number | null) => ['chat', 'suggestions', childId] as const,
}

export const adminChatKeys = {
  stats: ['admin', 'chat', 'stats'] as const,
  sessions: (params: object) => ['admin', 'chat', 'sessions', params] as const,
  session: (id: number) => ['admin', 'chat', 'session', id] as const,
  documents: (params: object) => ['admin', 'knowledge', params] as const,
  chunks: (id: number) => ['admin', 'knowledge', 'chunks', id] as const,
  configs: ['admin', 'chatbot-configs'] as const,
}

// hội thoại thuộc về tài khoản khách hàng (ChatSession.customer)
export function useChatSessions() {
  const { isCustomer } = useAuth()
  return useQuery({ queryKey: chatKeys.sessions, queryFn: chatApi.sessions, enabled: isCustomer })
}

export function useChatSession(id: number | null) {
  return useQuery({
    queryKey: chatKeys.session(id ?? 0),
    queryFn: () => chatApi.session(id!),
    enabled: id !== null,
  })
}

export function useChatSuggestions(childId: number | null, enabled: boolean) {
  return useQuery({
    queryKey: chatKeys.suggestions(childId),
    queryFn: () => chatApi.suggestions(childId),
    enabled,
    staleTime: 5 * 60_000,
  })
}
