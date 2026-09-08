<template>
  <div class="auth-page">
    <div class="auth-hero">
      <van-icon name="guide-o" size="48" />
      <h1>欢迎回来</h1>
      <p>登录后继续你的智能旅行探索</p>
    </div>
    <van-form class="auth-card" @submit="submit">
      <van-field v-model.trim="account" name="account" label="账号" placeholder="用户名或邮箱"
        left-icon="contact-o" :rules="[{ required: true, message: '请输入用户名或邮箱' }]" />
      <van-field v-model="password" name="password" label="密码" type="password" placeholder="请输入密码"
        left-icon="closed-eye" :rules="[{ required: true, message: '请输入密码' }]" />
      <div class="submit-area">
        <van-button round block type="primary" native-type="submit" :loading="auth.loading">登录</van-button>
      </div>
    </van-form>
    <p class="auth-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const account = ref('')
const password = ref('')

const submit = async () => {
  try {
    await auth.login(account.value, password.value)
    showToast('登录成功')
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/home')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '登录失败')
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; box-sizing: border-box; padding: 64px 20px 24px; background: linear-gradient(160deg,#eaf5ff 0%,#f7fbff 45%,#fff 100%); }
.auth-hero { color: #1989fa; margin-bottom: 34px; }
.auth-hero h1 { margin: 14px 0 8px; color: #1f2d3d; font-size: 28px; font-weight: 700; }
.auth-hero p { color: #7d8b99; font-size: 14px; }
.auth-card { background: #fff; border-radius: 18px; overflow: hidden; padding: 12px 4px; box-shadow: 0 12px 36px rgba(25,137,250,.1); }
.submit-area { margin: 24px 16px 8px; }
.auth-switch { margin-top: 24px; color: #969799; font-size: 14px; }
.auth-switch a { color: #1989fa; text-decoration: none; }
</style>
