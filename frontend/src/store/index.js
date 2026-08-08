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
  }

  function updateLastMessage(role, content) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role && messages.value[i].streaming) {
        messages.value[i].content = content
        break
      }
    }
  }

  function appendToLast(role, chunk) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role && messages.value[i].streaming) {
        messages.value[i].content += chunk
        break
      }
    }
  }

  function finishStreaming(role) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      if (messages.value[i].role === role && messages.value[i].streaming) {
        messages.value[i].streaming = false
        break
      }
    }
  }

  function addToolCall({ toolName, arguments: args, callId }) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && m.streaming) {
        if (!m.toolCalls) m.toolCalls = []
        m.toolCalls.push({ toolName, arguments: args, callId, pending: true })
        break
      }
    }
  }

  function setToolResult({ callId, result, success, durationMs }) {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const m = messages.value[i]
      if (m.role === 'assistant' && m.toolCalls) {
        const tc = m.toolCalls.find(t => t.callId === callId)
        if (tc) {
          tc.result = result
          tc.success = success
          tc.durationMs = durationMs
          tc.pending = false
          break
        }
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
    clearMessages,
    clearSession
  }
})
