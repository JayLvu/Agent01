import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'
import Chat from '@/views/Chat.vue'
import Documents from '@/views/Documents.vue'
import About from '@/views/About.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: Chat,
        meta: { title: '对话', icon: 'ChatDotRound' }
      },
      {
        path: 'documents',
        name: 'Documents',
        component: Documents,
        meta: { title: '文档管理', icon: 'Document' }
      },
      {
        path: 'about',
        name: 'About',
        component: About,
        meta: { title: '关于', icon: 'InfoFilled' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router
