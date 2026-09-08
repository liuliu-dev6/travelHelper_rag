<template>
  <div class="chat-bubble" :class="messageClass">
    <div class="bubble-content">
      <div class="message-text" v-if="message.role === 'user'">{{ message.content }}</div>
      <div class="message-text ai-message" v-else>
        <template v-if="message.content">{{ message.content }}</template>
      </div>
      <template v-if="message.role === 'assistant'">
        <AgentStepTimeline :steps="message.steps || []" />
        <SourceCardList :sources="message.sources || []" @select="$emit('source-select', $event)" />
        <KnowledgeGraphCard v-if="message.graph" :graph="message.graph" @select="$emit('node-select', $event)" />
        <div v-if="message.stats" class="answer-stats">
          基于 {{ message.stats.sourcesCount || 0 }} 条本地知识 · {{ formatDuration(message.stats.durationMs) }}
        </div>
      </template>
    </div>
    <div class="message-time" v-if="showTime">{{ formatTime }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AgentStepTimeline from '@/components/AgentStepTimeline.vue'
import SourceCardList from '@/components/SourceCardList.vue'
import KnowledgeGraphCard from '@/components/KnowledgeGraphCard.vue'
import type { ChatMessage, GraphNode, SourceItem } from '@/types/chat'

const props = defineProps<{ message: ChatMessage }>()
defineEmits<{
  'source-select': [source: SourceItem]
  'node-select': [node: GraphNode]
}>()

const messageClass = computed(() => {
  return props.message.role === 'user' ? 'user-message' : 'ai-message'
})

const showTime = computed(() => {
  return props.message.timestamp && props.message.content
})

const formatTime = computed(() => {
  if (!props.message.timestamp) return ''
  const date = new Date(props.message.timestamp)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
})

const formatDuration = (duration?: number) => {
  if (duration == null) return ''
  return duration < 1000 ? `${duration}ms` : `${(duration / 1000).toFixed(1)}s`
}
</script>

<style scoped>
.chat-bubble {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.user-message {
  align-self: flex-end;
  align-items: flex-end;
}

.ai-message {
  align-self: flex-start;
  align-items: flex-start;
}

.bubble-content {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.5;
  word-break: break-word;
}

.ai-message .bubble-content {
  width: min(100%, 560px);
  box-sizing: border-box;
}

.user-message .bubble-content {
  background: #1989fa;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-message .bubble-content {
  background: #f5f5f5;
  color: #323233;
  border-bottom-left-radius: 4px;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  padding: 0 4px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 8px;
}

.answer-stats {
  margin-top: 8px;
  color: #9aa5af;
  font-size: 10px;
  text-align: left;
}
</style>
