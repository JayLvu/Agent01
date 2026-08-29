import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/components/Layout.vue'
import Chat from '@/views/Chat.vue'
import Documents from '@/views/Documents.vue'
import Skills from '@/views/Skills.vue'
import Tasks from '@/views/Tasks.vue'
import Stats from '@/views/Stats.vue'
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
        path: 'skills',
        name: 'Skills',
        component: Skills,
        meta: { title: 'Skill 管理', icon: 'MagicStick' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: Tasks,
        meta: { title: '定时任务', icon: 'AlarmClock' }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: Stats,
        meta: { title: '统计与审计', icon: 'DataAnalysis' }
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
