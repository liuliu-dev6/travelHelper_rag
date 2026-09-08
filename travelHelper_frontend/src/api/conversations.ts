import request, { type ApiResult } from '@/utils/request'

export interface ConversationSummary {
  id: string
  title: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface StoredConversationMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  detailJson?: string | null
  sequenceNo: number
  createdAt: string
}

export interface ConversationDetail {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: StoredConversationMessage[]
}

export const createConversation = (title?: string): Promise<ApiResult<ConversationSummary>> =>
  request.post('/api/conversations', title ? { title } : {}) as unknown as Promise<ApiResult<ConversationSummary>>

export const getConversations = (): Promise<ApiResult<ConversationSummary[]>> =>
  request.get('/api/conversations') as unknown as Promise<ApiResult<ConversationSummary[]>>

export const getConversation = (id: string): Promise<ApiResult<ConversationDetail>> =>
  request.get(`/api/conversations/${id}`) as unknown as Promise<ApiResult<ConversationDetail>>

export const deleteConversation = (id: string): Promise<ApiResult<void>> =>
  request.delete(`/api/conversations/${id}`) as unknown as Promise<ApiResult<void>>
