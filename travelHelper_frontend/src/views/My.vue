<template>
  <div class="profile-container">
    <van-nav-bar title="我的" />

    <div v-if="auth.user" class="user-info">
      <div class="avatar">{{ auth.user.username.slice(0, 1).toUpperCase() }}</div>
      <div class="user-details">
        <h2>{{ auth.user.username }}</h2>
        <p>{{ auth.user.email }}</p>
      </div>
    </div>
    <div v-else class="guest-card" @click="router.push('/login')">
      <van-icon name="contact-o" size="54" /><div><h2>登录 / 注册</h2><p>登录后保存你的旅行对话</p></div>
    </div>

    <div class="menu-section">
      <h3 class="menu-title">我的服务</h3>
      <van-cell-group>
        <van-cell title="对话历史" label="查看推荐、索引来源与知识图谱对话" is-link icon="history"
          @click="router.push('/history')" />
        <van-cell title="设置" label="账号安全与修改密码" is-link icon="setting-o"
          @click="router.push('/settings')" />
        <van-cell title="知识库建设" label="加载PDF、Word、网页和Markdown并自动向量化" is-link icon="cluster-o"
          @click="router.push('/knowledge')" />
      </van-cell-group>
    </div>

    <div class="menu-section">
      <h3 class="menu-title">关于</h3>
      <van-cell-group>
        <van-cell title="关于我们" is-link @click="aboutDialogVisible = true" />
        <van-cell title="版本信息" value="v1.1.0" />
      </van-cell-group>
    </div>

    <van-dialog v-model:show="aboutDialogVisible" title="关于我们" confirm-button-text="知道了">
      <div class="about-content">
        <p>智能旅游助手 v1.1.0</p>
        <p>融合向量检索与知识图谱，为你提供可追溯的旅行建议。</p>
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const aboutDialogVisible = ref(false)
</script>

<style scoped>
.profile-container { min-height: 100vh; padding-bottom: 70px; background: #f7f8fa; }
.user-info,.guest-card { display: flex; align-items: center; padding: 32px 24px; color: #fff; background: linear-gradient(135deg,#1989fa,#36cbcb); text-align: left; }
.avatar { width: 72px; height: 72px; border-radius: 50%; display: grid; place-items: center; flex: 0 0 auto; font-size: 30px; font-weight: 700; background: rgba(255,255,255,.23); border: 3px solid rgba(255,255,255,.45); }
.user-details,.guest-card div { margin-left: 18px; min-width: 0; }
.user-details h2,.guest-card h2 { margin: 0 0 7px; color: #fff; font-size: 21px; }
.user-details p,.guest-card p { margin: 0; opacity: .88; font-size: 14px; overflow: hidden; text-overflow: ellipsis; }
.menu-section { margin: 15px 10px 0; overflow: hidden; border-radius: 12px; background: #fff; text-align: left; }
.menu-title { margin: 0; padding: 12px 15px; color: #646566; font-size: 14px; border-bottom: 1px solid #f0f0f0; }
.about-content { padding: 20px 24px; color: #646566; line-height: 1.7; text-align: center; }
.about-content p + p { margin-top: 10px; }
</style>
