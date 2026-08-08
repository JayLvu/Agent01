<template>
  <div class="message-item" :class="`message-${message.role}`">
    <div class="avatar">
      <el-icon><component :is="avatarIcon" /></el-icon>
    </div>
    <div class="content">
      <div class="role">{{ roleLabel }}</div>
      <div class="bubble">
        <!-- 工具调用过程 -->
        <div v-if="message.toolCalls && message.toolCalls.length" class="tool-calls">
          <div v-for="(tc, i) in message.toolCalls" :key="i" class="tool-call-item">
            <div class="tool-call-header">
              <el-icon><component :is="toolIcon(tc.toolName)" /></el-icon>
              <span class="tool-name">{{ tc.toolName }}</span>
              <el-tag size="small" :type="toolStatusType(tc)">{{ toolStatusText(tc) }}</el-tag>
              <span v-if="tc.durationMs" class="tool-duration">{{ tc.durationMs }}ms</span>
            </div>
            <div class="tool-args"><code>{{ tc.arguments }}</code></div>
            <div v-if="tc.result" class="tool-result" :class="{ 'is-error': !tc.success }">{{ tc.result }}</div>
          </div>
        </div>
        <!--
          streaming 期间用纯文本: 避免逐 token 调用 marked.parse 导致光标抖动/跳行
          流式结束(或 user 消息)后再切 markdown 渲染
        -->
        <div
          v-if="message.content && !message.streaming && message.role === 'assistant'"
          class="markdown-body"
          v-html="renderedContent"
        ></div>
        <div v-else-if="message.content" class="text-content">{{ message.content }}</div>
        <span v-if="message.streaming && message.role === 'assistant'" class="cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const avatarIcon = computed(() =>
  props.message.role === 'user' ? 'User' : 'Cpu'
)

const roleLabel = computed(() =>
  props.message.role === 'user' ? '我' : 'AI 助手'
)

const shouldRenderMarkdown = computed(() =>
  props.message.role === 'assistant' && props.message.content
)

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  try {
    return marked.parse(props.message.content, { breaks: true })
  } catch {
    return props.message.content
  }
})

function toolIcon(name) {
  const map = {
    execute_shell: 'Monitor',
    query_datetime: 'Clock',
    calculate: 'Cpu',
    list_files: 'FolderOpened',
    read_file: 'Document',
    write_file: 'EditPen'
  }
  return map[name] || 'Tools'
}

function toolStatusType(tc) {
  if (tc.pending) return 'warning'
  return tc.success ? 'success' : 'danger'
}

function toolStatusText(tc) {
  if (tc.pending) return '执行中'
  return tc.success ? '成功' : '失败'
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

    .el-icon {
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
