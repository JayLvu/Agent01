<template>
  <div class="message-item" :class="`message-${message.role}`">
    <div class="avatar">
      <el-icon><component :is="avatarIcon" /></el-icon>
    </div>
    <div class="content">
      <div class="role">{{ roleLabel }}</div>
      <div class="bubble">
        <!-- 工具调用过程: 可折叠卡片, 执行中默认展开, 完成后默认收起 -->
        <div v-if="message.toolCalls && message.toolCalls.length" class="tool-calls">
          <div
            v-for="(tc, i) in message.toolCalls"
            :key="tc.callId || i"
            class="tool-call-item"
            :class="{ 'is-pending': tc.pending }"
          >
            <div class="tool-call-header" @click="toggleTool(tc, i)">
              <el-icon class="tool-status-icon" :class="{ 'is-loading': tc.pending, 'is-success': !tc.pending && tc.success, 'is-error': !tc.pending && !tc.success }">
                <Loading v-if="tc.pending" />
                <CircleCheck v-else-if="tc.success" />
                <CircleClose v-else />
              </el-icon>
              <el-icon class="tool-kind-icon"><component :is="toolIcon(tc.toolName)" /></el-icon>
              <span class="tool-name">{{ tc.toolName }}</span>
              <el-tag size="small" :type="toolStatusType(tc)" effect="light">{{ toolStatusText(tc) }}</el-tag>
              <span class="tool-time" v-if="tc.startedAt">
                <span class="tool-time-label">开始</span>{{ formatTime(tc.startedAt) }}
              </span>
              <span class="tool-time" v-if="tc.finishedAt">
                <span class="tool-time-label">结束</span>{{ formatTime(tc.finishedAt) }}
              </span>
              <span class="tool-duration" v-if="tc.durationMs != null">耗时 {{ tc.durationMs }}ms</span>
              <el-icon class="tool-expand-icon">
                <ArrowUp v-if="isExpanded(tc, i)" />
                <ArrowDown v-else />
              </el-icon>
            </div>
            <div v-show="isExpanded(tc, i)" class="tool-call-body">
              <div v-if="tc.arguments" class="tool-args">
                <span class="tool-section-label">参数</span>
                <code>{{ tc.arguments }}</code>
              </div>
              <template v-if="!tc.pending">
                <div class="tool-result-head" :class="{ 'is-error': !tc.success }">
                  {{ tc.success ? '✓ 执行成功' : '✗ 执行失败' }}
                </div>
                <div
                  v-if="tc.result"
                  class="tool-result tool-result-md"
                  :class="{ 'is-error': !tc.success }"
                  v-html="renderToolResult(tc)"
                ></div>
              </template>
              <div v-else class="tool-running-hint">
                <el-icon class="is-loading"><Loading /></el-icon> 正在执行…
              </div>
            </div>
          </div>
        </div>
        <!--
          AI 消息: 流式 + 完成均用 markdown 实时渲染, 避免纯文本堆一行
          光标独立放在 markdown 容器外, 不参与 reparse, 避免抖动
        -->
        <div
          v-if="message.content && message.role === 'assistant'"
          class="markdown-body chat-markdown"
          :class="{ 'is-streaming': message.streaming }"
          v-html="renderedContent"
        ></div>
        <!-- 用户消息: 纯文本, 保留换行 + 路径高亮 -->
        <div v-else-if="message.content" class="text-content chat-text" v-html="renderedTextContent"></div>
        <!-- Token/成本统计页脚 -->
        <div v-if="message.role === 'assistant' && message.usage" class="usage-footer">
          <span v-if="message.usage.model || message.model" class="usage-model">模型 {{ message.usage.model || message.model }}</span>
          <span v-if="message.usage.totalTokens" class="usage-item">Tokens {{ message.usage.totalTokens }}</span>
          <span v-if="message.usage.cost != null" class="usage-item">成本 {{ formatCost(message.usage) }}</span>
        </div>
        <span v-if="message.streaming && message.role === 'assistant'" class="cursor">|</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import markdown from 'highlight.js/lib/languages/markdown'
import yaml from 'highlight.js/lib/languages/yaml'
import ini from 'highlight.js/lib/languages/ini'
import plaintext from 'highlight.js/lib/languages/plaintext'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('vue', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sh', bash)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('ini', ini)
hljs.registerLanguage('toml', ini)
hljs.registerLanguage('conf', ini)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)

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

/* =======================
 * marked 扩展: 路径识别 / 代码块美化 / 链接新窗口
 * ======================= */
