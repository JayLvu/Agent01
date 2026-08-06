<template>
  <div class="documents-page">
      <div class="content-wrapper">
        <!-- 上传区 -->
        <el-card class="upload-card" shadow="never">
          <div slot="header">
            <span><i class="el-icon-upload"></i> 上传文档</span>
          </div>
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
            <i class="el-icon-upload-filled"></i>
            <div class="el-upload__text">
              将文件拖到此处,或<em>点击上传</em>
            </div>
            <div class="el-upload__tip" slot="tip">
              支持 PDF / DOCX / TXT / MD 格式,单文件不超过 50MB
            </div>
          </el-upload>
          <div class="upload-actions">
            <el-button
              type="primary"
              icon="el-icon-upload"
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
          <div slot="header" class="list-header">
            <span><i class="el-icon-document"></i> 文档库</span>
            <el-button
              type="text"
              icon="el-icon-refresh"
              @click="loadDocuments"
            >
              刷新
            </el-button>
          </div>

          <el-table
            v-loading="loading"
            :data="documents"
            empty-text="暂无文档,上传后将自动分块并向量化"
            stripe
          >
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80">
              <template slot-scope="{ row }">
                <el-tag :type="typeTag(row.fileType)" size="mini">
                  {{ row.fileType?.toUpperCase() }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
            <el-table-column prop="totalChars" label="字符数" width="100" align="center">
              <template slot-scope="{ row }">
                {{ formatNumber(row.totalChars) }}
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="90" align="center">
              <template slot-scope="{ row }">
                {{ formatSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="uploadedAt" label="上传时间" width="170">
              <template slot-scope="{ row }">
                {{ formatTime(row.uploadedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template slot-scope="{ row }">
                <el-button
                  type="danger"
                  size="mini"
                  icon="el-icon-delete"
                  circle
                  @click="removeDocument(row)"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script>
import documentApi from '@/api/document'

export default {
  name: 'Documents',
  data() {
    return {
      documents: [],
      loading: false,
      uploading: false,
      pendingFile: null
    }
  },
  created() {
    this.loadDocuments()
  },
  methods: {
    async loadDocuments() {
      this.loading = true
      try {
        this.documents = await documentApi.list()
      } catch (e) {
        // 错误已在拦截器中提示
      } finally {
        this.loading = false
      }
    },

    handleFileChange(file) {
      this.pendingFile = file.raw
    },

    handleExceed() {
      this.$message.warning('一次只能上传一个文件,请先移除已选文件')
    },

    async uploadFile() {
      if (!this.pendingFile) return
      this.uploading = true
      try {
        const doc = await documentApi.upload(this.pendingFile)
        this.$message.success(`文档上传成功,共 ${doc.chunkCount} 个分块`)
        this.pendingFile = null
        this.$refs.upload.clearFiles()
        this.loadDocuments()
      } catch (e) {
        // 错误已在拦截器中提示
      } finally {
        this.uploading = false
      }
    },

    async removeDocument(row) {
      try {
        await this.$confirm(
          `确定删除文档「${row.fileName}」?此操作不可恢复。`,
          '删除确认',
          { type: 'warning' }
        )
        await documentApi.remove(row.id)
        this.$message.success('文档已删除')
        this.loadDocuments()
      } catch (e) {
        // 用户取消
      }
    },

    typeTag(type) {
      const map = { pdf: 'danger', docx: 'primary', txt: 'info', md: 'success' }
      return map[type] || 'info'
    },

    formatSize(bytes) {
      if (!bytes) return '-'
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1024 / 1024).toFixed(2) + ' MB'
    },

    formatNumber(n) {
      if (!n && n !== 0) return '-'
      return n.toLocaleString()
    },

    formatTime(t) {
      if (!t) return '-'
      return t.replace('T', ' ').split('.')[0]
    }
  }
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

  ::v-deep .el-card__header {
    padding: 12px 20px;
    font-weight: 500;

    i {
      margin-right: 6px;
      color: #409eff;
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

  span i {
    margin-right: 6px;
    color: #409eff;
  }
}
</style>
