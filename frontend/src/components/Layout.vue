<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon class="logo-icon"><Cpu /></el-icon>
        <span v-show="!isCollapse">AI Agent</span>
      </div>
      <el-menu
        :default-active="activeRoute"
        :collapse="isCollapse"
        :router="true"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item
          v-for="route in menuRoutes"
          :key="route.path"
          :index="'/' + route.path"
        >
          <el-icon><component :is="route.meta.icon" /></el-icon>
          <template #title>{{ route.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="page-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag v-if="store.sessionId" type="info" size="small">
            会话: {{ store.sessionId.slice(0, 8) }}...
          </el-tag>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/store'

const store = useChatStore()
const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

const menuRoutes = computed(() => {
  const root = router.options.routes.find(r => r.path === '/')
  return root ? root.children.filter(r => r.meta && r.meta.title) : []
})

const activeRoute = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: #304156;
  transition: width 0.28s;
  overflow: hidden;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    gap: 8px;

    .logo-icon {
      font-size: 24px;
      color: #409eff;
    }
  }

  :deep(.el-menu) {
    border-right: none;
  }
}

.header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      color: #5a5e66;

      &:hover {
        color: #409eff;
      }
    }

    .page-title {
      font-size: 16px;
      font-weight: 500;
    }
  }
}

.main {
  padding: 0;
  overflow: hidden;
}
</style>
