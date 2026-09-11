<template>
  <div class="page">
    <van-nav-bar title="知识库建设" left-arrow @click-left="router.back()" />

    <section class="hero">
      <van-icon name="cluster-o" size="30" color="#1989fa" />
      <div class="hero-main"><b>多格式文档自动入库</b><p>结构解析、父子分块，小块召回后展开完整上下文。</p></div>
      <div class="hero-actions">
        <van-button size="small" plain type="warning" @click="router.push('/knowledge/reviews')">人工审核</van-button>
        <van-button size="small" plain type="primary" @click="router.push('/knowledge/sources')">可信订阅</van-button>
      </div>
    </section>

    <section class="card">
      <van-tabs v-model:active="mode">
        <van-tab title="上传文件" name="file">
          <van-form class="form" @submit="upload">
            <van-uploader v-model="selected" multiple :max-count="10" :max-size="20 * 1024 * 1024"
              accept=".pdf,.doc,.docx,.html,.htm,.md,.markdown,.txt" @oversize="showToast('单个文件不能超过20MB')">
              <van-button icon="plus" type="primary" plain>选择 PDF / Word / HTML / Markdown</van-button>
            </van-uploader>
            <MetaFields v-model:city="city" v-model:knowledge-type="knowledgeType" />
            <van-button block type="primary" native-type="submit" :loading="busy" loading-text="正在解析并向量化...">
              自动处理并入库
            </van-button>
          </van-form>
        </van-tab>
        <van-tab title="加载网页" name="url">
          <van-form class="form" @submit="ingestUrl">
            <van-field v-model.trim="url" label="网页URL" placeholder="https://..." type="url"
              :rules="[{ required:true, message:'请输入网页URL' }]" />
            <van-field v-model.trim="title" label="标题" placeholder="可选，默认读取网页标题" />
            <MetaFields v-model:city="city" v-model:knowledge-type="knowledgeType" />
            <van-button block type="primary" native-type="submit" :loading="busy" loading-text="正在加载并向量化...">
              加载网页并入库
            </van-button>
          </van-form>
        </van-tab>
      </van-tabs>
      <p class="tip">高质量文档自动入库；疑似乱码、扫描件或复杂版面会进入人工审核区，批准前不会生成向量。图谱关系候选始终需要人工审核后才会写入 Neo4j。</p>
    </section>

    <section class="list-card">
      <div class="section-title"><b>已加载文档</b><van-button size="mini" plain type="primary" @click="load">刷新</van-button></div>
      <van-loading v-if="loading" vertical>加载中...</van-loading>
      <van-empty v-else-if="documents.length === 0" description="还没有文档" />
      <van-swipe-cell v-for="item in documents" v-else :key="item.id" :disabled="!!item.sourceId">
        <div class="document">
          <van-icon :name="item.sourceType === 'URL' ? 'link-o' : 'description-o'" size="23" color="#1989fa" />
          <div class="document-main">
            <div><b>{{ item.title }}</b><van-tag :type="statusType(item.status)">{{ statusText(item.status) }}</van-tag></div>
            <small>{{ item.sourceId ? '可信订阅' : item.sourceType === 'URL' ? '网页' : '文件' }} · {{ item.city || '未限定城市' }} · {{ item.chunkCount }}个检索小块</small>
            <small v-if="item.status === 'INDEXED'">{{ item.characterCount }}字 · 可参与在线检索</small>
            <small v-if="item.errorMessage" class="error">{{ item.errorMessage }}</small>
          </div>
        </div>
        <template v-if="!item.sourceId" #right><van-button square type="danger" text="删除" class="delete" @click="remove(item)" /></template>
      </van-swipe-cell>
    </section>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast, Field, Picker, Popup } from 'vant'
import { deleteKnowledgeDocument, ingestKnowledgeUrl, listKnowledgeDocuments, uploadKnowledgeDocuments,
  type KnowledgeDocument } from '@/api/knowledgeDocuments'

