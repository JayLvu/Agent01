<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <i class="el-icon-cpu"></i>
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
          <i :class="route.meta.icon"></i>
          <span slot="title">{{ route.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <i
            :class="isCollapse ? 'el-icon-s-unfold' : 'el-icon-s-fold'"
            class="collapse-btn"
            @click="isCollapse = !isCollapse"
          ></i>
          <span class="page-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag v-if="sessionId" type="info" size="small">
            会话: {{ sessionId.slice(0, 8) }}...
          </el-tag>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'Layout',
  data() {
    return {
      isCollapse: false
    }
  },
  computed: {
    ...mapGetters(['sessionId']),
    menuRoutes() {
      // 从根路由的 children 中获取菜单项
      const root = this.$router.options.routes.find(r => r.path === '/')
      return root ? root.children.filter(r => r.meta && r.meta.title) : []
    },
    activeRoute() {
      return this.$route.path
    },
    currentTitle() {
      return this.$route.meta?.title || ''
    }
  }
}
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

    i {
      font-size: 24px;
      color: #409eff;
    }
  }

  ::v-deep .el-menu {
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