// 常见路径模式: C:\xxx、/a/b/c、./xxx、../xxx、~/xxx、f:\xxx、相对路径 xxx/yyy.zz(带扩展名或多级)
const PATH_RE = /^([A-Za-z]:[\\/]|\/|~\/|\.\.?\/)[^\s<>?"'|]*[^\s<>?"'|,.，。；;)]|(^|[\s(（,，。；;:])([A-Za-z0-9_\-./]+\/[A-Za-z0-9_\-./]+(\.\w+)?)$/

const renderer = new marked.Renderer()

// 代码块: 带语言标签 + 复制按钮 (marked v12 位置参数: code, infostring, escaped)
renderer.code = function (code, infostring, escaped) {
  const text = code
  const language = (infostring || '').trim()
  const langLabel = language || 'text'
  const mermaid = language === 'mermaid'
  let html = escaped ? text : escapeHtml(text)
  if (hljs && !mermaid) {
    try {
      html = language && hljs.getLanguage(language)
        ? hljs.highlight(text, { language }).value
        : hljs.highlightAuto(text).value
    } catch {
      html = escapeHtml(text)
    }
  }
  if (mermaid) {
    return `<div class="md-code md-code-mermaid" data-lang="mermaid"><div class="md-code-bar"><span class="md-code-lang">Mermaid 流程图</span></div><div class="md-mermaid-body">${html}</div></div>`
  }
  return `<div class="md-code" data-lang="${langLabel}">
    <div class="md-code-bar">
      <span class="md-code-lang">${escapeHtml(langLabel.toUpperCase())}</span>
      <button class="md-code-copy" type="button" onclick="__chatCopyCode(this)" title="复制代码">复制</button>
    </div>
    <pre class="md-code-pre"><code class="hljs ${language ? 'language-' + language : ''}">${html}</code></pre>
  </div>`
}

// 行内代码: 若内容看起来像路径,额外加 .inline-path 样式 (marked v12: codespan(code))
renderer.codespan = function (code) {
  const text = code
  const isPath = /([A-Za-z]:[\\/]|\/|~\/|\.\.?\/)/.test(text) || /^[\w./-]+\.\w+$/.test(text)
  if (isPath) {
    return `<code class="inline-path">${escapeHtml(text)}</code>`
  }
  return `<code>${escapeHtml(text)}</code>`
}

// 链接: 新窗口打开 + 防跟踪 (marked v12: link(href, title, text))
renderer.link = function (href, title, text) {
  const t = title ? ` title="${escapeHtml(title)}"` : ''
  const isPathLike = /^(#|\/|[A-Za-z]:[\\/])/.test(href) || !/^https?:/i.test(href)
  if (isPathLike) {
    return `<a href="${escapeHtml(href)}"${t} class="md-link md-link-path">${text}</a>`
  }
  return `<a href="${escapeHtml(href)}"${t} target="_blank" rel="noopener noreferrer" class="md-link">${text}</a>`
}

// 段落: marked v12 默认已生成 <p>, 只需加 class
// 用 marked.use 的 extensions 方式不兼容, 改为直接覆盖返回值
renderer.paragraph = function (text) {
  return `<p class="md-paragraph">${text}</p>`
}

marked.use({
  renderer,
  gfm: true,
  breaks: true
})

function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 流式/用户消息的纯文本渲染: 保留换行 + 简单的行内路径/code 样式化
const renderedTextContent = computed(() => {
  if (!props.message.content) return ''
  let html = escapeHtml(props.message.content)
  // 1. 行内路径(独立出现): C:\xxx、./xxx、/a/b、../xxx、~/xxx
  html = html.replace(
    /(^|[\s（(,，。；;:])([A-Za-z]:[\\/][^\s<>?"'|,.，。；;:)]+|\/[^\s<>?"'|,.，。；;:)]{2,}|~\/[^\s<>?"'|,.，。；;:)]+|\.\.?\/[^\s<>?"'|,.，。；;:)]+)/g,
    (_m, pre, path) => `${pre}<code class="inline-path inline-path-plain">${path}</code>`
  )
  // 2. 换行 → <br>
  html = html.replace(/\r?\n/g, '<br>')
  return html
})

// 预处理 LLM 输出的不规范 markdown:
// 1. 补全 #/-/数字. 后的空格
// 2. 在同行出现的块级元素(#标题、-列表、```代码围栏)前补换行
// 3. 代码围栏 ``` 紧跟内容时补换行
// 4. 表格独立成段, 保证 GFM table 识别
function preprocessMarkdown(src) {
  if (!src) return ''
  let s = src

  // 1. 标题 #后紧跟非空格非# → 补空格
  s = s.replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3')
  // 2. 无序列表 -/*/+ 后紧跟非空格 → 补空格
  s = s.replace(/(^|\n)([-*+])([^\s*+\-\n])/g, '$1$2 $3')
  // 3. 有序列表 数字. 后紧跟非空格 → 补空格
  s = s.replace(/(^|\n)(\d+\.)([^\s\n])/g, '$1$2 $3')

  // 4. 同行出现的 ## 标题前补换行 (如 "文本##标题" → "文本\n\n## 标题")
  s = s.replace(/([^\n#])(#{1,6}\s)/g, '$1\n\n$2')
  // 5. 同行出现的 - 列表项前补换行 (如 "文本- 列表" → "文本\n- 列表")
  //    注意: 列表标记前不能是另一个 -/*/+，避免误伤表格分隔符 :---| 或 ****
  s = s.replace(/([^\s\n\-*+])(\n?)([-*+]\s)/g, (m, before, nl, list) => {
    if (nl) return m
    return before + '\n' + list
  })
  // 6. 同行出现的有序列表前补换行 (如 "文本1.列表" → "文本\n1. 列表")
  s = s.replace(/([^\s\n])(\n?)(\d+\.\s)/g, (m, before, nl, list) => {
    if (nl) return m
    return before + '\n' + list
  })

  // 7. 代码围栏 ``` 开始标记: 前面有非空内容 → 前面补换行
  s = s.replace(/([^\n`])(```)/g, '$1\n\n$2')
  // 8. 代码围栏 ```language 后紧跟代码内容(同行) → 后面补换行
  s = s.replace(/(```[a-zA-Z0-9+-]*)([^\n])/g, (m, fence, ch) => {
    if (ch === '`') return m
    return fence + '\n' + ch
  })
  // 9. 代码内容后紧跟 ``` 结束标记(同行) → 前面补换行
  s = s.replace(/([^\n`])(```)/g, '$1\n$2')

  // 10. 真正的水平分割线 ---/*** (整行只有这个, 且不位于表格分隔符) 在前一段紧跟时前补空行
  s = s.replace(/([^\n\s])(\n)(-{3,}|\*{3,})(?=\n|$)/g, '$1\n\n$2$3')
  //    注意: 只有当 ---/*** 前面的字符是普通段落字符(不是 |: 冒号/管道 表格分隔)时才补换行
  s = s.replace(/([^\n-\s*:|])(-{3,}|\*{3,})(?=\n|$)/g, '$1\n\n$2')

  // 11. 表格独立成段: 把字符串按行处理, 表格前/后各补空行, 保证 GFM table 识别
  //     同时修复"表格内容和前面的文本处在同一行"的情况
  const lines = s.split('\n')
  const isTblRow = (ln) => /^\s*\|.*\|\s*$/.test(ln) && (ln.match(/\|/g) || []).length >= 2
  const isTblSep = (ln) => /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(ln)
  const isBlank = (ln) => /^\s*$/.test(ln)
  const isFence = (ln) => /^\s*```/.test(ln)
  const out = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const tblR = isTblRow(line)
    const tblS = isTblSep(line)
    if ((tblR || tblS) && out.length > 0) {
      const prev = out[out.length - 1]
      if (!isBlank(prev) && !isTblRow(prev) && !isTblSep(prev) && !isFence(prev)) {
        out.push('')
      }
    }
    // 如果当前行本身"前面有普通文本 + 紧跟一个完整表格行"(同行拼接), 则拆成两行
    let pushed = false
    if (!tblR && !tblS) {
      const m = line.match(/^(.+?[^|\n])(\s*)(\|\s*[^|\n].*\|)\s*$/)
      if (m && (m[3].match(/\|/g) || []).length >= 2) {
        const head = m[1]
        const tblPart = m[3]
        if (/^\|/.test(tblPart)) {
          if (head.trim()) out.push(head)
          // 插入一个空行作为分隔(表格前缀保护)
          if (out.length > 0 && !isBlank(out[out.length - 1])) out.push('')
          out.push(tblPart)
          pushed = true
        }
      }
    }
    if (!pushed) {
      // 表格结束后的"正文第一行"如果紧跟表格, 则在正文前插入一个空行, 避免 marked 把后续文字当成表格的一行
      if (!tblR && !tblS && !isBlank(line) && !isFence(line) && out.length > 0) {
        const prev = out[out.length - 1]
        if (isTblRow(prev) || isTblSep(prev)) {
          out.push('')
        }
      }
      out.push(line)
    }
  }
  s = out.join('\n')

  return s
}

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  try {
    return marked.parse(preprocessMarkdown(props.message.content))
  } catch {
    return escapeHtml(props.message.content).replace(/\r?\n/g, '<br>')
  }
})

// 工具调用结果: 同样走 markdown 渲染, 支持表格、加粗(消除字面星号)、代码块等
function renderToolResult(tc) {
  const src = tc.result
  if (!src) return ''
  try {
    return marked.parse(preprocessMarkdown(src))
  } catch {
    return escapeHtml(src).replace(/\r?\n/g, '<br>')
  }
}

/* =======================
 * 全局: 代码复制(使用onclick直接绑,避免重复监听)
 * ======================= */
if (typeof window !== 'undefined' && !window.__chatCopyCode) {
  window.__chatCopyCode = function (btn) {
    const code = btn.parentElement?.parentElement?.querySelector('pre > code')
    if (!code) return
    const text = code.innerText
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => flashBtn(btn)).catch(() => fallbackCopy(btn, text))
    } else {
      fallbackCopy(btn, text)
    }
  }
  function fallbackCopy(btn, text) {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); flashBtn(btn) } catch {}
    document.body.removeChild(ta)
  }
  function flashBtn(btn) {
    const old = btn.textContent
    btn.textContent = '已复制'
    btn.classList.add('is-copied')
    setTimeout(() => {
      btn.textContent = old
      btn.classList.remove('is-copied')
    }, 1500)
  }
}

function toolIcon(name) {
  const map = {
    execute_shell: 'Monitor',
    query_datetime: 'Clock',
    calculate: 'Cpu',
    list_files: 'FolderOpened',
    read_file: 'Document',
    write_file: 'EditPen',
    web_search: 'Search',
    http_request: 'Connection',
    browse_url: 'View',
    schedule_task: 'AlarmClock'
  }
  return map[name] || 'Tools'
}

function formatCost(usage) {
  if (!usage) return ''
  if (usage.cost == null) return ''
  return usage.cost.toFixed(6) + ' ' + (usage.currency || 'CNY')
}

// 折叠状态: 每个工具调用独立控制
// 默认: 执行中(pending)展开让用户看到进度; 完成后默认收起,减少干扰,点击再展开查看详情
const expandedMap = reactive({})

function toolKey(tc, i) {
  return tc.callId || `idx-${i}`
}

function isExpanded(tc, i) {
  const k = toolKey(tc, i)
  if (k in expandedMap) return expandedMap[k]
  // 完成后默认收起, 仅 pending 展开
  return !!tc.pending
}

function toggleTool(tc, i) {
  const k = toolKey(tc, i)
  expandedMap[k] = !isExpanded(tc, i)
}

// 毫秒时间戳 -> HH:mm:ss.mmm
function formatTime(ts) {
  if (ts == null) return ''
  const d = new Date(ts)
  const pad = (n, w = 2) => String(n).padStart(w, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

function toolStatusType(tc) {
  if (tc.pending) return 'warning'
  return tc.success ? 'success' : 'danger'
}

function toolStatusText(tc) {
  if (tc.pending) return '执行中'
  return '已完成'
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
    max-width: 80%;
  }

  .role {
    font-size: 12px;
    color: #909399;
    margin-bottom: 4px;
    letter-spacing: 0.2px;
  }

  .bubble {
    padding: 14px 18px;
    line-height: 1.6;
    word-wrap: break-word;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
    backdrop-filter: blur(2px);
    transition: box-shadow 0.2s;

    &:hover {
      box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
    }
  }

  .cursor {
    display: inline-block;
    margin-left: 2px;
    animation: blink 1s infinite;
    color: #409eff;
    font-weight: bold;
    vertical-align: middle;
    font-size: 1.1em;
    line-height: 1;
  }

  /* 流式输出时 markdown 容器: 末尾留出光标空间 */
  .markdown-body.is-streaming {
    > *:last-child {
      margin-bottom: 0;
    }
    /* 流式时最后一个段落末尾不加过多间距,让光标紧跟 */
    p:last-child {
      margin-bottom: 2px;
    }
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
  border-left: 3px solid #dcdfe6;
  border-radius: 6px;
  font-size: 12px;
  overflow: hidden;
  transition: border-color 0.2s;

  &.is-pending {
    border-left-color: #e6a23c;
    background: #fdf6ec;
  }

  .tool-call-header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 7px 10px;
    cursor: pointer;
    user-select: none;
    transition: background 0.15s;

    &:hover {
      background: rgba(0, 0, 0, 0.03);
    }

    .tool-status-icon {
      font-size: 15px;
      flex-shrink: 0;

      &.is-loading {
        animation: tool-spin 1s linear infinite;
        color: #e6a23c;
      }

      &.is-success {
        color: #67c23a;
      }

      &.is-error {
        color: #f56c6c;
      }
    }

    .tool-name {
      font-weight: 600;
      color: #303133;
    }

    .tool-time {
      color: #909399;
      font-size: 11px;
      font-family: 'Consolas', 'Menlo', monospace;
      white-space: nowrap;

      .tool-time-label {
        color: #c0c4cc;
        margin-right: 2px;
      }
    }

    .tool-duration {
      margin-left: auto;
      color: #606266;
      font-size: 11px;
      font-weight: 600;
      background: #ecf5ff;
      color: #409eff;
      padding: 0 6px;
      border-radius: 8px;
    }

    .tool-expand-icon {
      margin-left: 4px;
      color: #c0c4cc;
      font-size: 12px;
      transition: transform 0.2s;
    }
  }

  .tool-call-body {
    padding: 0 10px 8px;
    border-top: 1px dashed #ebeef5;
  }

  .tool-section-label {
    display: inline-block;
    font-size: 10px;
    color: #909399;
    background: #f0f2f5;
    padding: 0 4px;
    border-radius: 2px;
    margin-right: 6px;
    vertical-align: middle;
  }

  .tool-args {
    margin-top: 6px;
    color: #606266;

    code {
      display: inline-block;
      background: #ecf5ff;
      color: #409eff;
      padding: 1px 6px;
      border-radius: 3px;
      font-family: 'Consolas', monospace;
      word-break: break-all;
      white-space: pre-wrap;
    }
  }

  .tool-result-head {
    margin-top: 6px;
    font-weight: 600;
    color: #67c23a;
    font-size: 12px;

    &.is-error {
      color: #f56c6c;
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
    max-height: 320px;
    overflow-y: auto;

    &.is-error {
      color: #f56c6c;
      background: #fef0f0;
    }
  }

  // 工具卡片: 走 markdown 渲染后的样式, 与页面 markdown-body 保持一致但更紧凑
  .tool-result-md {
    white-space: normal;
    color: inherit;
    line-height: 1.5;

    :deep(p) { margin: 0 0 4px; &:last-child { margin-bottom: 0; } }
    :deep(strong) { font-weight: 600; color: inherit; }
    :deep(em) { font-style: italic; }
    :deep(code) {
      background: rgba(255,255,255,0.6);
      color: inherit;
      padding: 0 4px;
      border-radius: 2px;
      font-family: 'Consolas', monospace;
    }
    :deep(pre) {
      background: rgba(0,0,0,0.04);
      padding: 6px 8px;
      border-radius: 3px;
      overflow-x: auto;
      margin: 4px 0;
      code { background: transparent; padding: 0; }
    }
    :deep(table) {
      width: 100%;
      border-collapse: collapse;
      margin: 4px 0;
      font-size: 12px;
      color: #303133;
      background: #fff;
      border-radius: 3px;
      overflow: hidden;
      display: block;
      overflow-x: auto;

      th, td {
        border: 1px solid #ebeef5;
        padding: 4px 8px;
        text-align: left;
        vertical-align: top;
        white-space: nowrap;
      }
      th {
        background: #f5f7fa;
        font-weight: 600;
        color: #606266;
      }
      tr:nth-child(even) td {
        background: #fafbfc;
      }
    }
    :deep(ul), :deep(ol) {
      padding-left: 1.4em;
      margin: 2px 0;
    }
    :deep(blockquote) {
      margin: 4px 0;
      padding: 2px 8px;
      border-left: 3px solid #dcdfe6;
      color: #606266;
      background: rgba(255,255,255,0.4);
    }
  }

  .tool-running-hint {
    margin-top: 6px;
    color: #e6a23c;
    display: flex;
    align-items: center;
    gap: 4px;

    .is-loading {
      animation: tool-spin 1s linear infinite;
    }
  }
}

@keyframes tool-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.tool-kind-icon {
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

.usage-footer {
  margin-top: 8px;
  font-size: 11px;
  color: #a8abb2;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  border-top: 1px dashed #ebeef5;
  padding-top: 6px;

  .usage-model {
    font-weight: 600;
    color: #909399;
  }

  .usage-item {
    font-family: 'Consolas', 'Menlo', monospace;
  }
}
</style>
