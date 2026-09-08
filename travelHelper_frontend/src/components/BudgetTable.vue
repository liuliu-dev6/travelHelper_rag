<template>
  <div class="budget-table">
    <van-cell-group :border="false">
      <van-cell
        v-for="(value, key) in budgetItems"
        :key="key"
        :title="getLabel(key)"
        :value="`¥${value}`"
        :border="false"
      />
    </van-cell-group>
    <div class="budget-total">
      <span>总计</span>
      <span class="total-amount">¥{{ total }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({})
  },
  total: {
    type: [Number, String],
    default: 0
  }
})

const budgetItems = computed(() => {
  return {
    accommodation: props.data.accommodation || 0,
    food: props.data.food || 0,
    transportation: props.data.transportation || 0,
    tickets: props.data.tickets || 0,
    other: props.data.other || 0
  }
})

const labelMap = {
  accommodation: '住宿',
  food: '餐饮',
  transportation: '交通',
  tickets: '门票',
  other: '其他'
}

const getLabel = (key) => {
  return labelMap[key] || key
}
</script>

<style scoped>
.budget-table {
  margin-top: 8px;
}

.budget-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
}

.total-amount {
  color: #ee0a24;
  font-size: 18px;
}
</style>