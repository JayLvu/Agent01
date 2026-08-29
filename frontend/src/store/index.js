import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全局状态: 当前会话 ID + 对话历史
 * 注: 实际对话历史在后端 Redis 维护,这里仅缓存当前展示
 */
export const useChatStore = defineStore('chat', () => {
  const sessionId = ref(localStorage.getItem('sessionId') || '')
  const messages = ref([])

  function setSession(id) {
    sessionId.value = id || ''
    localStorage.setItem('sessionId', id || '')
  }

  function addMessage(message) {
    messages.value.push(message)
    messages.value = [...messages.value]
  }

  function updateLastMessage(role, content) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role) {
        messages.value[i].content = content
        messages.value = [...messages.value]
        break
      }
    }
  }

  function appendToLast(role, chunk) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role && messages.value[i].streaming) {
        // Vue 3 响应式: 直接修改 + 数组整体替换做双重兜底,避免某些情况下不刷新
        messages.value[i].content += chunk
        messages.value = [...messages.value]
        break
      }
    }
  }

  function finishStreaming(role) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role && messages.value[i].streaming) {
        messages.value[i].streaming = false
        messages.value = [...messages.value]
        break
      }
    }
  }

  function addToolCall({ toolName, arguments: args, callId, startedAt }) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && m.streaming) {
        if (!m.toolCalls) m.toolCalls = []
        m.toolCalls.push({ toolName, arguments: args, callId, startedAt, pending: true })
        messages.value = [...messages.value]
        break
      }
    }
  }

  function setToolResult({ callId, result, success, durationMs, startedAt, finishedAt }) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && m.toolCalls) {
        const tc = m.toolCalls.find(t => t.callId === callId)
        if (tc) {
          tc.result = result
          tc.success = success
          tc.durationMs = durationMs
          if (startedAt != null) tc.startedAt = startedAt
          tc.finishedAt = finishedAt
          tc.pending = false
          messages.value = [...messages.value]
          break
        }
      }
    }
  }

  /** 附加 token/成本统计到最后一条 assistant 消息 */
  function setUsage(usage) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === 'assistant') {
        messages.value[i].usage = usage
        messages.value[i].model = usage?.model
        messages.value = [...messages.value]
        break
      }
    }
  }

  /** 标记最后一条 assistant 消息为已取消(无内容时补提示) */
  function markCancelled(reason) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant') {
        m.cancelled = true
        m.streaming = false
        if (!m.content) m.content = `⏹️ 已停止生成${reason ? '：' + reason : ''}`
        messages.value = [...messages.value]
        break
      }
    }
  }

  function clearMessages() {
    messages.value = []
  }

  function clearSession() {
    clearMessages()
    setSession('')
  }

  return {
    sessionId,
    messages,
    setSession,
    addMessage,
    updateLastMessage,
    appendToLast,
    finishStreaming,
    addToolCall,
    setToolResult,
    setUsage,
    markCancelled,
    clearMessages,
    clearSession
  }
})
