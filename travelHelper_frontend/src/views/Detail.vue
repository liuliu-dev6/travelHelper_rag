<template>
  <div class="page-container">
    <van-nav-bar title="推荐结果" left-arrow @click="goBack"/>
    <div class="page-content">

      <div v-if="loading" class="loading-container">
        <van-loading size="48px" type="spinner"/>
      </div>

      <div v-else-if="error" class="error-container">
        <van-empty :description="error">
          <van-button round type="primary" class="bottom-button" @click="fetchRecommend">重新获取</van-button>
        </van-empty>
      </div>

      <template v-else-if="tripData && tripData.success===true">
        <div class="overview-card">

          <div class="card overview-card" style="display: flex;">
            <h2>{{ tripData.city }}.{{ tripData.days }}天行程</h2>
            <span class="trip-budget" :style="{'margin':'auto'}">预算：{{ tripData.totalBudget }}元</span>
          </div>
          <van-collapse v-model="activeDays" >
            <van-collapse-item v-for="day in tripData.dailyItinerary" :key="day.day" :title="'第'+day.day+'天'" :name="day.day">

              <div class="day-schedule">

                <div class="schedule-section">
                  <div class="section-label morning">上午</div>
                  <SpotItem :data="day.morning"/>
                </div>

                <div class="schedule-section">
                  <div class="section-label afternoon">下午</div>
                  <SpotItem :data="day.afternoon"/>
                </div>

                <div class="schedule-section">
                  <div class="section-label evening">晚上</div>
                  <SpotItem :data="day.evening"/>
                </div>

              </div>
            </van-collapse-item>
          </van-collapse>
        </div>

        <!-- 预算明细 -->
        <div class="card budget-card">
          <div class="section-title"> 预算明细</div>
          <BudgetTable :data="tripData.budgetBreakdown" :total="tripData.totalBudget" />
        </div>

        <!-- 出行贴士 -->
        <div class="card tips-card" v-if="tripData.tips && tripData.tips.length">
          <div class="section-title"> 温馨提示</div>
          <ul class="tips-list">
            <li v-for="(tip, index) in tripData.tips" :key="index">{{ tip }}</li>
          </ul>
        </div>

        <!-- 注意事项 -->
        <div class="card warnings-card" v-if="tripData.warnings && tripData.warnings.length">
          <div class="section-title"> 注意事项</div>
          <ul class="warnings-list">
            <li v-for="(warning, index) in tripData.warnings" :key="index">{{ warning }}</li>
          </ul>
        </div>
      </template>

      <div v-else class="error-container">
        <van-empty description="暂无数据，请返回重新搜索">
          <van-button round type="primary" class="bottom-button" @click="goBack">返回首页</van-button>
        </van-empty>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { onMounted, reactive, ref } from 'vue'
import { travelRecommend } from '@/api/travel'
import SpotItem from '@/components/SpotItem.vue'
import BudgetTable from '@/components/BudgetTable.vue'
const route = useRoute()
const router = useRouter()

const formData = reactive({
  city: '',
  budget: '',
  days: ''
})

const loading = ref(false)
const error = ref('')
const tripData = ref<any>(null)
const activeDays = ref([])

onMounted(async () => {
  formData.city = route.query.city as string
  formData.budget = route.query.budget as string
  formData.days = route.query.days as string

  await fetchRecommend()
})

const goBack = () => {
  router.back()
}

const fetchRecommend = async () => {
  loading.value = true
  error.value = ''

  try {
    const params = {
      city: formData.city,
      budget: Number(formData.budget),
      days: Number(formData.days)
    }

    const response = await travelRecommend(params)
    tripData.value = response.data
  } catch (err: any) {
    console.error('接口调用失败:', err)
    error.value = err.message || '请求失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style lang="css" scoped>
.page-container {
    min-height: 100vh;
    background-color: #f5f5f5;
    padding-bottom: 70px;
}
.card {
    background-color: #fff;
    border-radius: 8px;
    padding: 5px;
    margin-bottom: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.section-title {
    font-size: 18px;
    font-weight: 600;
    color: #323233;
    margin-bottom: 12px;
}
.page-content {
    padding: 16px;
}
.overview-card {
  margin-bottom: 16px;
}

.trip-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trip-header h2 {
  font-size: 20px;
  color: #323233;
  margin: 0;
}

.trip-budget {
  font-size: 16px;
  color: #ee0a24;
  font-weight: 600;
}

.trip-collapse {
  margin-bottom: 16px;
}

.day-schedule {
  padding: 8px 0;
}

.schedule-section {
  margin-bottom: 16px;
}

.schedule-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 4px;
  display: inline-block;
  margin-bottom: 8px;
}

.section-label.morning {
  background: #fff7e6;
  color: #fa8c16;
}

.section-label.afternoon {
  background: #e6f7ff;
  color: #1890ff;
}

.section-label.evening {
  background: #f6ffed;
  color: #52c41a;
}

.budget-card,
.tips-card,
.warnings-card {
  margin-bottom: 16px;
}

.tips-list,
.warnings-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.tips-list li,
.warnings-list li {
  padding: 8px 0;
  color: #666;
  font-size: 14px;
  border-bottom: 1px solid #f5f5f5;
}

.tips-list li:last-child,
.warnings-list li:last-child {
  border-bottom: none;
}

.detail-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  max-width: 750px;
  margin: 0 auto;
}

.error-card {
  text-align: center;
  padding: 40px 16px;
}
</style>
