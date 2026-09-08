export interface SourceItem {
  id: string
  type: 'poi' | 'food' | string
  title: string
  city?: string
  tags?: string[]
  score?: number
  snippet?: string
  payload?: {
    ticket?: string
    rating?: number
  }
}

export interface GraphNode {
  id: string
  label: string
  type: 'city' | 'poi' | 'food' | 'theme' | string
  props?: Record<string, unknown>
}

export interface GraphEdge {
  id?: string
  source: string
  target: string
  label: string
}

export interface GraphData {
  center: string
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface ToolCallStep {
  id: string
  name: string
  status: 'running' | 'done' | 'error'
  summary?: string
  durationMs?: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  sources?: SourceItem[]
  graph?: GraphData
  steps?: ToolCallStep[]
  streaming?: boolean
  stats?: { durationMs?: number; sourcesCount?: number }
}

export interface ConversationTurn {
  role: 'user' | 'assistant'
  content: string
}

export type SseEvent =
  | { type: 'meta'; sessionId?: string; conversationId?: string }
  | { type: 'tool_call'; id: string; name: string; status: 'start' | 'end' | 'error'; summary?: string; durationMs?: number }
  | { type: 'sources'; items: SourceItem[] }
  | ({ type: 'graph' } & GraphData)
  | { type: 'chunk'; content: string }
  | { type: 'done'; done: boolean; stats?: { durationMs?: number; sourcesCount?: number } }
  | { type: 'error'; error: string }

export interface StreamHandlers {
  onEvent?: (event: SseEvent) => void
  onError?: (message: string) => void
}
