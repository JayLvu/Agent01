<template>
  <div class="message-item" :class="`message-${message.role}`">
    <div class="avatar">
      <i :class="avatarIcon"></i>
    </div>
    <div class="content">
      <div class="role">{{ roleLabel }}</div>
      <div class="bubble">
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
</style>
