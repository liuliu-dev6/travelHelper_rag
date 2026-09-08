import type { GraphData, GraphEdge, GraphNode } from '@/types/chat'

/**
 * 历史记录可能来自旧版本，其中 graph 会被保存成空对象。
 * 在进入 Vue 组件树之前统一校验，避免一条坏消息阻断整个路由卸载。
 */
export const normalizeGraphData = (value: unknown): GraphData | undefined => {
  if (!value || typeof value !== 'object') return undefined
  const candidate = value as Partial<GraphData>
  if (!Array.isArray(candidate.nodes) || candidate.nodes.length === 0) return undefined
  return {
    center: typeof candidate.center === 'string' ? candidate.center : '',
    nodes: candidate.nodes as GraphNode[],
    edges: Array.isArray(candidate.edges) ? candidate.edges as GraphEdge[] : []
  }
}
