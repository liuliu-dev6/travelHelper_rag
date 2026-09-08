<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const showTabbar = computed(() => auth.isLoggedIn && !route.meta.hideTabbar)
</script>

<template>
  <div id="app">
    <router-view v-slot="{ Component, route: matchedRoute }">
      <!-- 页面级路由必须拥有稳定且不同的组件 key。
           history -> chat 时强制卸载旧页面；chat 内 query 改变则复用组件并由 Chat.vue 的 watch 切换会话。 -->
      <component :is="Component" :key="String(matchedRoute.name || matchedRoute.path)" />
    </router-view>
    <van-tabbar v-if="showTabbar" route>
      <van-tabbar-item icon="home-o" to="/home">首页</van-tabbar-item>
      <van-tabbar-item icon="chat-o" to="/chat">对话</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/my">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
