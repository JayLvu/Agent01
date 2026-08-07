import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

/**
 * 全局状态: 当前会话 ID + 对话历史
 * 注: 实际对话历史在后端 Redis 维护,这里仅缓存当前展示
 */
export default new Vuex.Store({
  state: {
    sessionId: localStorage.getItem('sessionId') || '',
    messages: []
  },
  mutations: {
    SET_SESSION(state, sessionId) {
      state.sessionId = sessionId
      localStorage.setItem('sessionId', sessionId)
    },
    ADD_MESSAGE(state, message) {
      state.messages.push(message)
    },
    UPDATE_LAST_MESSAGE(state, { role, content }) {
      // 找到最后一个匹配 role 的消息并更新
      for (let i = state.messages.length - 1; i >= 0; i--) {
        if (state.messages[i].role === role && state.messages[i].streaming) {
          state.messages[i].content = content
          break
        }
      }
    },
    APPEND_TO_LAST(state, { role, chunk }) {
      for (let i = state.messages.length - 1; i >= 0; i--) {
        if (state.messages[i].role === role && state.messages[i].streaming) {
          state.messages[i].content += chunk
          break
        }
      }
    },
    FINISH_STREAMING(state, role) {
      for (let i = state.messages.length - 1; i >= 0; i--) {
        if (state.messages[i].role === role && state.messages[i].streaming) {
          state.messages[i].streaming = false
          break
        }
      }
    },
    /** 在最后一个 streaming assistant 消息上追加工具调用记录 */
    ADD_TOOL_CALL(state, { toolName, arguments: args, callId }) {
      for (let i = state.messages.length - 1; i >= 0; i--) {
        const m = state.messages[i]
        if (m.role === 'assistant' && m.streaming) {
          if (!m.toolCalls) Vue.set(m, 'toolCalls', [])
          m.toolCalls.push({ toolName, arguments: args, callId, pending: true })
          break
        }
      }
    },
    /** 更新工具调用结果 */
    SET_TOOL_RESULT(state, { callId, result, success, durationMs }) {
      for (let i = state.messages.length - 1; i >= 0; i--) {
        const m = state.messages[i]
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
    },
    CLEAR_MESSAGES(state) {
      state.messages = []
    }
  },
  actions: {
    createSession({ commit }, sessionId) {
      commit('SET_SESSION', sessionId || '')
    },
    clearSession({ commit }) {
      commit('CLEAR_MESSAGES')
      commit('SET_SESSION', '')
    }
  },
  getters: {
    sessionId: state => state.sessionId,
    messages: state => state.messages
  }
})