const router = useRouter(), mode = ref('file'), busy = ref(false), loading = ref(false)
const selected = ref<any[]>([]), url = ref(''), title = ref(''), city = ref(''), knowledgeType = ref('guide')
const documents = ref<KnowledgeDocument[]>([])
const typeOptions = [
  { text:'综合攻略', value:'guide' }, { text:'景点资料', value:'poi' },
  { text:'美食资料', value:'food' }, { text:'交通资料', value:'transport' }, { text:'历史文化', value:'culture' }
]

const MetaFields = defineComponent({
  props: { city: String, knowledgeType: String }, emits: ['update:city','update:knowledgeType'],
  setup(props, { emit }) {
    const show = ref(false)
    return () => h('div', [
      h(Field, { modelValue:props.city, label:'城市', placeholder:'可选，例如长沙',
        'onUpdate:modelValue':(v:string) => emit('update:city', v) }),
      h(Field, { modelValue:typeOptions.find(x => x.value === props.knowledgeType)?.text, label:'资料类型', readonly:true,
        isLink:true, onClick:() => { show.value = true } }),
      h(Popup, { show:show.value, position:'bottom', 'onUpdate:show':(v:boolean) => { show.value = v } }, () =>
        h(Picker, { columns:typeOptions, onCancel:() => { show.value = false }, onConfirm:({ selectedOptions }:any) => {
          emit('update:knowledgeType', selectedOptions[0].value); show.value = false
        }}))
    ])
  }
})

const load = async () => {
  loading.value = true
  try { documents.value = (await listKnowledgeDocuments()).data }
  catch (e) { showToast(e instanceof Error ? e.message : '加载失败') }
  finally { loading.value = false }
}
const upload = async () => {
  const files = selected.value.map(item => item.file).filter(Boolean) as File[]
  if (!files.length) return showToast('请先选择文件')
  busy.value = true
  try { await uploadKnowledgeDocuments(files, city.value, knowledgeType.value); selected.value = []; showToast('文档已完成分块并入库'); await load() }
  catch (e) { showToast(e instanceof Error ? e.message : '文档入库失败'); await load() }
  finally { busy.value = false }
}
const ingestUrl = async () => {
  busy.value = true
  try { await ingestKnowledgeUrl(url.value, title.value, city.value, knowledgeType.value); url.value=''; title.value=''; showToast('网页已完成分块并入库'); await load() }
  catch (e) { showToast(e instanceof Error ? e.message : '网页入库失败'); await load() }
  finally { busy.value = false }
}
const remove = async (item: KnowledgeDocument) => {
  try { await showConfirmDialog({ title:'删除知识文档', message:'将同时删除该文档的全部向量分块，确定继续吗？' }); await deleteKnowledgeDocument(item.id); showToast('已删除'); await load() }
  catch (e) { if (e instanceof Error && e.message) showToast(e.message) }
}
const statusText = (value:string) => ({ PROCESSING:'处理中', REVIEW_REQUIRED:'待人工审核', PARSE_REVIEW:'待人工审核', REJECTED:'已拒绝', INDEXED:'已入库', FAILED:'失败' } as any)[value] || value
const statusType = (value:string) => value === 'INDEXED' ? 'success' : value === 'FAILED' ? 'danger' : 'warning'
onMounted(load)
</script>

<style scoped>
.page{min-height:100vh;background:#f7f8fa;padding-bottom:30px;text-align:left}.hero,.card,.list-card{margin:12px;padding:16px;border-radius:14px;background:#fff}.hero{display:flex;gap:12px;align-items:flex-start}.hero-main{min-width:0;flex:1}.hero b{font-size:18px}.hero p,.tip{margin:5px 0 0;color:#969799;font-size:12px;line-height:1.6}.form{display:flex;flex-direction:column;gap:12px;padding-top:16px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.document{display:flex;gap:11px;padding:14px 4px;border-top:1px solid #f2f3f5}.document-main{display:flex;min-width:0;flex:1;flex-direction:column;gap:5px}.document-main>div{display:flex;align-items:center;gap:7px}.document-main b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.document-main small{color:#969799}.document-main .error{color:#ee0a24}.delete{height:100%}
.hero-actions{display:flex;flex-direction:column;gap:7px;flex:none}
</style>
