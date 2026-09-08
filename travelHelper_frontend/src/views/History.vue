<template>
  <div class="history-page">
    <van-nav-bar title="对话历史" left-arrow @click-left="router.back()" />
    <van-loading v-if="loading" class="center" size="30" vertical>加载中...</van-loading>
    <van-empty v-else-if="items.length === 0" description="还没有对话会话">
      <van-button round type="primary" @click="router.push('/chat')">开始咨询</van-button>
    </van-empty>
    <div v-else class="history-list">
      <article v-for="item in items" :key="item.id" class="history-card">
        <div class="card-head" @click="toggle(item.id)">
          <div><h3>{{ item.title }}</h3><time>{{ item.messageCount }} 条消息 · {{ formatDate(item.updatedAt) }}</time></div>
          <van-icon :name="expandedId === item.id ? 'arrow-up' : 'arrow-down'" />
        </div>
        <div v-if="expandedId === item.id" class="history-detail">
          <ChatBubble v-for="message in details[item.id] || []" :key="message.id" :message="message" />
          <div class="history-actions">
            <van-button
              size="small"
              type="primary"
              plain
              :loading="openingId === item.id"
              :disabled="openingId !== null"
              @click.stop="openConversation(item.id)"
            >继续对话</van-button>
            <van-button size="small" plain type="danger" icon="delete-o" @click="remove(item)">删除会话</van-button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import ChatBubble from '@/components/ChatBubble.vue'
import type { ChatMessage } from '@/types/chat'
import { normalizeGraphData } from '@/utils/chatData'
import {
  deleteConversation,
  getConversation,
  getConversations,
  type ConversationSummary,
  type StoredConversationMessage
} from '@/api/conversations'

const router = useRouter()
const loading = ref(true)
const items = ref<ConversationSummary[]>([])
const details = ref<Record<string, ChatMessage[]>>({})
const expandedId = ref<string | null>(null)
const openingId = ref<string | null>(null)
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

const toMessage = (stored: StoredConversationMessage): ChatMessage => {
  let detail: Partial<ChatMessage> = {}
  if (stored.detailJson) {
    try { detail = JSON.parse(stored.detailJson) as Partial<ChatMessage> }
    catch { /* 使用纯文本 */ }
  }
  return { id: String(stored.id), role: stored.role, content: stored.content,
    timestamp: stored.createdAt, sources: detail.sources,
    graph: normalizeGraphData(detail.graph), stats: detail.stats }
}

const toggle = async (id: string) => {
  if (expandedId.value === id) {
    expandedId.value = null
    return
  }
  expandedId.value = id
  if (!details.value[id]) {
    try { details.value[id] = (await getConversation(id)).data.messages.map(toMessage) }
    catch (error) { showToast(error instanceof Error ? error.message : '加载会话失败') }
  }
}

const load = async () => {
  try { items.value = (await getConversations()).data }
  catch (error) { showToast(error instanceof Error ? error.message : '加载失败') }
  finally { loading.value = false }
}

const remove = async (item: ConversationSummary) => {
  try {
    await showConfirmDialog({ title: '删除会话', message: '会话中的全部消息都会删除，确定继续吗？' })
    await deleteConversation(item.id)
    items.value = items.value.filter(value => value.id !== item.id)
    if (expandedId.value === item.id) expandedId.value = null
    showToast('已删除')
  } catch (error) {
    if (error instanceof Error) showToast(error.message)
  }
}

const openConversation = async (id: string) => {
  if (openingId.value) return
  openingId.value = id
  try {
    await router.push({ name: 'Chat', query: { conversation: id } })
  } catch (error) {
    showToast(error instanceof Error ? error.message : '无法打开该会话')
  } finally {
    openingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.history-page { min-height: 100vh; background: #f7f8fa; text-align: left; }
.center { padding-top: 80px; text-align: center; }.history-list { padding: 14px; }
.history-card { margin-bottom: 12px; border-radius: 12px; background: #fff; box-shadow: 0 2px 10px rgba(0,0,0,.04); overflow: hidden; }
.card-head { display: flex; align-items: center; justify-content: space-between; padding: 16px; cursor: pointer; }
.card-head h3 { max-width: 290px; margin: 0 0 6px; overflow: hidden; color: #323233; font-size: 16px; white-space: nowrap; text-overflow: ellipsis; }
.card-head time { color: #969799; font-size: 12px; }.history-detail { display: flex; flex-direction: column; gap: 14px; padding: 16px; border-top: 1px solid #f2f3f5; }
.history-actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
