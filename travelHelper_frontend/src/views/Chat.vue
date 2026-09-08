<template>
    <div class="page-container chat-page">
        <div class="page-header" style="height: 46px;">
            <van-nav-bar
                title="AI旅游助手"
                left-text="返回"
                right-text="会话"
                fixed
                left-arrow
                @click-left="onClickLeft"
                @click-right="showSessions = true"
                />
        </div>

        <div class="session-toolbar">
          <span class="session-title">{{ currentConversationTitle }}</span>
          <van-button size="mini" plain type="primary" icon="plus" @click="createNewConversation">新增会话</van-button>
        </div>

        <div class="chat-container" ref="chatContainer">
            <div class="chat-empty" v-if="messages.length === 0">
                <van-empty description="开始和AI助手对话吧！" />
                <div class="quick-questions">
                    <div class="quick-title">常见问题</div>
                    <van-tag class="quick-tag" v-for="question in questions" :key="question" size="large" @click="handleQuickQuestion(question)">{{ question }}</van-tag>
                </div> 
            </div>

            <div class="message-list" v-else>
                <ChatBubble
                  v-for="message in messages"
                  :key="message.id"
                  :message="message"
                  @source-select="askAboutSource"
                  @node-select="askAboutNode"
                />
                <div class="streaming-indicator" v-if="isStreaming">
                  <van-loading type="spinner" size="20px" />
                  <span>{{ streamingLabel }}</span>
                </div>
            </div>
        </div>

        <van-popup v-model:show="showSessions" position="left" class="session-drawer">
          <div class="drawer-head">
            <strong>对话会话</strong>
            <van-button size="small" type="primary" icon="plus" @click="createNewConversation">新增</van-button>
          </div>
          <van-empty v-if="visibleConversations.length === 0" description="暂无会话" />
          <div v-else class="session-list">
            <div
              v-for="conversation in visibleConversations"
              :key="conversation.id"
              class="session-item"
              :class="{ active: conversation.id === currentConversationId }"
              @click="switchConversation(conversation.id)"
            >
              <div class="session-copy">
                <span>{{ conversation.title }}</span>
                <small>{{ conversation.messageCount }} 条消息 · {{ formatSessionTime(conversation.updatedAt) }}</small>
              </div>
              <van-icon name="delete-o" @click.stop="removeConversation(conversation.id)" />
            </div>
          </div>
        </van-popup>

        <div class="chat-input-area">
        <van-field
            placeholder="输入您的问题"
            v-model="inputMessage"
            @keyup.enter="sendMessage"
        >
        <template #button>
            <van-button v-if="!isStreaming" size="small" type="primary" @click="sendMessage" :disabled="!inputMessage.trim()">
              发送
            </van-button>
            <van-button v-else size="small" type="danger" plain @click="stopGeneration">
              停止
            </van-button>
        </template>
        </van-field>
        </div>
    
    </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { fetchStream } from '@/utils/request'
import { normalizeGraphData } from '@/utils/chatData'
import { showConfirmDialog, showToast } from 'vant'
import ChatBubble from '@/components/ChatBubble.vue'
import type { ChatMessage, GraphNode, SourceItem, SseEvent, ToolCallStep } from '@/types/chat'
import {
  createConversation,
  deleteConversation,
  getConversation,
  getConversations,
  type ConversationSummary,
  type StoredConversationMessage
} from '@/api/conversations'

const router = useRouter()
const route = useRoute()
const inputMessage = ref('')
const isStreaming = ref(false)
const questions=ref([
  '北京有哪些必去的景点？',
  '上海美食推荐',
  '成都三日游攻略',
  '如何选择旅行保险？'
])
const chatContainer = ref<HTMLElement | null>(null)
const messages = ref<ChatMessage[]>([])
const conversations = ref<ConversationSummary[]>([])
const currentConversationId = ref<string | null>(null)
const showSessions = ref(false)
const initialized = ref(false)
let messageIdCounter = 0
let abortStream: (() => void) | null = null
const generateId = () => `${Date.now()}-${++messageIdCounter}`
const currentConversationTitle = computed(() =>
  conversations.value.find(item => item.id === currentConversationId.value)?.title || '新对话'
)
const visibleConversations = computed(() =>
  conversations.value.filter(item => item.messageCount > 0)
)

