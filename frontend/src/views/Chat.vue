<template>
  <div class="chat-page">
      <!-- 消息列表区 -->
      <div ref="messageList" class="message-list">
        <div v-if="store.messages.length === 0" class="empty-state">
          <el-icon class="empty-icon"><ChatDotSquare /></el-icon>
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
          v-for="(msg, idx) in store.messages"
          :key="idx"
          :message="msg"
        />
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <!-- 已选附件展示 -->
        <div v-if="attachments.length > 0" class="attachments-bar">
          <div
            v-for="(a, idx) in attachments"
            :key="idx"
            class="attach-item"
          >
            <el-icon class="attach-icon"><Paperclip /></el-icon>
            <span class="attach-name" :title="a.fileName">{{ a.fileName }}</span>
            <span class="attach-size">{{ formatSize(a.fileSize) }}</span>
            <el-icon class="attach-close" @click="removeAttachment(idx)"><Close /></el-icon>
          </div>
        </div>

        <div class="toolbar">
          <el-tooltip content="清空当前会话" placement="top">
            <el-button
              :icon="Delete"
              size="small"
              circle
              :disabled="loading || !store.sessionId"
              @click="clearSession"
            />
          </el-tooltip>

          <!-- 附件上传按钮 -->
          <el-upload
            :show-file-list="false"
            multiple
            accept=".txt,.md,.markdown,.csv,.json,.xml,.yaml,.yml,.pdf,.docx,.xlsx,.pptx,.py,.java,.js,.html,.vue,.log"
            :before-upload="beforeAttachmentUpload"
            class="attach-uploader"
          >
            <el-tooltip content="上传附件(支持 Word/Excel/PDF/PPT/TXT/MD,后端解析文本后拼入消息)" placement="top">
              <el-button :icon="Paperclip" size="small" circle :loading="uploading" />
            </el-tooltip>
          </el-upload>

          <el-tooltip :content="streamMode ? '流式模式(逐字输出)' : '同步模式(整段返回)'" placement="top">
            <el-switch
              v-model="streamMode"
              :disabled="loading"
            />
          </el-tooltip>
          <span class="mode-label">{{ streamMode ? '流式' : '同步' }}</span>

          <el-tooltip :content="enableRag ? '已启用 RAG 文档检索' : '未启用 RAG'" placement="top">
            <el-switch
              v-model="enableRag"
              :disabled="loading"
            />
          </el-tooltip>
          <span class="mode-label">RAG</span>

          <el-tooltip :content="enableTools ? '已启用工具调用(LLM 自主决策)' : '未启用工具调用'" placement="top">
            <el-switch
              v-model="enableTools"
              :disabled="loading"
            />
          </el-tooltip>
          <span class="mode-label">工具</span>
        </div>

        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            :autosize="{ minRows: 2, maxRows: 6 }"
            placeholder="输入消息,Enter 发送,Shift+Enter 换行；可先在工具栏点 📎 附带文件一起分析"
            :disabled="loading"
            @keydown.enter.exact.prevent="handleEnter"
          />
          <div class="actions">
            <el-button
              v-if="loading"
              type="danger"
              :icon="VideoPause"
              @click="stopGeneration"
            >
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :icon="Promotion"
              :disabled="!canSend"
              @click="sendMessage()"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
</template>

