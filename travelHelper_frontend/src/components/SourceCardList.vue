<template>
  <div v-if="sources.length" class="sources-wrap">
    <div class="section-title"><van-icon name="records-o" /> 推荐依据 · {{ sources.length }}</div>
    <div class="source-scroll">
      <button v-for="(source, index) in sources" :key="source.id" class="source-card" @click="$emit('select', source)">
        <div class="source-head">
          <span class="source-index">{{ index + 1 }}</span>
          <span class="source-title">{{ source.title }}</span>
          <span v-if="source.score != null" class="source-score">{{ scoreText(source.score) }}</span>
        </div>
        <div class="source-meta">
          <span v-if="source.city">{{ source.city }}</span>
          <span v-if="source.payload?.ticket">{{ source.payload.ticket }}</span>
          <span v-if="source.payload?.rating">★ {{ source.payload.rating }}</span>
        </div>
        <p v-if="source.snippet" class="source-snippet">{{ source.snippet }}</p>
        <div v-if="source.tags?.length" class="source-tags">
          <span v-for="tag in source.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SourceItem } from '@/types/chat'

defineProps<{ sources: SourceItem[] }>()
defineEmits<{ select: [source: SourceItem] }>()

const scoreText = (score: number) => `${Math.round(score * 100)}% 匹配`
</script>

<style scoped>
.sources-wrap { margin-top: 10px; width: 100%; }
.section-title { display: flex; align-items: center; gap: 5px; margin: 0 2px 7px; color: #6d7d8c; font-size: 12px; }
.source-scroll { display: flex; gap: 8px; overflow-x: auto; padding: 1px 1px 5px; scrollbar-width: none; }
.source-scroll::-webkit-scrollbar { display: none; }
.source-card { min-width: 230px; max-width: 260px; padding: 10px; border: 1px solid #e4ebf2; border-radius: 12px; background: #fff; text-align: left; box-shadow: 0 3px 10px rgba(42, 73, 104, .05); }
.source-head { display: flex; align-items: center; gap: 6px; }
.source-index { width: 18px; height: 18px; border-radius: 6px; background: #e8f3ff; color: #1989fa; font-size: 11px; line-height: 18px; text-align: center; }
.source-title { min-width: 0; flex: 1; overflow: hidden; color: #263849; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.source-score { color: #07a06a; font-size: 10px; }
.source-meta { display: flex; gap: 8px; margin-top: 7px; color: #788795; font-size: 11px; }
.source-snippet { display: -webkit-box; overflow: hidden; margin: 7px 0 0; color: #657585; font-size: 11px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.source-tags { display: flex; gap: 5px; margin-top: 7px; }
.source-tags span { padding: 2px 6px; border-radius: 8px; background: #f2f6fa; color: #718096; font-size: 10px; }
</style>
