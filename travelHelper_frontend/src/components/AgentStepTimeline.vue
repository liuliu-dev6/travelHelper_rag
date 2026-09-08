<template>
  <div v-if="steps.length" class="agent-steps">
    <div class="steps-title">
      <van-icon name="cluster-o" />
      <span>智能检索过程</span>
    </div>
    <div v-for="step in steps" :key="step.id" class="step-row" :class="step.status">
      <span class="step-dot">
        <van-loading v-if="step.status === 'running'" size="12" />
        <van-icon v-else-if="step.status === 'done'" name="success" />
        <van-icon v-else name="warning-o" />
      </span>
      <div class="step-main">
        <span class="step-name">{{ displayName(step.name) }}</span>
        <span v-if="step.summary" class="step-summary">{{ step.summary }}</span>
      </div>
      <span v-if="step.durationMs != null" class="step-time">{{ formatDuration(step.durationMs) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ToolCallStep } from '@/types/chat'

defineProps<{ steps: ToolCallStep[] }>()

const names: Record<string, string> = {
  intent_analysis: '理解旅行需求',
  search_attractions: '搜索本地知识库',
  query_knowledge_graph: '扩展关联知识',
  get_weather_forecast: '查询实时天气',
  get_air_quality: '查询空气质量',
  realtime_lookup: '查询实时信息',
  generate: '生成回答'
}

const displayName = (name: string) => names[name] || name
const formatDuration = (ms: number) => ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
</script>

<style scoped>
.agent-steps { margin-bottom: 10px; padding: 10px 12px; border: 1px solid #e8eef5; border-radius: 12px; background: #fbfdff; }
.steps-title { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; color: #4b6b8a; font-size: 12px; font-weight: 600; }
.step-row { display: flex; align-items: center; gap: 8px; min-height: 25px; color: #607080; font-size: 12px; }
.step-dot { width: 14px; color: #1989fa; display: inline-flex; justify-content: center; }
.step-row.error .step-dot { color: #ee7a34; }
.step-main { min-width: 0; flex: 1; display: flex; gap: 6px; text-align: left; }
.step-name { flex-shrink: 0; font-weight: 500; color: #34495e; }
.step-summary { overflow: hidden; color: #8a96a3; text-overflow: ellipsis; white-space: nowrap; }
.step-time { flex-shrink: 0; color: #aab2bd; font-size: 10px; }
</style>
