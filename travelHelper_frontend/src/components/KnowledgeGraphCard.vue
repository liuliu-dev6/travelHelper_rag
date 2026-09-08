<template>
  <div v-if="nodes.length" class="graph-card">
    <button class="graph-header" @click="expanded = !expanded">
      <span><van-icon name="share-o" /> 知识关联 · {{ relatedNodes.length }}</span>
      <van-icon :name="expanded ? 'arrow-up' : 'arrow-down'" />
    </button>
    <div v-if="expanded" class="graph-content">
      <div class="center-node">{{ centerNode?.label || '推荐地点' }}</div>
      <div class="relations">
        <button v-for="node in relatedNodes" :key="node.id" class="relation" :class="node.type" @click="$emit('select', node)">
          <span class="relation-label">{{ relationLabel(node.id) }}</span>
          <span class="node-name">{{ node.label }}</span>
          <span class="node-type">{{ typeLabel(node.type) }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { GraphData, GraphNode } from '@/types/chat'

const props = defineProps<{ graph?: Partial<GraphData> | null }>()
defineEmits<{ select: [node: GraphNode] }>()
const expanded = ref(false)
const nodes = computed<GraphNode[]>(() => Array.isArray(props.graph?.nodes) ? props.graph.nodes : [])
const edges = computed(() => Array.isArray(props.graph?.edges) ? props.graph.edges : [])
const centerNode = computed(() => nodes.value.find(node => node.id === props.graph?.center))
const relatedNodes = computed(() => nodes.value.filter(node => node.id !== props.graph?.center))
const relationLabel = (nodeId: string) => {
  const relation = edges.value.find(edge => edge.target === nodeId || edge.source === nodeId)?.label
  return ({ NEARBY: '附近', LOCATED_IN: '位于', HAS_THEME: '主题' } as Record<string, string>)[relation || ''] || '关联'
}
const typeLabel = (type: string) => ({ city: '城市', food: '美食', theme: '偏好', poi: '景点' } as Record<string, string>)[type] || type
</script>

<style scoped>
.graph-card { margin-top: 9px; overflow: hidden; border: 1px solid #e7ecf2; border-radius: 12px; background: #fff; }
.graph-header { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 10px 12px; border: 0; background: linear-gradient(90deg, #f5f9ff, #faf7ff); color: #52677b; font-size: 12px; }
.graph-header span { display: flex; align-items: center; gap: 5px; }
.graph-content { padding: 12px; }
.center-node { display: inline-block; padding: 6px 12px; border: 2px solid #1989fa; border-radius: 14px; background: #eaf4ff; color: #1769aa; font-size: 12px; font-weight: 600; }
.relations { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-top: 12px; }
.relation { position: relative; display: flex; min-width: 0; flex-direction: column; gap: 2px; padding: 8px; border: 1px solid #e8edf2; border-radius: 9px; background: #fafcfe; text-align: left; }
.relation.food { border-color: #d9f1e5; background: #f4fcf8; }
.relation.theme { border-color: #eee4f8; background: #fbf7ff; }
.relation-label { color: #9aa6b2; font-size: 9px; }
.node-name { overflow: hidden; color: #405264; font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.node-type { color: #95a1ad; font-size: 9px; }
</style>
