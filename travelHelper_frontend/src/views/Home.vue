<template>
    <div class="page-container">
        <van-nav-bar title="智能旅游助手" />
        
    <div class="page-content">
      <van-notice-bar left-icon="volume-o" text="基于AI的智能景点介绍与行程规划系统" />
    <div class="card" style="margin-top: 8px;">
      <div class="section-title" style="margin-bottom: 8px;">
        规划你的旅程
      </div>
      <van-field
  v-model="formData.city"
  is-link
  readonly
  label="目的地"
  placeholder="请选择城市"
  @click="showPicker = true"
  style="background-color: #f7f7f7;border-radius: 8px;margin-bottom: 8px;"
/>
<van-popup v-model:show="showPicker" destroy-on-close round position="bottom">
  <van-picker
    :columns="columns"
    @cancel="onCancel"
    @confirm="onConfirm"
  />
</van-popup>

   <van-field v-model="formData.budget" label="预算(元)" placeholder="请输入预算" style="background-color: #f7f7f7;border-radius: 8px;margin-bottom: 8px;" />
   <van-field v-model="formData.days" label="天数" placeholder="请输入天数" style="background-color: #f7f7f7;border-radius: 8px;margin-bottom: 8px;" />
    <van-button type="primary" block style="border-radius: 30px;" @click="handlePlan">开始规划</van-button>
    </div>
    <div class="card">
      <div class="section-title">
        快捷入口
      </div>
      <div class="quick-entries">
        <RouterLink to="/chat" class="entry-item">
          <van-icon name="chat-o" size="23" color="black" />
          <span class="entry-text">AI对话</span>
        </RouterLink>
        <RouterLink class="entry-item" to="/my">
          <van-icon name="user-o" size="23" color="black" />
          <span class="entry-text">我的</span>
        </RouterLink>
      </div>
    </div>
    <div class="card">
      <div class="section-title">
        热门目的地
      </div>
      <div class="hot-cities">
        <van-grid :column-num="4" :gap="12">
          <van-grid-item v-for="city in hotCities" :key="city">
            <div class="city-tag" @click="formData.city = city" :class="{'active': formData.city === city}">{{city}}</div>
          </van-grid-item>
        </van-grid>
      </div>
    </div> 
    </div>
  </div>
</template>

<script setup lang="ts">
import { showFailToast } from 'vant';
import { reactive, ref } from 'vue'
import {useRouter} from 'vue-router'
const router = useRouter()

    const formData = reactive({
      "city": '',
      "budget": '' as string | number,
      "days": '' as string | number
    });
    const hotCities = ref([
  '北京', '上海', '广州', '深圳', '成都', '杭州', '西安', '重庆'
]);
    const columns = ref([
      {text:'北京',value:'Beijing'},
      {text:'上海',value:'Shanghai'},
      {text:'广州',value:'Guangzhou'},
      {text:'深圳',value:'Shenzhen'},
      {text:'成都',value:'Chengdu'},      
      {text:'杭州',value:'Hangzhou'},
      {text:'西安',value:'Xian'},
      {text:'重庆',value:'Chongqing'},
      {text:'南京',value:'Nanjing'},
      {text:'武汉',value:'Wuhan'},
      {text:'苏州',value:'Suzhou'},
      {text:'长沙',value:'Changsha'},
      {text:'天津',value:'Tianjin'},
      {text:'郑州',value:'Zhengzhou'},
      {text:'济南',value:'Jianjin'},
      {text:'青岛',value:'Qingdao'},
      {text:'大连',value:'Dalian'},
      {text:'沈阳',value:'Sheny'},
      {text:'哈尔滨',value:'Harbin'},
      {text:'长春',value:'Changdu'},
      {text:'福州',value:'Fuzhou'},
      {text:'厦门',value:'Xiamen'},
      {text:'南昌',value:'Nanchang'},
      {text:'合肥',value:'Hefei'},
      {text:'昆明 ',value:'Kunming'},
      {text:'贵阳',value:'Guiyang'},
      {text:'南宁',value:'Nanning'},
      {text:'桂林',value:'Guangxi'},
      {text:'海口',value:'Haikou'},
      {text:'三亚',value:'Sanya'},
      {text:'丽江',value:'Liangjiang'},
      {text:'大理',value:'Daliang'},
      {text:'西安',value:'Xian'},
      {text:'兰州',value:'Lanzhou'},
      {text:'乌鲁木齐',value:'Urumqi'},
      {text:'拉萨',value:'Lhasa'},
      {text:'呼和浩特',value:'Hohhot'},
      {text:'太原',value:'Taiyuan'},
      {text:'石家庄',value:'Shijiazhuang'}
    ]);
    const showPicker = ref(false);
    
    const onConfirm = ({ selectedOptions }: { selectedOptions: any[] }) => {
      formData.city = selectedOptions[0].text;
      showPicker.value = false;

    };
    const onCancel = () => {
      showPicker.value = false;
    };
    const handlePlan = () => {
      
      if (!formData.city) {
        showFailToast('请输入目的地')
        return
      }
      if (!formData.budget || Number(formData.budget) < 100){
        showFailToast('预算不能低于100元')
        return
      }
      if (!formData.days || Number(formData.days) < 1 || Number(formData.days) > 30){
        showFailToast('天数不能低于1天或高于30天')
        return
      }
      // 跳转到详情页
      router.push({
        path: '/detail',
        query: {
          city: formData.city,
          budget: formData.budget,
          days: formData.days
        }
      });
    };

</script>

<style scoped>
.page-content {
    padding: 16px;
    margin: 0 auto;

}
.page-container {
    min-height: 100vh;
    background-color: #f5f5f5;
    padding-bottom: 70px;
}
.card {
    margin-bottom: 12px;
    background-color: #fff;
    border-radius: 8px;
    padding: 16px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.section-title {
    font-size: 16px;
    font-weight: 600;
    font-weight: bold;
    color: #333;
}

.quick-entries {
    display: flex;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #f0f0f0;
}
.entry-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
}
.entry-text {
    font-size: 13px;
    color: #666;
}
.hot-cities {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #f0f0f0;
}
.city-tag {
    padding: 8px 12px;
    border-radius: 16px;
    font-size: 14px;
    background: #f7f8fa;
    color: #666;
    transition:all 0.3s;
}
.city-tag.active {
    background: #e7f5ff;
    color: #409eff;
}
</style>