const streamingLabel = computed(() => {
  const assistant = [...messages.value].reverse().find(message => message.role === 'assistant')
  const runningStep = assistant?.steps?.find(step => step.status === 'running')
  return ({
    intent_analysis: '正在理解你的旅行需求...',
    search_attractions: '正在检索本地旅行知识...',
    query_knowledge_graph: '正在扩展景点与美食关联...',
    get_weather_forecast: '正在查询目的地天气...',
    get_air_quality: '正在查询目的地空气质量...',
    realtime_lookup: '正在查询实时旅行信息...',
    generate: '正在生成回答...'
  } as Record<string, string>)[runningStep?.name || ''] || 'AI 正在思考...'
})

const onClickLeft =()=> {
    router.back()
  }

//消息置底
const scrollToBottom = () => {
  void nextTick(() => {
    if (chatContainer.value) {
        chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  })
}

//处理常见问题点击
const handleQuickQuestion = (question: string) => {
    inputMessage.value = question
    sendMessage()
}

// 发送消息
const sendMessage = async () => {
  const msg = inputMessage.value.trim()
    if (!msg||isStreaming.value) {
        return
    }
    if (!currentConversationId.value) await createNewConversation()
    if (!currentConversationId.value) return
    //创建用户会话消息
    addUserMessage(msg)
    inputMessage.value = ''
    //获取流式响应（使用已 trim 的 msg）
    fetchAIResponse(msg)

  }

  const addUserMessage = (msg: string) => {
    messages.value.push({
        id: generateId(),
        role:'user',
        content:msg,
        timestamp:new Date().toISOString()
    })
  }

// 获取流式响应
const fetchAIResponse = (userMessage: string) => {
    isStreaming.value = true
    // 必须持有 Vue Proxy；若继续修改 push 前的原始对象，SSE 数据虽已收到但视图不会更新。
    const assistantMessage = reactive<ChatMessage>({
        id: generateId(),
        role:'assistant',
        content:'',
        timestamp:new Date().toISOString(),
        steps: [],
        streaming: true
    })
    messages.value.push(assistantMessage)

    abortStream = fetchStream('chat', {
      message: userMessage,
      conversationId: currentConversationId.value
    }, {
      onEvent: (event) => handleSseEvent(assistantMessage, event, userMessage),
      onError: (errorMessage) => {
        assistantMessage.content ||= `抱歉，本次回答未能完成：${errorMessage}`
        assistantMessage.streaming = false
        isStreaming.value = false
        abortStream = null
        showToast('发生错误，请稍后重试')
        scrollToBottom()
      }
    })
    scrollToBottom()
}

const handleSseEvent = (message: ChatMessage, event: SseEvent, userMessage: string) => {
  switch (event.type) {
    case 'tool_call':
      updateStep(message, event)
      break
    case 'sources':
      message.sources = event.items
      break
    case 'graph':
      message.graph = { center: event.center, nodes: event.nodes, edges: event.edges }
      break
    case 'chunk':
      message.content += event.content
      break
    case 'done':
      message.stats = event.stats
      message.streaming = false
      isStreaming.value = false
      abortStream = null
      void refreshConversationList()
      break
    case 'error':
      break
    case 'meta':
      if (event.conversationId) currentConversationId.value = event.conversationId
      break
  }
  scrollToBottom()
}

const updateStep = (message: ChatMessage, event: Extract<SseEvent, { type: 'tool_call' }>) => {
  const steps = message.steps || (message.steps = [])
  const existing = steps.find(step => step.id === event.id)
  const status: ToolCallStep['status'] = event.status === 'start' ? 'running' : event.status === 'end' ? 'done' : 'error'
  if (existing) {
    existing.status = status
    existing.summary = event.summary
    existing.durationMs = event.durationMs
  } else {
    steps.push({ id: event.id, name: event.name, status, summary: event.summary, durationMs: event.durationMs })
  }
}

const stopGeneration = () => {
  abortStream?.()
  abortStream = null
  isStreaming.value = false
  const last = messages.value.at(-1)
  if (last?.role === 'assistant') last.streaming = false
}

const askAboutSource = (source: SourceItem) => {
  if (isStreaming.value) return
  inputMessage.value = `请详细介绍${source.title}，并给出游玩建议`
  sendMessage()
}

const askAboutNode = (node: GraphNode) => {
  if (isStreaming.value) return
  inputMessage.value = node.type === 'food'
    ? `请介绍${node.label}，在哪里比较值得体验？`
    : `请结合${node.label}继续推荐相关行程`
  sendMessage()
}

const fromStoredMessage = (stored: StoredConversationMessage): ChatMessage => {
  let detail: Partial<ChatMessage> = {}
  if (stored.detailJson) {
    try { detail = JSON.parse(stored.detailJson) as Partial<ChatMessage> }
    catch { /* 保留纯文本消息 */ }
  }
  return {
    id: String(stored.id),
    role: stored.role,
    content: stored.content,
    timestamp: stored.createdAt,
    sources: detail.sources,
    graph: normalizeGraphData(detail.graph),
    steps: detail.steps,
    stats: detail.stats
  }
}

const refreshConversationList = async () => {
  try { conversations.value = (await getConversations()).data }
  catch (error) { showToast(error instanceof Error ? error.message : '会话列表加载失败') }
}

const createNewConversation = async () => {
  if (isStreaming.value) return showToast('请先停止当前回答')
  try {
    const created = (await createConversation()).data
    conversations.value = [created, ...conversations.value.filter(item => item.id !== created.id)]
    currentConversationId.value = created.id
    messages.value = []
    showSessions.value = false
    await router.replace({ path: '/chat', query: { conversation: created.id } })
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建会话失败')
  }
}

const switchConversation = async (id: string) => {
  if (id === currentConversationId.value) {
    showSessions.value = false
    return
  }
  if (isStreaming.value) return showToast('请先停止当前回答')
  try {
    const detail = (await getConversation(id)).data
    currentConversationId.value = id
    messages.value = detail.messages.map(fromStoredMessage)
    showSessions.value = false
    await router.replace({ path: '/chat', query: { conversation: id } })
    scrollToBottom()
  } catch (error) {
    showToast(error instanceof Error ? error.message : '会话加载失败')
  }
}

const removeConversation = async (id: string) => {
  if (isStreaming.value && id === currentConversationId.value) return showToast('请先停止当前回答')
  try {
    await showConfirmDialog({ title: '删除会话', message: '会话内的全部消息都会删除，确定继续吗？' })
    await deleteConversation(id)
    conversations.value = conversations.value.filter(item => item.id !== id)
    if (currentConversationId.value === id) {
      const next = conversations.value[0]
      if (next) await switchConversation(next.id)
      else await createNewConversation()
    }
  } catch (error) {
    if (error instanceof Error) showToast(error.message)
  }
}

const formatSessionTime = (value: string) => new Date(value).toLocaleString('zh-CN', {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
})

const initializeConversations = async () => {
  await refreshConversationList()
  const requested = typeof route.query.conversation === 'string' ? route.query.conversation : null
  const target = requested && conversations.value.some(item => item.id === requested)
    ? requested
    : conversations.value[0]?.id
  if (target) await switchConversation(target)
  else await createNewConversation()
  initialized.value = true
  if (typeof route.query.prompt === 'string') inputMessage.value = route.query.prompt
}

onMounted(initializeConversations)

// 从历史页或浏览器前进/后退进入同一路由时，Vue 可能复用组件；
// 此时不能只依赖 onMounted，必须按 URL 中的 conversation 主动切换。
watch(() => route.query.conversation, async value => {
  if (!initialized.value || typeof value !== 'string' || value === currentConversationId.value) return
  await switchConversation(value)
})
watch(() => route.query.prompt, value => {
  if (typeof value === 'string') inputMessage.value = value
})
</script>

<style lang="css" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding-bottom: 50px;
}

