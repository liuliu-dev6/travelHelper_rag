import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { resetCsrfToken } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<authApi.UserInfo | null>(null)
  const initialized = ref(false)
  const loading = ref(false)
  const isLoggedIn = computed(() => Boolean(user.value))

  async function initialize() {
    if (initialized.value) return
    try {
      const result = await authApi.getCurrentUser()
      user.value = result.data
    } catch {
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function login(account: string, password: string) {
    loading.value = true
    try {
      const result = await authApi.login({ account, password })
      // Spring Security may rotate the session id after authentication. Any
      // token obtained for the anonymous session must not be reused.
      resetCsrfToken()
      user.value = result.data
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function register(username: string, email: string, password: string) {
    loading.value = true
    try {
      await authApi.register({ username, email, password })
      await login(email, password)
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      initialized.value = true
      resetCsrfToken()
    }
  }

  return { user, initialized, loading, isLoggedIn, initialize, login, register, logout }
})
