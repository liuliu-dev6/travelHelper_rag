import request, { type ApiResult } from '@/utils/request'

export interface KnowledgeDocument {
  id: string
  title: string
  sourceType: 'FILE' | 'URL'
  sourceUri: string
  sourceId?: string
  mimeType?: string
  city?: string
  knowledgeType?: string
  status: 'PROCESSING' | 'REVIEW_REQUIRED' | 'REJECTED' | 'PARSE_REVIEW' | 'INDEXED' | 'FAILED'
  characterCount: number
  chunkCount: number
  errorMessage?: string
  reviewedBy?: string
  reviewedAt?: string
  createdAt: string
  indexedAt?: string
}

export type KnowledgeRefreshPolicy = 'EVERY_2_HOURS' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'MANUAL'

export interface KnowledgeSource {
  id: string
  code: string
  name: string
  url: string
  city: string
  entityName?: string
  knowledgeType: string
  crawlKeywords?: string
  maxLinkedPages: number
  refreshPolicy: KnowledgeRefreshPolicy
  enabled: boolean
  priority: number
  status: 'PENDING' | 'SYNCING' | 'ACTIVE' | 'UNCHANGED' | 'FAILED' | 'DISABLED'
  lastCheckedAt?: string
  lastSuccessAt?: string
  nextRefreshAt?: string
  lastError?: string
  currentDocumentId?: string
}

export interface KnowledgeRefreshResult {
  outcome: 'UPDATED' | 'NOT_MODIFIED' | 'CHECKSUM_UNCHANGED'
  changed: boolean
  source: KnowledgeSource
}

export interface GraphCandidateEntity {
  id: string
  type: 'POI' | 'CITY' | 'FOOD' | 'THEME' | 'AREA' | 'EXHIBITION' | 'STATION'
  name: string
  city?: string
  confidence: number
  status: 'PENDING' | 'CONFLICT' | 'PUBLISHING' | 'PUBLISHED' | 'REJECTED' | 'FAILED'
  issues?: string
  alignedEntityId?: string
}

export interface GraphRelationCandidate {
  id: string
  documentId: string
  relationType: 'LOCATED_IN' | 'HAS_THEME' | 'NEARBY' | 'HAS_FOOD' | 'SUITABLE_FOR' | 'HAS_EXHIBITION'
  confidence: number
  evidence: string
  status: 'PENDING' | 'CONFLICT' | 'PUBLISHING' | 'PUBLISHED' | 'REJECTED' | 'FAILED'
  issues?: string
  source: GraphCandidateEntity
  target: GraphCandidateEntity
  createdAt: string
}

export const listKnowledgeDocuments = () =>
  request.get<any, ApiResult<KnowledgeDocument[]>>('/api/knowledge/documents')

export const uploadKnowledgeDocuments = (files: File[], city: string, knowledgeType: string) => {
  const form = new FormData()
  files.forEach(file => form.append('files', file))
  if (city.trim()) form.append('city', city.trim())
  form.append('knowledgeType', knowledgeType)
  return request.post<any, ApiResult<KnowledgeDocument[]>>('/api/knowledge/documents/upload', form,
    { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600000 })
}

export const ingestKnowledgeUrl = (url: string, title: string, city: string, knowledgeType: string) =>
  request.post<any, ApiResult<KnowledgeDocument>>('/api/knowledge/documents/url',
    { url: url.trim(), title: title.trim() || null, city: city.trim() || null, knowledgeType },
    { timeout: 600000 })

export const deleteKnowledgeDocument = (id: string) =>
  request.delete<any, ApiResult<void>>(`/api/knowledge/documents/${id}`)

export const listKnowledgeSources = () =>
  request.get<any, ApiResult<KnowledgeSource[]>>('/api/knowledge/sources')

export const updateKnowledgeSource = (id: string, enabled: boolean, refreshPolicy: KnowledgeRefreshPolicy) =>
  request.put<any, ApiResult<KnowledgeSource>>(`/api/knowledge/sources/${id}`, { enabled, refreshPolicy })

export const refreshKnowledgeSource = (id: string) =>
  request.post<any, ApiResult<KnowledgeRefreshResult>>(`/api/knowledge/sources/${id}/refresh`, {},
    { timeout: 600000 })

export const listReviewDocuments = () =>
  request.get<any, ApiResult<KnowledgeDocument[]>>('/api/knowledge/reviews/documents')

export const approveReviewDocument = (id: string) =>
  request.post<any, ApiResult<KnowledgeDocument>>(`/api/knowledge/reviews/documents/${id}/approve`, {},
    { timeout: 600000 })

export const rejectReviewDocument = (id: string, reason = '') =>
  request.post<any, ApiResult<KnowledgeDocument>>(`/api/knowledge/reviews/documents/${id}/reject`, { reason })

export const listGraphRelationCandidates = () =>
  request.get<any, ApiResult<GraphRelationCandidate[]>>('/api/knowledge/reviews/relations')

export const approveGraphRelation = (id: string) =>
  request.post<any, ApiResult<GraphRelationCandidate>>(`/api/knowledge/reviews/relations/${id}/approve`, {})

export const rejectGraphRelation = (id: string, reason = '') =>
  request.post<any, ApiResult<void>>(`/api/knowledge/reviews/relations/${id}/reject`, { reason })