.chat-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  padding-bottom: 60px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.quick-questions {
  margin-top: 32px;
  text-align: center;
}

.quick-title {
  font-size: 14px;
  color: #999;
  margin-bottom: 16px;
}

.quick-tag {
  margin: 8px;
  cursor: pointer;
  border-radius: 0 18px 18px 0;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.session-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 16px; border-bottom: 1px solid #f1f2f5; background: #fff; }
.session-title { min-width: 0; overflow: hidden; color: #4b5563; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.session-drawer { width: min(82vw, 340px); height: 100%; padding: 18px 12px; background: #f7f8fa; }
.drawer-head { display: flex; align-items: center; justify-content: space-between; padding: 8px 4px 16px; }
.session-list { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; }
.session-item { display: flex; align-items: center; gap: 10px; padding: 12px; border-radius: 10px; background: #fff; color: #4b5563; cursor: pointer; }
.session-item.active { background: #eaf4ff; color: #1989fa; }
.session-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.session-copy span { overflow: hidden; font-size: 14px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.session-copy small { color: #969799; font-size: 11px; }

.message-list :deep(.chat-bubble.ai-message) {
  max-width: 94%;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  color: #999;
  font-size: 14px;
}

.chat-input-area {
  position: fixed;
  bottom: 50px;
  left: 0;
  right: 0;
  background: #fff;
  padding: 8px 16px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  max-width: 750px;
  margin: 0 auto;
}

.chat-input-area :deep(.van-field) {
  background: #f7f8fa;
  border-radius: 20px;
  padding: 8px 16px;
}
</style>
