<template>
  <layout>
    <div class="chat-page">
      <!-- 消息列表区 -->
      <div ref="messageList" class="message-list">
        <div v-if="messages.length === 0" class="empty-state">
          <i class="el-icon-chat-dot-square"></i>
          <p>开始与 AI 助手对话吧!</p>
          <div class="suggestions">
            <el-tag
              v-for="(s, i) in suggestions"
              :key="i"
              class="suggestion-tag"
              @click="sendMessage(s)"
            >
              {{ s }}
            </el-tag>
          </div>
        </div>

        <message-item
          v-for="(msg, idx) in messages"
          :key="idx"
          :message="msg"
        />
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="toolbar">
          <el-tooltip content="清空当前会话" placement="top">
            <el-button
              icon="el-icon-delete"
              size="mini"
              circle
              :disabled="loading || !sessionId"
              @click="clearSession"
            />
          </el-tooltip>
          <el-tooltip :content="streamMode ? '流式模式(逐字输出)' : '同步模式(整段返回)'" placement="top">
            <el-switch
              v-model="streamMode"
              active-color="#409eff"
              inactive-color="#dcdfe6"
              :disabled="loading"
            />
          </el-tooltip>
          <span class="mode-label">{{ streamMode ? '流式' : '同步' }}</span>

          <el-tooltip :content="enableRag ? '已启用 RAG 文档检索' : '未启用 RAG'" placement="top">
            <el-switch
              v-model="enableRag"
              active-color="#67c23a"
              inactive-color="#dcdfe6"
              :disabled="loading"
            />
          </el-tooltip>
          <span class="mode-label">RAG</span>
        </div>

        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :autosize="{ minRows: 2, maxRows: 6 }"
            placeholder="输入消息,Enter 发送,Shift+Enter 换行"
            :disabled="loading"
            @keydown.enter.native.exact.prevent="handleEnter"
          />
          <div class="actions">
            <el-button
              v-if="loading"
              type="danger"
              icon="el-icon-video-pause"
              @click="stopGeneration"
            >
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              icon="el-icon-s-promotion"
              :disabled="!inputText.trim()"
              @click="sendMessage()"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </layout>
</template>

<script>
import Layout from '@/components/Layout.vue'
import MessageItem from '@/components/MessageItem.vue'
import chatApi from '@/api/chat'
import { streamChat } from '@/utils/sse'

export default {
  name: 'Chat',
  components: { Layout, MessageItem },
  data() {
    return {
      inputText: '',
      loading: false,
      streamMode: true,
      enableRag: true,
      suggestions: [
        '你好,请介绍一下自己',
        '用 200 字解释什么是 RAG',
        '帮我写一个冒泡排序',
        'Spring Boot 3 有哪些新特性?'
      ],
      abortController: null
    }
  },
  computed: {
    messages() {
      return this.$store.state.messages
    },
    sessionId() {
      return this.$store.state.sessionId
    }
  },
  watch: {
    messages: {
      handler() {
        this.$nextTick(this.scrollToBottom)
      },
      deep: true
    }
  },
  methods: {
    handleEnter() {
      if (!this.loading) this.sendMessage()
    },

    async sendMessage(presetText) {
      const text = (presetText || this.inputText).trim()
      if (!text || this.loading) return

      // 1. 立即显示用户消息
      this.$store.commit('ADD_MESSAGE', { role: 'user', content: text })
      this.inputText = ''
      this.loading = true

      // 2. 添加占位 assistant 消息(流式模式标记 streaming)
      this.$store.commit('ADD_MESSAGE', {
        role: 'assistant',
        content: '',
        streaming: this.streamMode
      })

      try {
        if (this.streamMode) {
          await this.sendStream(text)
        } else {
          await this.sendSync(text)
        }
      } catch (err) {
        // 错误时把占位消息改为错误提示
        this.$store.commit('UPDATE_LAST_MESSAGE', {
          role: 'assistant',
          content: `⚠️ 对话失败: ${err.message || '未知错误'}`
        })
        this.$store.commit('FINISH_STREAMING', 'assistant')
      } finally {
        this.loading = false
        this.abortController = null
      }
    },

    /** 同步对话 */
    async sendSync(text) {
      const resp = await chatApi.chat({
        sessionId: this.sessionId || undefined,
        message: text,
        enableRag: this.enableRag,
        stream: false
      })
      if (resp.sessionId && resp.sessionId !== this.sessionId) {
        this.$store.commit('SET_SESSION', resp.sessionId)
      }
      this.$store.commit('UPDATE_LAST_MESSAGE', {
        role: 'assistant',
        content: resp.content
      })
      this.$store.commit('FINISH_STREAMING', 'assistant')
    },

    /** 流式对话(SSE) */
    sendStream(text) {
      return new Promise((resolve, reject) => {
        this.abortController = streamChat(
          {
            sessionId: this.sessionId || undefined,
            message: text,
            enableRag: this.enableRag,
            stream: true
          },
          {
            onToken: (token) => {
              this.$store.commit('APPEND_TO_LAST', { role: 'assistant', chunk: token })
            },
            onDone: () => {
              this.$store.commit('FINISH_STREAMING', 'assistant')
              // 若首条流式消息,后端会通过持久化生成 sessionId
              // 这里从首条响应里读取(若后端在 SSE 中带上 sessionId,可在此处理)
              resolve()
            },
            onError: (err) => reject(err)
          }
        )
      })
    },

    /** 停止生成 */
    stopGeneration() {
      if (this.abortController) {
        this.abortController.abort()
      }
    },

    /** 清空会话 */
    async clearSession() {
      try {
        await this.$confirm('确定清空当前会话历史?', '提示', {
          type: 'warning'
        })
        if (this.sessionId) {
          await chatApi.clearSession(this.sessionId)
        }
        this.$store.dispatch('clearSession')
        this.$message.success('会话已清空')
      } catch (e) {
        // 用户取消
      }
    },

    scrollToBottom() {
      const el = this.$refs.messageList
      if (el) el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style lang="scss" scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #909399;

  i {
    font-size: 64px;
    color: #c0c4cc;
  }

  p {
    margin: 16px 0 24px;
    font-size: 15px;
  }

  .suggestions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: center;
    max-width: 600px;
    margin: 0 auto;
  }

  .suggestion-tag {
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
    }
  }
}

.input-area {
  border-top: 1px solid #e6e6e6;
  background: #fff;
  padding: 12px 24px 20px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;

  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;

    .mode-label {
      font-size: 12px;
      color: #909399;
      margin-right: 8px;
    }
  }

  .input-box {
    display: flex;
    gap: 12px;
    align-items: flex-end;

    ::v-deep .el-textarea__inner {
      resize: none;
      border-radius: 8px;
      padding: 10px 14px;
      font-family: inherit;
    }

    .actions {
      .el-button {
        height: 40px;
      }
    }
  }
}
</style>
