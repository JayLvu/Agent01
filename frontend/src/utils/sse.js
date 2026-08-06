/**
 * SSE 流式请求工具
 *
 * 浏览器原生 EventSource 仅支持 GET,这里用 fetch 实现 POST + SSE
 *
 * 用法:
 *   streamChat(body, {
 *     onToken(token) { ... },
 *     onDone() { ... },
 *     onError(err) { ... }
 *   })
 *
 * @param {Object} body 请求体
 * @param {Object} handlers 回调
 * @returns {AbortController} 可调用 .abort() 中断
 */
export function streamChat(body, { onToken, onDone, onError } = {}) {
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
          const lines = event.split('\n')
          let eventType = 'message'
          let data = ''
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              data += line.slice(5).trim()
            }
          }
          if (eventType === 'token' && data) {
            onToken && onToken(data)
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
