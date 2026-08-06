<template>
  <div class="about-page">
      <el-card shadow="never" class="about-card">
        <h1><i class="el-icon-cpu"></i> AI 对话 Agent</h1>
        <p class="subtitle">基于 Spring Boot 3 + DeepSeek + Vue 2 的智能对话系统</p>

        <el-divider content-position="left">核心功能</el-divider>
        <ul class="features">
          <li><i class="el-icon-chat-dot-round"></i> 多轮对话(同步 + SSE 流式)</li>
          <li><i class="el-icon-document"></i> RAG 文档检索(PDF/Word/TXT/MD)</li>
          <li><i class="el-icon-memory"></i> 对话记忆管理(Redis + TTL)</li>
          <li><i class="el-icon-connection"></i> 标准 RESTful HTTP API</li>
        </ul>

        <el-divider content-position="left">技术栈</el-divider>
        <div class="tech-stack">
          <el-tag size="medium">Spring Boot 3</el-tag>
          <el-tag size="medium" type="success">Vue 2.7</el-tag>
          <el-tag size="medium" type="warning">Element UI</el-tag>
          <el-tag size="medium" type="danger">DeepSeek</el-tag>
          <el-tag size="medium" type="info">Redis</el-tag>
          <el-tag size="medium" type="info">WebClient</el-tag>
        </div>

        <el-divider content-position="left">API 端点</el-divider>
        <ul class="api-list">
          <li><code>POST /api/v1/chat</code> - 同步对话</li>
          <li><code>POST /api/v1/chat/stream</code> - 流式对话(SSE)</li>
          <li><code>DELETE /api/v1/chat/{sessionId}</code> - 清空会话</li>
          <li><code>POST /api/v1/documents</code> - 上传文档</li>
          <li><code>GET /api/v1/documents</code> - 文档列表</li>
          <li><code>DELETE /api/v1/documents/{id}</code> - 删除文档</li>
        </ul>

        <el-divider />
        <p class="footer">© 2026 VanzyLiu · <a href="https://github.com/VanzyLiu/Agent01" target="_blank">GitHub</a></p>
      </el-card>
    </div>
  </div>
</template>

<script>
export default {
  name: 'About'
}
</script>

<style lang="scss" scoped>
.about-page {
  padding: 24px;
  overflow-y: auto;
  height: 100%;
}

.about-card {
  max-width: 800px;
  margin: 0 auto;
  border-radius: 8px;

  h1 {
    font-size: 28px;
    margin: 0 0 8px;
    color: #303133;

    i { color: #409eff; margin-right: 8px; }
  }

  .subtitle {
    color: #909399;
    margin: 0 0 24px;
  }
}

.features, .api-list {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    padding: 8px 0;
    color: #606266;

    i {
      color: #409eff;
      margin-right: 8px;
      width: 18px;
    }

    code {
      background: #f0f2f5;
      padding: 2px 8px;
      border-radius: 3px;
      font-family: 'Consolas', monospace;
      color: #c7254e;
    }
  }
}

.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.footer {
  text-align: center;
  color: #909399;
  font-size: 13px;

  a {
    color: #409eff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}
</style>
