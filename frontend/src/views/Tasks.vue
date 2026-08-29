<template>
  <div class="tasks-page">
    <div class="page-header">
      <h3>定时任务</h3>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">新建任务</el-button>
    </div>

    <el-table :data="tasks" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="message" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="执行时间" width="170">
        <template #default="{ row }">{{ formatTime(row.dueAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            size="small"
            type="danger"
            link
            @click="cancelTask(row)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建定时任务" width="440px">
      <el-form label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="可选" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.message" type="textarea" :rows="3" placeholder="要执行的内容/提醒事项" />
        </el-form-item>
        <el-form-item label="延迟(秒)">
          <el-input-number v-model="form.delaySeconds" :min="10" :max="2592000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import scheduleApi from '@/api/schedule'

const tasks = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({ name: '', message: '', delaySeconds: 300 })

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await scheduleApi.list()
  } catch (e) {
    ElMessage.error('加载任务失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function createTask() {
  if (!form.value.message) {
    ElMessage.warning('请填写任务内容')
    return
  }
  try {
    await scheduleApi.create(form.value)
    ElMessage.success('任务已创建')
    dialogVisible.value = false
    form.value = { name: '', message: '', delaySeconds: 300 }
    loadTasks()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.message || e))
  }
}

async function cancelTask(row) {
  try {
    await scheduleApi.cancel(row.id)
    ElMessage.success('已取消')
    loadTasks()
  } catch (e) {
    ElMessage.error('取消失败: ' + (e.message || e))
  }
}

function statusType(status) {
  const map = { PENDING: 'warning', RUNNING: 'primary', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }
  return map[status] || 'info'
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(loadTasks)
</script>

<style lang="scss" scoped>
.tasks-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  background: #f5f7fa;

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    h3 {
      margin: 0;
      color: #303133;
    }
  }
}
</style>
