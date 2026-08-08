<template>
  <div class="documents-page">
      <div class="content-wrapper">
        <!-- 上传区 -->
        <el-card class="upload-card" shadow="never">
          <template #header>
            <span><el-icon><Upload /></el-icon> 上传文档</span>
          </template>
          <el-upload
            ref="upload"
            drag
            action=""
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            accept=".pdf,.docx,.txt,.md"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处,或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF / DOCX / TXT / MD 格式,单文件不超过 50MB
              </div>
            </template>
          </el-upload>
          <div class="upload-actions">
            <el-button
              type="primary"
              :icon="Upload"
              :loading="uploading"
              :disabled="!pendingFile"
              @click="uploadFile"
            >
              上传并解析
            </el-button>
          </div>
        </el-card>

        <!-- 文档列表 -->
        <el-card class="list-card" shadow="never">
          <template #header>
            <div class="list-header">
              <span><el-icon><Document /></el-icon> 文档库</span>
              <el-button
                type="primary"
                link
                :icon="Refresh"
                @click="loadDocuments"
              >
                刷新
              </el-button>
            </div>
          </template>

          <el-table
            v-loading="loading"
            :data="documents"
            empty-text="暂无文档,上传后将自动分块并向量化"
            stripe
          >
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="typeTag(row.fileType)" size="small">
                  {{ row.fileType?.toUpperCase() }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
            <el-table-column prop="totalChars" label="字符数" width="100" align="center">
              <template #default="{ row }">
                {{ formatNumber(row.totalChars) }}
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="90" align="center">
              <template #default="{ row }">
                {{ formatSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="uploadedAt" label="上传时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.uploadedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  size="small"
                  :icon="Delete"
                  circle
                  @click="removeDocument(row)"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Upload, UploadFilled, Document, Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import documentApi from '@/api/document'

const documents = ref([])
const loading = ref(false)
const uploading = ref(false)
const pendingFile = ref(null)
const upload = ref(null)

onMounted(() => {
  loadDocuments()
})

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await documentApi.list()
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    loading.value = false
  }
}

function handleFileChange(file) {
  pendingFile.value = file.raw
}

function handleExceed() {
  ElMessage.warning('一次只能上传一个文件,请先移除已选文件')
}

async function uploadFile() {
  if (!pendingFile.value) return
  uploading.value = true
  try {
    const doc = await documentApi.upload(pendingFile.value)
    ElMessage.success(`文档上传成功,共 ${doc.chunkCount} 个分块`)
    pendingFile.value = null
    upload.value?.clearFiles()
    loadDocuments()
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    uploading.value = false
  }
}

async function removeDocument(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${row.fileName}」?此操作不可恢复。`,
      '删除确认',
      { type: 'warning' }
    )
    await documentApi.remove(row.id)
    ElMessage.success('文档已删除')
    loadDocuments()
  } catch (e) {
    // 用户取消
  }
}

function typeTag(type) {
  const map = { pdf: 'danger', docx: 'primary', txt: 'info', md: 'success' }
  return map[type] || 'info'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function formatNumber(n) {
  if (!n && n !== 0) return '-'
  return n.toLocaleString()
}

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').split('.')[0]
}
</script>

<style lang="scss" scoped>
.documents-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
}

.content-wrapper {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.upload-card, .list-card {
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 12px 20px;
    font-weight: 500;

    .el-icon {
      margin-right: 6px;
      color: #409eff;
      vertical-align: middle;
    }
  }
}

.upload-actions {
  text-align: right;
  margin-top: 16px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  span .el-icon {
    margin-right: 6px;
    color: #409eff;
    vertical-align: middle;
  }
}
</style>
