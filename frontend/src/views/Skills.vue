<template>
  <div class="skills-page">
    <div class="page-header">
      <h2>Skill 管理</h2>
      <p class="desc">上传 .md / .markdown / .txt 格式的 Skill 文件，启用后会在每次对话时自动注入到 System Prompt 中，让 AI 遵循你自定义的知识与规则。</p>
      <el-upload
        class="uploader"
        :show-file-list="false"
        accept=".md,.markdown,.txt"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
      >
        <el-button type="primary" :icon="Upload" :loading="uploading">上传 Skill</el-button>
        <template #tip>
          <div class="el-upload__tip">仅支持 md / markdown / txt 格式，单个文件建议 ≤ 50KB</div>
        </template>
      </el-upload>
    </div>

    <el-card class="list-card" shadow="never">
      <el-table :data="skills" v-loading="loading" style="width: 100%" stripe>
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">
              <el-icon class="doc-icon"><Document /></el-icon>
              <div>
                <div class="name">{{ row.name }}</div>
                <div class="sub">{{ row.fileName }} · {{ formatSize(row.fileSize) }} · {{ row.contentLength }} 字符</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="row._saving"
              @change="(v) => handleToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.uploadedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">查看</el-button>
            <el-popconfirm title="确认删除此 Skill?" @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 Skill, 点击右上角上传第一个 Skill 吧" />
        </template>
      </el-table>
    </el-card>

    <!-- 查看 Skill 内容的对话框 -->
    <el-dialog v-model="previewVisible" :title="previewing?.name || 'Skill 内容'" width="720px">
      <div class="preview-content">
        <pre>{{ previewContent }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Upload, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import skillApi from '@/api/skill'

const skills = ref([])
const loading = ref(false)
const uploading = ref(false)
const previewVisible = ref(false)
const previewing = ref(null)
const previewContent = ref('')

onMounted(loadSkills)

async function loadSkills() {
  loading.value = true
  try {
    skills.value = await skillApi.list()
  } catch (e) {
    ElMessage.error('加载 Skill 列表失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

function beforeUpload(file) {
  const ok = /\.(md|markdown|txt)$/i.test(file.name)
  if (!ok) {
    ElMessage.error('仅支持 .md / .markdown / .txt 格式')
  }
  return ok
}

async function handleUpload(options) {
  uploading.value = true
  try {
    await skillApi.upload(options.file)
    ElMessage.success('上传成功')
    await loadSkills()
  } catch (e) {
    ElMessage.error('上传失败: ' + (e.message || e))
  } finally {
    uploading.value = false
  }
}

async function handleToggle(row, enabled) {
  row._saving = true
  try {
    await skillApi.toggleEnabled(row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch (e) {
    row.enabled = !enabled
    ElMessage.error('更新失败: ' + (e.message || e))
  } finally {
    row._saving = false
  }
}

async function handleDelete(row) {
  try {
    await skillApi.remove(row.id)
    ElMessage.success('已删除')
    await loadSkills()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.message || e))
  }
}

async function preview(row) {
  previewing.value = row
  previewContent.value = '加载中...'
  previewVisible.value = true
  try {
    const resp = await skillApi.getContent(row.id)
    previewContent.value = resp?.content || '(空内容)'
  } catch (e) {
    previewContent.value = '读取失败: ' + (e.message || e)
  }
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function formatDate(dt) {
  if (!dt) return '-'
  const d = new Date(dt)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style lang="scss" scoped>
.skills-page {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
  background: #f5f7fa;
}

.page-header {
  background: #fff;
  padding: 20px 24px;
  border-radius: 8px;
  margin-bottom: 16px;

  h2 {
    margin: 0 0 8px;
    font-size: 18px;
  }

  .desc {
    color: #909399;
    font-size: 13px;
    margin: 0 0 12px;
  }
}

.list-card {
  border-radius: 8px;
}

.name-cell {
  display: flex;
  align-items: flex-start;
  gap: 12px;

  .doc-icon {
    font-size: 22px;
    color: #409eff;
    margin-top: 2px;
  }

  .name {
    font-size: 14px;
    font-weight: 500;
  }

  .sub {
    color: #909399;
    font-size: 12px;
    margin-top: 2px;
  }
}

.preview-content {
  max-height: 60vh;
  overflow: auto;
  background: #f5f7fa;
  padding: 16px;
  border-radius: 6px;

  pre {
    white-space: pre-wrap;
    word-break: break-word;
    margin: 0;
    font-size: 13px;
    line-height: 1.6;
    color: #303133;
  }
}
</style>
