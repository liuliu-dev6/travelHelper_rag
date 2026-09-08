import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Chat from '../views/Chat.vue'
import My from '../views/My.vue'
import Detail from '../views/Detail.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import History from '../views/History.vue'
import Settings from '../views/Settings.vue'
import KnowledgeBase from '../views/KnowledgeBase.vue'
import KnowledgeSources from '../views/KnowledgeSources.vue'
import pinia from '../stores'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'Home', component: Home, meta: { requiresAuth: true } },
  { path: '/login', name: 'Login', component: Login, meta: { guestOnly: true, hideTabbar: true } },
  { path: '/register', name: 'Register', component: Register, meta: { guestOnly: true, hideTabbar: true } },
  { path: '/chat', name: 'Chat', component: Chat, meta: { requiresAuth: true } },
  { path: '/my', name: 'My', component: My, meta: { requiresAuth: true } },
  { path: '/detail', name: 'Detail', component: Detail, meta: { requiresAuth: true } },
  { path: '/history', name: 'History', component: History, meta: { requiresAuth: true, hideTabbar: true } },
  { path: '/settings', name: 'Settings', component: Settings, meta: { requiresAuth: true, hideTabbar: true } },
  { path: '/knowledge', name: 'KnowledgeBase', component: KnowledgeBase, meta: { requiresAuth: true, hideTabbar: true } },
  { path: '/knowledge/sources', name: 'KnowledgeSources', component: KnowledgeSources, meta: { requiresAuth: true, hideTabbar: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async to => {
  const auth = useAuthStore(pinia)
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.path === '/' ? '/home' : to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) return { name: 'My' }
  return true
})

export default router
