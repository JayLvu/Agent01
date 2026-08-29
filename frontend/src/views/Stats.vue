<template>
  <div class="stats-page">
    <!-- 汇总卡片 -->
    <div class="summary-cards">
      <div class="stat-card">
        <div class="stat-label">总 Token</div>
        <div class="stat-value">{{ summary.totalTokens ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">输入 Token</div>
        <div class="stat-value">{{ summary.promptTokens ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">输出 Token</div>
        <div class="stat-value">{{ summary.completionTokens ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">调用次数</div>
        <div class="stat-value">{{ summary.calls ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总成本</div>
        <div class="stat-value">{{ formatCost(summary.totalCost) }}</div>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <!-- Token 明细 -->
      <el-tab-pane label="Token 明细" name="usage">
        <el-table :data="recent" stripe style="width: 100%" max-height="520">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="model" label="模型" width="160" />
          <el-table-column prop="promptTokens" label="输入" width="90" />
          <el-table-column prop="completionTokens" label="输出" width="90" />
          <el-table-column prop="totalTokens" label="总计" width="90" />
          <el-table-column label="成本" width="110">
            <template #default="{ row }">{{ formatCost(row.cost) }}</template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 审计日志 -->
      <el-tab-pane label="审计日志" name="audit">
        <div class="audit-filter">
          <el-select v-model="auditAction" placeholder="全部操作" clearable style="width: 160px" @change="loadAudit">
            <el-option label="对话" value="CHAT" />
            <el-option label="工具调用" value="TOOL_CALL" />
            <el-option label="取消" value="CANCEL" />
            <el-option label="定时任务" value="SCHEDULE" />
          </el-select>
          <el-button @click="loadAudit">刷新</el-button>
        </div>
        <el-table :data="auditLogs" stripe style="width: 100%" max-height="480">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="action" label="操作" width="110" />
          <el-table-column prop="toolName" label="工具" width="130" />
          <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import statsApi from '@/api/stats'
import auditApi from '@/api/audit'

const summary = ref({})
const recent = ref([])
const auditLogs = ref([])
const activeTab = ref('usage')
const auditAction = ref('')

async function loadStats() {
  try {
    const data = await statsApi.overview()
    summary.value = data.summary || {}
    recent.value = data.recent || []
  } catch (e) {
    ElMessage.error('加载统计失败: ' + (e.message || e))
  }
}

async function loadAudit() {
  try {
    const params = auditAction.value ? { action: auditAction.value } : {}
    auditLogs.value = await auditApi.logs(params)
  } catch (e) {
    ElMessage.error('加载审计日志失败: ' + (e.message || e))
  }
}

function formatCost(v) {
  if (v == null) return '0'
  return Number(v).toFixed(6)
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadStats()
  loadAudit()
})
</script>

<style lang="scss" scoped>
.stats-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  background: #f5f7fa;

  .summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 16px;
    margin-bottom: 20px;

    .stat-card {
      background: #fff;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      padding: 16px;

      .stat-label {
        font-size: 12px;
        color: #909399;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 22px;
        font-weight: 600;
        color: #303133;
        font-family: 'Consolas', monospace;
      }
    }
  }

  .audit-filter {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }
}
</style>
