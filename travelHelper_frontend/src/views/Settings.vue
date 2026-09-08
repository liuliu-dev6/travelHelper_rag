<template>
  <div class="settings-page">
    <van-nav-bar title="设置" left-arrow @click-left="router.back()" />
    <div class="account-card">
      <div class="account-line"><span>用户名</span><strong>{{ auth.user?.username }}</strong></div>
      <div class="account-line"><span>邮箱</span><strong>{{ auth.user?.email }}</strong></div>
      <div class="account-line"><span>注册时间</span><strong>{{ createdDate }}</strong></div>
    </div>

    <div class="section-title">账号安全</div>
    <van-form class="password-form" @submit="submitPassword">
      <van-field v-model="currentPassword" type="password" label="当前密码" placeholder="请输入当前密码"
        :rules="[{ required: true, message: '请输入当前密码' }]" />
      <van-field v-model="newPassword" type="password" label="新密码" placeholder="至少8个字符"
        :rules="[{ validator: validPassword, message: '新密码应为8-72个字符' }]" />
      <van-field v-model="confirmPassword" type="password" label="确认密码" placeholder="再次输入新密码"
        :rules="[{ validator: samePassword, message: '两次输入的密码不一致' }]" />
      <div class="button-area"><van-button block round type="primary" native-type="submit" :loading="saving">修改密码</van-button></div>
    </van-form>

    <div class="logout-area"><van-button block plain round type="danger" @click="confirmLogout">退出登录</van-button></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/auth'

const router = useRouter()
const auth = useAuthStore()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const saving = ref(false)
const createdDate = computed(() => auth.user?.createdAt ? new Date(auth.user.createdAt).toLocaleDateString('zh-CN') : '-')
const validPassword = (value: string) => value.length >= 8 && value.length <= 72
const samePassword = (value: string) => Boolean(value) && value === newPassword.value

const submitPassword = async () => {
  saving.value = true
  try {
    await changePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    showSuccessToast({ message: '密码修改成功', duration: 5000 })
  } catch (error) {
    showToast(error instanceof Error ? error.message : '修改失败')
  } finally { saving.value = false }
}

const confirmLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
    await auth.logout()
    await router.replace('/login')
  } catch (error) {
    if (error instanceof Error) showToast(error.message)
  }
}
</script>

<style scoped>
.settings-page { min-height: 100vh; background: #f7f8fa; text-align: left; }
.account-card,.password-form { margin: 16px; overflow: hidden; border-radius: 12px; background: #fff; }
.account-line { display: flex; justify-content: space-between; gap: 20px; padding: 14px 16px; border-bottom: 1px solid #f2f3f5; font-size: 14px; }
.account-line span { color: #969799; }.account-line strong { color: #323233; font-weight: 500; word-break: break-all; }
.section-title { margin: 22px 20px 8px; color: #969799; font-size: 13px; }
.button-area { padding: 22px 16px 16px; }.logout-area { margin: 28px 16px; }
</style>
