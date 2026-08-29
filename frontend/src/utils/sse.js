/**
 * SSE 流式请求工具
 *
 * 浏览器原生 EventSource 仅支持 GET,这里用 fetch 实现 POST + SSE
 *
 * 用法:
 *   streamChat(body, {
 *     onSession({sessionId, requestId}) { ... },  // 首个事件,携带 sessionId + requestId
 *     onToken(token) { ... },              // 逐 token 回调
 *     onToolCall(info) { ... },            // LLM 决定调用工具 {toolName,arguments,callId}
 *     onToolResult(info) { ... },          // 工具执行结果 {toolName,result,success,durationMs}
 *     onUsage(info) { ... },               // token/成本统计 {model,promptTokens,...,cost}
 *     onCancelled(reason) { ... },         // 用户停止生成
 *     onDone() { ... },                    // 流结束
 *     onError(err) { ... }                 // 异常
 *   })
 *
 * @param {Object} body 请求体
 * @param {Object} handlers 回调
 * @returns {AbortController} 可调用 .abort() 中断
 */
export function streamChat(body, { onSession, onToken, onToolCall, onToolResult, onUsage, onCancelled, onDone, onError } = {}) {
  const controller = new AbortController()

  fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(body),
    signal: controller.signal
  })
    .then(async response => {
      if (!response.ok) {
        const text = await response.text()
        throw new Error(text || `HTTP ${response.status}`)
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // SSE 以 \n\n 分隔事件
        const events = buffer.split('\n\n')
        buffer = events.pop() // 最后一段可能不完整,保留

        for (const event of events) {
          // 按 \n 拆行,但 data 行的换行需要保留
          // SSE 规范: 每行以 "field:" 开头, data 字段值可能跨多行(每行一个 data:)
          let eventType = 'message'
          const dataParts = []
          for (const line of event.split('\n')) {
            if (line.startsWith('event:')) {
              eventType = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              // data: 后面的内容保留原始值,只去掉 "data:" 前缀和紧跟的一个空格(SSE 规范)
              // 不 trim,否则 token 中的前导/尾随空格和换行会丢失
              let val = line.slice(5)
              if (val.startsWith(' ')) val = val.slice(1) // SSE 规范: data: 后可有一个空格
              dataParts.push(val)
            }
          }
          // 多个 data: 行按 SSE 规范用 \n 拼接; 单行 data 直接取值
          const data = dataParts.join('\n')
          if (eventType === 'session' && data) {
            onSession && onSession(safeParse(data))
          } else if (eventType === 'token' && data) {
            onToken && onToken(data)
          } else if (eventType === 'tool_call' && data) {
            onToolCall && onToolCall(safeParse(data))
          } else if (eventType === 'tool_result' && data) {
            onToolResult && onToolResult(safeParse(data))
          } else if (eventType === 'usage' && data) {
            onUsage && onUsage(safeParse(data))
          } else if (eventType === 'cancelled' && data) {
            onCancelled && onCancelled(data)
          } else if (eventType === 'done') {
            onDone && onDone()
            return
          } else if (eventType === 'error') {
            throw new Error(data || '服务器错误')
          }
        }
      }
      onDone && onDone()
    })
    .catch(err => {
      if (err.name === 'AbortError') {
        // 用户主动中断,不视为错误
        onDone && onDone()
        return
      }
      onError && onError(err)
    })

  return controller
}

function safeParse(str) {
  try {
    return JSON.parse(str)
  } catch {
    return { raw: str }
  }
}
