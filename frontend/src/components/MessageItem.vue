<template>
  <div class="message-item" :class="`message-${message.role}`">
    <div class="avatar">
      <i :class="avatarIcon"></i>
    </div>
    <div class="content">
      <div class="role">{{ roleLabel }}</div>
      <div class="bubble">
        <!-- 工具调用过程 -->
        <div v-if="message.toolCalls && message.toolCalls.length" class="tool-calls">
          <div v-for="(tc, i) in message.toolCalls" :key="i" class="tool-call-item">
            <div class="tool-call-header">
              <i :class="toolIcon(tc.toolName)"></i>
              <span class="tool-name">{{ tc.toolName }}</span>
              <el-tag size="mini" :type="toolStatusType(tc)">{{ toolStatusText(tc) }}</el-tag>
              <span v-if="tc.durationMs" class="tool-duration">{{ tc.durationMs }}ms</span>
            </div>
            <div class="tool-args"><code>{{ tc.arguments }}</code></div>
            <div v-if="tc.result" class="tool-result" :class="{ 'is-error': !tc.success }">{{ tc.result }}</div>
          </div>
        </div>
        <div v-if="shouldRenderMarkdown" class="markdown-body" v-html="renderedContent"></div>
        <div v-else class="text-content">{{ message.content }}</div>
        <span v-if="message.streaming" class="cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script>
import { marked } from 'marked'

export default {
  name: 'MessageItem',
  props: {
    message: {
      type: Object,
      required: true
    }
  },
  computed: {
    avatarIcon() {
      return this.message.role === 'user' ? 'el-icon-user' : 'el-icon-cpu'
    },
    roleLabel() {
      return this.message.role === 'user' ? '我' : 'AI 助手'
    },
    // assistant 回复用 Markdown 渲染,user 消息纯文本
    shouldRenderMarkdown() {
      return this.message.role === 'assistant' && this.message.content
    },
    renderedContent() {
      if (!this.message.content) return ''
      try {
        return marked.parse(this.message.content, { breaks: true })
      } catch {
        return this.message.content
      }
    }
  },
  methods: {
    toolIcon(name) {
      const map = {
        execute_shell: 'el-icon-monitor',
        query_datetime: 'el-icon-time',
        calculate: 'el-icon-cpu',
        list_files: 'el-icon-folder-opened',
        read_file: 'el-icon-document',
        write_file: 'el-icon-edit-outline'
      }
      return map[name] || 'el-icon-s-tools'
    },
    toolStatusType(tc) {
      if (tc.pending) return 'warning'
      return tc.success ? 'success' : 'danger'
    },
    toolStatusText(tc) {
      if (tc.pending) return '执行中'
      return tc.success ? '成功' : '失败'
    }
  }
}
</script>

<style lang="scss" scoped>
.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;

  &.message-user {
    flex-direction: row-reverse;

    .content {
      align-items: flex-end;
    }

    .bubble {
      background: #409eff;
      color: #fff;
      border-radius: 12px 2px 12px 12px;
    }
  }

  &.message-assistant {
    .bubble {
      background: #fff;
      color: #303133;
      border: 1px solid #ebeef5;
      border-radius: 2px 12px 12px 12px;
    }
  }

  .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #f0f2f5;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    font-size: 18px;
    color: #606266;
  }

  .message-user .avatar {
    background: #409eff;
    color: #fff;
  }

  .content {
    display: flex;
    flex-direction: column;
    max-width: 75%;
  }

  .role {
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
  }

  .bubble {
    padding: 10px 14px;
    line-height: 1.6;
    word-wrap: break-word;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  }

  .cursor {
    display: inline-block;
    margin-left: 2px;
    animation: blink 1s infinite;
    color: #409eff;
    font-weight: bold;
  }

  @keyframes blink {
    0%, 50% { opacity: 1; }
    51%, 100% { opacity: 0; }
  }
}

.tool-calls {
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call-item {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;

  .tool-call-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;

    i {
      color: #e6a23c;
    }

    .tool-name {
      font-weight: 600;
      color: #303133;
    }

    .tool-duration {
      margin-left: auto;
      color: #909399;
      font-size: 11px;
    }
  }

  .tool-args {
    color: #606266;
    code {
      background: #ecf5ff;
      color: #409eff;
      padding: 1px 6px;
      border-radius: 3px;
      font-family: 'Consolas', monospace;
    }
  }

  .tool-result {
    margin-top: 4px;
    color: #67c23a;
    background: #f0f9eb;
    padding: 4px 6px;
    border-radius: 3px;
    word-break: break-all;
    white-space: pre-wrap;
    max-height: 120px;
    overflow-y: auto;

    &.is-error {
      color: #f56c6c;
      background: #fef0f0;
    }
  }
}
</style>
