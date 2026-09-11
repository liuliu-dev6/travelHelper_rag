<template>
  <div class="page">
    <van-nav-bar title="知识人工审核" left-arrow @click-left="router.back()" />
    <section class="summary">
      <div><b>{{ documents.length }}</b><span>待审文档</span></div>
      <div><b>{{ relations.length }}</b><span>图谱关系</span></div>
      <van-button size="small" plain type="primary" :loading="loading" @click="load">刷新</van-button>
    </section>

    <van-tabs v-model:active="active">
      <van-tab title="文档准入" name="documents">
        <van-empty v-if="!loading && documents.length === 0" description="没有待审核文档" />
        <section v-for="item in documents" :key="item.id" class="card">
          <div class="title"><b>{{ item.title }}</b><van-tag type="warning">待审核</van-tag></div>
          <p>{{ item.sourceType === 'URL' ? item.sourceUri : '上传文件' }} · {{ item.city || '未限定城市' }}</p>
          <div class="issue">{{ item.errorMessage || '质量门禁要求人工确认' }}</div>
          <div class="actions">
            <van-button size="small" type="danger" plain @click="rejectDocument(item)">拒绝</van-button>
            <van-button size="small" type="primary" :loading="working === item.id" @click="approveDocument(item)">确认内容并入库</van-button>
          </div>
        </section>
      </van-tab>

      <van-tab title="图谱关系" name="relations">
        <van-empty v-if="!loading && relations.length === 0" description="没有待审核关系" />
        <section v-for="item in relations" :key="item.id" class="card">
          <div class="title"><b>{{ item.source.name }} → {{ item.target.name }}</b>
            <van-tag :type="item.status === 'CONFLICT' || item.status === 'FAILED' ? 'danger' : 'warning'">{{ relationStatus(item.status) }}</van-tag>
          </div>
          <div class="relation">{{ item.source.type }} —[{{ item.relationType }}]→ {{ item.target.type }}</div>
          <p>城市：{{ item.source.city || item.target.city || '未识别' }} · 置信度 {{ percent(item.confidence) }}</p>
          <blockquote>证据：{{ item.evidence || '无可定位证据' }}</blockquote>
          <div v-if="item.issues" class="issue">{{ item.issues }}</div>
          <div class="actions">
            <van-button size="small" type="danger" plain @click="rejectRelation(item)">拒绝</van-button>
            <van-button size="small" type="primary" :loading="working === item.id" @click="approveRelation(item)">审核通过并发布</van-button>
          </div>
        </section>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { approveGraphRelation, approveReviewDocument, listGraphRelationCandidates, listReviewDocuments,
  rejectGraphRelation, rejectReviewDocument, type GraphRelationCandidate, type KnowledgeDocument } from '@/api/knowledgeDocuments'

const router = useRouter()
const active = ref('documents'), loading = ref(false), working = ref('')
const documents = ref<KnowledgeDocument[]>([]), relations = ref<GraphRelationCandidate[]>([])

const load = async () => {
  loading.value = true
  try {
    const [documentResult, relationResult] = await Promise.all([listReviewDocuments(), listGraphRelationCandidates()])
    documents.value = documentResult.data
    relations.value = relationResult.data
  } catch (error) { showToast(error instanceof Error ? error.message : '审核队列加载失败') }
  finally { loading.value = false }
}
const approveDocument = async (item: KnowledgeDocument) => {
  try {
    await showConfirmDialog({ title:'批准文档入库', message:'已确认解析内容准确？批准后将生成向量，并抽取待审图谱候选。' })
    working.value = item.id; await approveReviewDocument(item.id); showToast('文档已入库'); await load()
  } catch (error) { if (error instanceof Error && error.message) showToast(error.message) }
  finally { working.value = '' }
}
const rejectDocument = async (item: KnowledgeDocument) => {
  try { await showConfirmDialog({ title:'拒绝文档', message:'该文档不会进入检索库，是否继续？' }); await rejectReviewDocument(item.id, '内容质量未通过人工审核'); await load() }
  catch (error) { if (error instanceof Error && error.message) showToast(error.message) }
}
const approveRelation = async (item: GraphRelationCandidate) => {
  try {
    await showConfirmDialog({ title:'发布图谱关系', message:`确认“${item.source.name} —${item.relationType}→ ${item.target.name}”有原文依据？` })
    working.value = item.id; await approveGraphRelation(item.id); showToast('关系已写入知识图谱'); await load()
  } catch (error) { if (error instanceof Error && error.message) showToast(error.message) }
  finally { working.value = '' }
}
const rejectRelation = async (item: GraphRelationCandidate) => {
  try { await showConfirmDialog({ title:'拒绝图谱关系', message:'该候选将不会写入 Neo4j，是否继续？' }); await rejectGraphRelation(item.id, '关系未通过人工审核'); await load() }
  catch (error) { if (error instanceof Error && error.message) showToast(error.message) }
}
const percent = (value:number) => `${Math.round(value * 100)}%`
const relationStatus = (value:string) => ({ PENDING:'待审核', CONFLICT:'存在冲突', FAILED:'发布失败' } as Record<string,string>)[value] || value
onMounted(load)
</script>

<style scoped>
.page{min-height:100vh;background:#f7f8fa;padding-bottom:30px;text-align:left}.summary{display:flex;align-items:center;gap:22px;margin:12px;padding:14px 16px;border-radius:14px;background:#fff}.summary div{display:flex;flex-direction:column}.summary b{font-size:22px;color:#1989fa}.summary span,.card p{color:#969799;font-size:12px}.summary button{margin-left:auto}.card{margin:12px;padding:15px;border-radius:14px;background:#fff}.title,.actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.title b{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.relation{margin-top:12px;padding:9px;border-radius:8px;background:#f2f7ff;color:#315c8d;font-family:monospace;font-size:12px}blockquote{margin:9px 0;padding-left:10px;border-left:3px solid #d9e8fa;color:#646566;font-size:13px;line-height:1.6}.issue{margin:9px 0;padding:9px;border-radius:8px;background:#fff7e8;color:#d46b08;font-size:12px;line-height:1.5}.actions{justify-content:flex-end;margin-top:12px}
</style>
