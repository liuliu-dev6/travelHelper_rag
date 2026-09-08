<template>
  <div class="auth-page">
    <van-nav-bar title="创建账号" left-arrow @click-left="router.back()" />
    <div class="auth-intro"><h1>加入智能旅游助手</h1><p>保存你的知识图谱旅行咨询记录</p></div>
    <van-form class="auth-card" @submit="submit">
      <van-field v-model.trim="username" name="username" label="用户名" placeholder="2-32个字符"
        :rules="[{ required: true, message: '请输入用户名' }, { pattern: /^[\p{L}\p{N}_-]{2,32}$/u, message: '用户名格式不正确' }]" />
      <van-field v-model.trim="email" name="email" label="邮箱" placeholder="name@example.com"
        :rules="[{ required: true, message: '请输入邮箱' }, { pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '邮箱格式不正确' }]" />
      <van-field v-model="password" name="password" label="密码" type="password" placeholder="至少8个字符"
        :rules="[{ required: true, message: '请输入密码' }, { validator: validPassword, message: '密码至少8个字符' }]" />
      <van-field v-model="confirmPassword" name="confirmPassword" label="确认密码" type="password" placeholder="再次输入密码"
        :rules="[{ validator: samePassword, message: '两次输入的密码不一致' }]" />
      <div class="submit-area"><van-button round block type="primary" native-type="submit" :loading="auth.loading">注册并登录</van-button></div>
    </van-form>
    <p class="auth-switch">已有账号？<router-link to="/login">直接登录</router-link></p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const validPassword = (value: string) => value.length >= 8 && value.length <= 72
const samePassword = (value: string) => Boolean(value) && value === password.value

const submit = async () => {
  try {
    await auth.register(username.value, email.value, password.value)
    showToast('注册成功')
    await router.replace('/home')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '注册失败')
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; box-sizing: border-box; background: #f7f8fa; }
.auth-intro { padding: 32px 24px 24px; text-align: left; }
.auth-intro h1 { margin: 0 0 8px; font-size: 25px; color: #323233; }
.auth-intro p { color: #969799; font-size: 14px; }
.auth-card { margin: 0 16px; padding: 12px 4px; border-radius: 16px; overflow: hidden; background: #fff; }
.submit-area { margin: 24px 16px 8px; }
.auth-switch { margin-top: 22px; color: #969799; font-size: 14px; }
.auth-switch a { color: #1989fa; text-decoration: none; }
</style>
