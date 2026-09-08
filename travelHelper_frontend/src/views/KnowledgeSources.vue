<template>
  <div class="page">
    <van-nav-bar title="可信网页订阅" left-arrow @click-left="router.back()" />

    <section class="summary">
      <div><b>{{ sources.length }}</b><span>可信来源</span></div>
      <div><b>{{ activeCount }}</b><span>已启用</span></div>
      <div><b>{{ indexedCount }}</b><span>已同步</span></div>
    </section>

    <section class="explain">
      <b>受控增量同步</b>
      <p>只检查配置的官方页面。优先使用 ETag / Last-Modified，正文指纹未变化时不会重新向量化。</p>
    </section>

    <van-loading v-if="loading" vertical class="loading">加载中...</van-loading>
    <van-empty v-else-if="sources.length === 0" description="暂无可信来源" />
    <section v-for="item in sources" v-else :key="item.id" class="source-card">
      <div class="source-head">
        <div class="source-name">
          <b>{{ item.name }}</b>
          <van-tag :type="statusType(item.status)">{{ statusText(item.status) }}</van-tag>
        </div>
        <van-switch :model-value="item.enabled" size="21" :loading="updating === item.id"
          @update:model-value="toggle(item, $event)" />
      </div>
      <p class="meta">{{ item.city }} · {{ typeText(item.knowledgeType) }} · 优先级 {{ item.priority }}</p>
      <p v-if="item.maxLinkedPages" class="crawl">跟进至多 {{ item.maxLinkedPages }} 篇：{{ item.crawlKeywords }}</p>
      <a :href="item.url" target="_blank" rel="noopener noreferrer" class="url">{{ item.url }}</a>
      <div class="time-grid">
        <span>最近成功<br><b>{{ formatTime(item.lastSuccessAt) }}</b></span>
        <span>下次检查<br><b>{{ item.enabled ? formatTime(item.nextRefreshAt) : '已停用' }}</b></span>
      </div>
      <p v-if="item.lastError" class="error">{{ item.lastError }}</p>
      <div class="actions">
        <van-button size="small" plain @click="choosePolicy(item)">{{ policyText(item.refreshPolicy) }}</van-button>
        <van-button size="small" type="primary" :loading="refreshing === item.id"
          :disabled="!item.enabled" @click="refresh(item)">立即增量刷新</van-button>
      </div>
    </section>

    <van-action-sheet v-model:show="showPolicies" title="选择刷新频率" :actions="policyActions"
      cancel-text="取消" @select="selectPolicy" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listKnowledgeSources, refreshKnowledgeSource, updateKnowledgeSource,
  type KnowledgeRefreshPolicy, type KnowledgeSource } from '@/api/knowledgeDocuments'

const router = useRouter()
const sources = ref<KnowledgeSource[]>([])
const loading = ref(false), refreshing = ref(''), updating = ref('')
const showPolicies = ref(false), selected = ref<KnowledgeSource | null>(null)
const policies: Array<{ name:string; value:KnowledgeRefreshPolicy }> = [
  { name:'每2小时', value:'EVERY_2_HOURS' }, { name:'每天', value:'DAILY' },
  { name:'每周', value:'WEEKLY' }, { name:'每月', value:'MONTHLY' },
  { name:'每季度', value:'QUARTERLY' }, { name:'仅手动', value:'MANUAL' }
]
const policyActions = policies.map(item => ({ name:item.name, value:item.value }))
const activeCount = computed(() => sources.value.filter(item => item.enabled).length)
const indexedCount = computed(() => sources.value.filter(item => item.currentDocumentId).length)

const load = async () => {
  loading.value = true
  try { sources.value = (await listKnowledgeSources()).data }
  catch (error) { showToast(message(error, '来源加载失败')) }
  finally { loading.value = false }
}
const replace = (value:KnowledgeSource) => {
  const index = sources.value.findIndex(item => item.id === value.id)
  if (index >= 0) sources.value[index] = value
}
const toggle = async (item:KnowledgeSource, enabled:boolean) => {
  updating.value = item.id
  try { replace((await updateKnowledgeSource(item.id, enabled, item.refreshPolicy)).data) }
  catch (error) { showToast(message(error, '更新失败')) }
  finally { updating.value = '' }
}
const choosePolicy = (item:KnowledgeSource) => { selected.value = item; showPolicies.value = true }
const selectPolicy = async (action:{ value:KnowledgeRefreshPolicy }) => {
  showPolicies.value = false
  if (!selected.value) return
  const item = selected.value
  updating.value = item.id
  try { replace((await updateKnowledgeSource(item.id, item.enabled, action.value)).data) }
  catch (error) { showToast(message(error, '频率更新失败')) }
  finally { updating.value = ''; selected.value = null }
}
const refresh = async (item:KnowledgeSource) => {
  refreshing.value = item.id
  try {
    const result = (await refreshKnowledgeSource(item.id)).data
    replace(result.source)
    showToast(result.changed ? '发现变化，已生成并切换新版本' : '网页未变化，已跳过向量化')
  } catch (error) {
    showToast(message(error, '同步失败'))
    await load()
  } finally { refreshing.value = '' }
}
const message = (error:unknown, fallback:string) => error instanceof Error ? error.message : fallback
const policyText = (value:KnowledgeRefreshPolicy) => policies.find(item => item.value === value)?.name || value
const statusText = (value:string) => ({ PENDING:'待同步', SYNCING:'同步中', ACTIVE:'已更新',
  UNCHANGED:'无变化', FAILED:'失败', DISABLED:'已停用' } as Record<string,string>)[value] || value
const statusType = (value:string) => value === 'FAILED' ? 'danger' : value === 'ACTIVE' || value === 'UNCHANGED'
  ? 'success' : value === 'DISABLED' ? 'default' : 'warning'
const typeText = (value:string) => ({ notice:'公告', opening:'开放/预约', exhibition:'展览',
  ticket:'票价', transport:'交通', guide:'攻略' } as Record<string,string>)[value] || value
const formatTime = (value?:string) => value ? new Date(value).toLocaleString('zh-CN', { hour12:false }) : '尚未同步'
onMounted(load)
</script>

<style scoped>
.page{min-height:100vh;padding-bottom:28px;background:#f7f8fa;text-align:left}.summary{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;margin:12px;border-radius:14px;overflow:hidden;background:#ebedf0}.summary div{display:flex;flex-direction:column;align-items:center;padding:14px 4px;background:#fff}.summary b{color:#1989fa;font-size:22px}.summary span{margin-top:3px;color:#969799;font-size:12px}.explain,.source-card{margin:12px;padding:15px;border-radius:14px;background:#fff}.explain p{margin:6px 0 0;color:#969799;font-size:12px;line-height:1.6}.loading{padding:40px}.source-head{display:flex;gap:12px;align-items:flex-start;justify-content:space-between}.source-name{display:flex;min-width:0;align-items:center;gap:7px}.source-name b{font-size:16px}.meta{margin:8px 0 5px;color:#646566;font-size:12px}.crawl{margin:5px 0;color:#969799;font-size:11px;line-height:1.5}.url{display:block;overflow:hidden;color:#1989fa;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.time-grid{display:grid;grid-template-columns:1fr 1fr;margin-top:12px;padding:10px;border-radius:8px;background:#f7f8fa;color:#969799;font-size:11px;line-height:1.6}.time-grid b{color:#646566;font-weight:500}.error{margin:8px 0 0;color:#ee0a24;font-size:12px;line-height:1.5}.actions{display:flex;justify-content:flex-end;gap:8px;margin-top:12px}
</style>