<script setup>
import { ref, watch, nextTick, computed } from 'vue'
import { Delete, VideoPause, Promotion, Paperclip, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MessageItem from '@/components/MessageItem.vue'
import chatApi from '@/api/chat'
import { streamChat } from '@/utils/sse'
import { useChatStore } from '@/store'

const store = useChatStore()

const inputText = ref('')
const loading = ref(false)
const uploading = ref(false)
const streamMode = ref(true)
const enableRag = ref(true)
const enableTools = ref(true)
const abortController = ref(null)
const messageList = ref(null)

/** 附件列表: [{fileName, fileType, fileSize, content}] */
const attachments = ref([])

/** 能否发送: 有文字或有附件 */
const canSend = computed(() => !loading.value && (inputText.value.trim() || attachments.value.length > 0))

const suggestions = [
  '现在几点了？',
  '帮我计算 (12+8)*5 等于多少',
  '查看当前目录下有哪些文件',
  '用 200 字解释什么是 RAG'
]

watch(() => store.messages, () => {
  nextTick(scrollToBottom)
}, { deep: true })

function handleEnter() {
  if (!loading.value) sendMessage()
}

/** 上传附件: 发送到后端解析 Word/Excel/PDF/PPT 等格式,提取文本后存入附件列表 */
async function beforeAttachmentUpload(file) {
  uploading.value = true
  try {
    const result = await chatApi.upload(file)
    attachments.value.push({
      fileName: result.fileName,
      fileType: result.fileType,
      fileSize: result.fileSize,
      content: result.content
    })
    ElMessage.success(`已附加文件: ${file.name} (${result.content.length} 字符)`)
  } catch (e) {
    ElMessage.error(`解析文件 ${file.name} 失败: ${e.response?.data?.message || e.message || e}`)
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认上传
}

function removeAttachment(idx) {
  attachments.value.splice(idx, 1)
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

async function sendMessage(presetText) {
  const text = (presetText || inputText.value).trim()
  // 允许仅附带文件 + 空文字
  if (!text && attachments.value.length === 0) return
  if (loading.value) return

  // 1. 立即显示用户消息(若有附件,则在消息里提示)
  const displayText = buildUserDisplayText(text, attachments.value)
  store.addMessage({ role: 'user', content: displayText })
  inputText.value = ''
  const currentAttachments = [...attachments.value]
  attachments.value = []
  loading.value = true

  // 2. 添加占位 assistant 消息(流式模式标记 streaming)
  store.addMessage({
    role: 'assistant',
    content: '',
    streaming: streamMode.value
  })

  try {
    if (streamMode.value) {
      await sendStream(text, currentAttachments)
    } else {
      await sendSync(text, currentAttachments)
    }
  } catch (err) {
    store.updateLastMessage('assistant', `⚠️ 对话失败: ${err.message || '未知错误'}`)
    store.finishStreaming('assistant')
  } finally {
    loading.value = false
    abortController.value = null
  }
}

function buildUserDisplayText(text, atts) {
  if (!atts || atts.length === 0) return text || ''
  let head = `[附加 ${atts.length} 个文件: ` + atts.map(a => a.fileName).join(', ') + ']\n\n'
  return head + (text || '(无额外文字说明)')
}

/** 同步对话 */
async function sendSync(text, atts) {
  const resp = await chatApi.chat({
    sessionId: store.sessionId || undefined,
    message: text || '请分析附件内容',
    enableRag: enableRag.value,
    enableTools: enableTools.value,
    stream: false,
    attachments: atts.length ? atts : undefined
  })
  if (resp.sessionId && resp.sessionId !== store.sessionId) {
    store.setSession(resp.sessionId)
  }
  store.updateLastMessage('assistant', resp.content)
  store.finishStreaming('assistant')
}

/** 流式对话(SSE) */
function sendStream(text, atts) {
  return new Promise((resolve, reject) => {
    abortController.value = streamChat(
      {
        sessionId: store.sessionId || undefined,
        message: text || '请分析附件内容',
        enableRag: enableRag.value,
        enableTools: enableTools.value,
        stream: true,
        attachments: atts.length ? atts : undefined
      },
      {
        onSession: (sessionId) => {
          if (sessionId && sessionId !== store.sessionId) {
            store.setSession(sessionId)
          }
        },
        onToken: (token) => {
          store.appendToLast('assistant', token)
        },
        onToolCall: (info) => {
          store.addToolCall({
            toolName: info.toolName,
            arguments: info.arguments,
            callId: info.callId,
            startedAt: info.startedAt
          })
        },
        onToolResult: (info) => {
          store.setToolResult({
            callId: info.callId,
            result: info.result,
            success: info.success,
            durationMs: info.durationMs,
            startedAt: info.startedAt,
            finishedAt: info.finishedAt
          })
        },
        onDone: () => {
          store.finishStreaming('assistant')
          resolve()
        },
        onError: (err) => reject(err)
      }
    )
  })
}

/** 停止生成 */
function stopGeneration() {
  if (abortController.value) {
    abortController.value.abort()
  }
}

/** 清空会话 */
async function clearSession() {
  try {
    await ElMessageBox.confirm('确定清空当前会话历史?', '提示', {
      type: 'warning'
    })
    if (store.sessionId) {
      await chatApi.clearSession(store.sessionId)
    }
    store.clearSession()
    ElMessage.success('会话已清空')
  } catch (e) {
    // 用户取消
  }
}

function scrollToBottom() {
  const el = messageList.value
  if (el) el.scrollTop = el.scrollHeight
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

  .empty-icon {
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

  .attachments-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 10px;

    .attach-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: #ecf5ff;
      color: #409eff;
      border: 1px solid #d9ecff;
      padding: 4px 8px 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      max-width: 320px;

      .attach-icon {
        font-size: 14px;
      }

      .attach-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 180px;
      }

      .attach-size {
        color: #909399;
      }

      .attach-close {
        cursor: pointer;
        font-size: 14px;
        color: #909399;
        border-radius: 50%;
        padding: 2px;

        &:hover {
          color: #f56c6c;
          background: rgba(245, 108, 108, 0.1);
        }
      }
    }
  }

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

  .attach-uploader {
    display: inline-flex;
  }

  .input-box {
    display: flex;
    gap: 12px;
    align-items: flex-end;

    :deep(.el-textarea__inner) {
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
