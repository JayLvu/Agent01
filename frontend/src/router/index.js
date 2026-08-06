import Vue from 'vue'
import VueRouter from 'vue-router'
import Chat from '@/views/Chat.vue'
import Documents from '@/views/Documents.vue'
import About from '@/views/About.vue'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat,
    meta: { title: '对话', icon: 'el-icon-chat-dot-round' }
  },
  {
    path: '/documents',
    name: 'Documents',
    component: Documents,
    meta: { title: '文档管理', icon: 'el-icon-document' }
  },
  {
    path: '/about',
    name: 'About',
    component: About,
    meta: { title: '关于', icon: 'el-icon-info' }
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

export default router
