# Agent01 · AI 对话 Agent

> 基于 Spring Boot 3 + DeepSeek 大模型 + Vue 3 的私有化 AI 对话 Agent，内置 **LLM 工具调用（Function Calling）框架**、**流式响应（SSE）**、**对话记忆（Redis）**、**RAG 文档检索（向量搜索）**、**Skill 知识注入**、**对话附件**六大核心能力；并在此基础上扩展了 **多模型路由**、**并行工具调用**、**停止生成（真正取消）**、**记忆摘要压缩**、**联网搜索 / HTTP 请求 / 浏览器自动化 / 定时任务**工具、**Token 成本统计** 与 **审计日志**。

***

## ✨ 功能特性

### 🤖 核心对话

- **DeepSeek 大模型对接**：官方原生 API，支持 deepseek-chat / deepseek-reasoner / deepseek-v4-pro 等模型
- **SSE 流式响应**：边生成边输出，打字机效果（前端通过 `EventSource` 实时消费）
- **同步响应接口**：同时提供同步接口，方便非流式场景调用
- **多轮对话记忆**：基于 Redis 保存会话上下文，默认保留最近 20 轮，TTL 24 小时
- **对话附件**：在输入框上传文本文件（md/txt/csv/json/py/java 等），前端本地读取内容后随消息一起发送，后端自动把附件内容以结构化格式拼到用户消息前，让 LLM 直接分析文件内容

### 🛠️ LLM 工具调用框架（Function Calling）

LLM 可以自主决策、编排、链式调用以下工具，循环执行直到拿到最终答案（`while` 无限循环，退出条件：LLM 不再返回 tool\_calls）：

| 工具名              | 类名             | 功能说明                                                   |
| ---------------- | -------------- | ------------------------------------------------------ |
| `calculate`      | CalculatorTool | 数学表达式求值（支持 +、-、\*、/、括号、小数、幂运算）                         |
| `query_datetime` | DateTimeTool   | 查询当前日期时间，支持自定义格式与时区偏移                                  |
| `execute_shell`  | ShellTool      | 在本机执行 Shell / PowerShell / CMD 命令并返回输出                 |
| `list_files`     | FileListTool   | 列出 workspace 沙箱内的文件与目录（支持递归）                           |
| `read_file`      | FileReadTool   | 读取沙箱内的文本文件（txt/md/json/csv/yaml/xml/py/java/js/html 等） |
| `write_file`     | FileWriteTool  | 在沙箱内创建/覆盖/追加文本文件，自动创建父目录                               |
| `web_search`     | WebSearchTool  | 联网搜索实时信息（DuckDuckGo 免费 / Serper 需 Key）                    |
| `http_request`   | HttpRequestTool| 调用外部 HTTP API（GET/POST/PUT/DELETE，自定义 header/body）         |
| `browse_url`     | BrowserTool    | 抓取网页并提取标题、描述与正文（jsoup 实现）                              |
| `schedule_task`  | ScheduleTool   | 创建延迟执行的定时任务/提醒，由调度器到期执行                              |

**工具调用安全机制**：

- **文件沙箱**：所有文件操作路径强制相对 `./workspace` 根目录，路径规范化 + `startsWith(base)` 前缀校验，禁止 `../` 越权；尝试访问沙箱外部会被拦截并返回"路径越权"错误
- **文件大小限制**：单次读取 ≤ 200KB，单次写入 ≤ 500KB（可配置）
- **文件格式过滤**：仅允许读取文本扩展名，拒绝二进制文件
- **工具可插拔**：新增工具只需实现 `Tool` 接口 + `@Component` 注解，`ToolRegistry` 自动扫描注册，无需其他配置

### 📚 RAG 文档检索增强生成

- **上传文档**：支持 PDF（PDFBox）、Word/Excel（POI）、纯文本格式，最大 50MB
- **文档分块**：按字符切分，默认 500 字符/块，100 字符重叠
- **向量存储**：基于 Redis 的轻量向量索引（无需额外向量数据库），向量维度 256
- **相似检索**：Top-K=4 检索最相关片段，拼入 LLM 系统提示词
- **文档管理页**：前端可视化上传、列表、删除文档

### 🧠 Skill 知识注入

- **Skill 上传**：支持 `.md` / `.markdown` / `.txt` 格式的 Skill 文件，通过前端管理页或 API 上传
- **自动注入**：每次对话时，后端自动读取所有已启用的 Skill 内容，以 `# 用户自定义 Skill` 标题拼接到 System Prompt 中，让 LLM 遵循自定义的知识与规则
- **启用/禁用**：每个 Skill 可独立开关，禁用后不注入，不影响已上传的文件
- **Skill 管理页**：前端可视化上传、列表、查看内容、启用/禁用、删除
- **元数据存储**：Skill 元数据存 Redis，文件内容存磁盘（`./data/skills/`）

### 📎 对话附件

- **文件上传**：在对话框工具栏点击 📎 按钮选择文件，支持多选
- **支持格式**：md/markdown/txt/csv/json/xml/yaml/yml/py/java/js/html/vue/log 等文本格式
- **本地读取**：前端用 `FileReader.readAsText` 在浏览器本地读取文件内容，不上传二进制文件到服务器
- **内容注入**：后端把附件内容以 `附件[N]: 文件名 + 内容块 + 用户问题` 格式拼到用户消息前，超 200KB 自动截断
- **消息气泡展示**：用户消息气泡中显示 `[附加 N 个文件: a.md, b.csv]`，便于确认上下文

### 🚀 进阶能力（2026 增强）

除上述六大核心能力外，本版本还内置以下工程化能力：

| 能力 | 说明 | 相关配置 |
| --- | --- | --- |
| **多模型路由** | 按请求显式指定 / 关键词 / reasoning 标记自动选择模型（如普通问题走 chat、推理问题走 reasoner） | `agent.router.*` |
| **流式工具调用** | 工具调用模式下 LLM 仍逐 token 流式输出，参数跨 chunk 拼接 | 内置 |
| **并行工具调用** | 一轮中多个无依赖 tool_calls 并发执行，结果按序回传 | `agent.tools.parallel` |
| **停止生成（真正取消）** | 前端停止时经 `requestId` 通知后端，`takeUntilOther` 真正中断底层 LLM 请求 | `POST /chat/cancel/{requestId}` |
| **记忆摘要压缩** | 历史消息数超阈值时自动把更早对话压成摘要，避免上下文膨胀 | `agent.memory.summarize-*` |
| **Token / 成本统计** | 记录每次调用 token 与成本（按配置单价），提供汇总与明细 | `agent.cost.*`、`/stats/*` |
| **审计日志** | 记录对话、工具调用、取消、定时任务等关键操作，落库可查 | `/audit/logs` |
| **联网搜索工具** | `web_search`：DuckDuckGo（免费）或 Serper（需 Key） | `agent.tools.search.*` |
| **HTTP 请求工具** | `http_request`：调用任意外部 REST API | 内置 |
| **浏览器自动化** | `browse_url`：抓取网页标题/描述/正文（jsoup，轻量） | `agent.tools.browser.*` |
| **定时任务工具** | `schedule_task`：创建延迟执行任务，调度器到期执行 | `agent.tools.scheduler.*` |

> 说明：工具调用循环已加 `agent.tools.max-iterations` 上限，防止模型陷入死循环；`browse_url` 为轻量网页抓取，如需点击/填表/截图等完整浏览器自动化，可基于 `Tool` 接口替换为 Playwright/Selenium 实现。

### 🎨 前端界面（Vue 3 + Element Plus）

- 四个模块：**对话页 / 文档管理 / Skill 管理 / 关于**
- 全部组件采用 `<script setup>` Composition API 风格
- 对话页：
  - 气泡式消息流，支持 Markdown 渲染（marked）
  - 工具调用卡片：图标 → 输入参数 → 执行中 loading → 成功/失败结果（可折叠展开）
  - 流式 Token 实时追加，支持取消生成
  - 多 Session 会话切换，新会话按钮
  - **附件上传**：工具栏 📎 按钮，支持多选文本文件，附件展示栏可逐个移除
- Skill 管理页：上传 Skill 文件、列表表格、启用/禁用开关、查看内容弹窗、删除确认
- 开发环境：通过 Vite `server.proxy` 把 `/api` 代理到后端 `8080`，解决跨域
- 生产构建：Vite 构建输出到 `frontend/dist/`，可由 Nginx 托管或放到 Spring Boot static 目录

***

## 🏗️ 技术栈

### 后端

| 模块        | 选型                                | 版本         |
| --------- | --------------------------------- | ---------- |
| 语言        | Java                              | 21         |
| 框架        | Spring Boot                       | 3.2.5      |
| Web/HTTP  | Spring Web + WebFlux (WebClient)  | —          |
| 参数校验      | Spring Validation (Jakarta)       | —          |
| 缓存/记忆/向量库 | Redis (Spring Data Redis + Jedis) | —          |
| JSON      | Jackson                           | 2.16.1     |
| PDF 解析    | Apache PDFBox                     | 3.0.2      |
| Office 解析 | Apache POI OOXML                  | 5.2.5      |
| 工具类       | Hutool + Apache Commons Lang3     | 5.8.27 / — |
| 代码简化      | Lombok                            | —          |
| 构建        | Maven                             | 3.9+       |

### 前端

| 模块       | 选型                                     | 版本           |
| -------- | -------------------------------------- | ------------ |
| 语言       | JavaScript (ES6+)                      | —            |
| 框架       | Vue 3（Composition API）                 | 3.4+         |
| UI       | Element Plus + @element-plus/icons-vue | 2.7+         |
| 路由       | Vue Router                             | 4.3+         |
| 状态管理     | Pinia                                  | 2.1+         |
| HTTP     | Axios                                  | 1.6.8        |
| Markdown | marked                                 | 12.0.1       |
| CSS      | Sass / SCSS                            | 1.72.0       |
| 构建       | Vite + @vitejs/plugin-vue              | 5.2+         |
| 运行时      | Node.js                                | ≥ 16（推荐 18+） |

***

## 📂 项目结构

```
Agent01/
├── backend (以项目根目录作为 Spring Boot 工程根)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/vanzy/agent/
│   │   │   │   ├── AgentApplication.java              # 启动类
│   │   │   │   ├── client/
│   │   │   │   │   └── DeepSeekClient.java            # DeepSeek HTTP 封装(同步/流式 + 多模型 + usage)
│   │   │   │   ├── routing/
│   │   │   │   │   └── ModelRouter.java               # 多模型路由(关键词/显式/reasoning)
│   │   │   │   ├── persistence/                       # JPA 实体 + 仓库(审计/统计/定时任务)
│   │   │   │   │   ├── AuditLog.java / AuditLogRepository.java
│   │   │   │   │   ├── TokenUsageRecord.java / TokenUsageRepository.java
│   │   │   │   │   └── ScheduledTask.java / ScheduledTaskRepository.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── AgentProperties.java           # agent.* 配置绑定(记忆/RAG/工具/文件沙箱/Skill)
│   │   │   │   │   ├── DeepSeekProperties.java        # deepseek.* 配置绑定
│   │   │   │   │   ├── RedisConfig.java               # Redis 序列化配置
│   │   │   │   │   ├── WebClientConfig.java           # WebClient 超时+连接池
│   │   │   │   │   └── WebMvcConfig.java              # 全局 CORS + UTF-8 响应编码
│   │   │   │   ├── controller/
│   │   │   │   │   ├── ChatController.java            # /chat 同步 /chat/stream SSE /cancel /clear
│   │   │   │   │   ├── DocumentController.java        # /documents 上传/列表/删除
│   │   │   │   │   ├── SkillController.java           # /skills 上传/列表/切换/删除/读取内容
│   │   │   │   │   ├── StatsController.java           # /stats token/成本统计
│   │   │   │   │   ├── AuditController.java           # /audit 审计日志
│   │   │   │   │   ├── ScheduleController.java        # /schedules 定时任务管理
│   │   │   │   │   └── HealthController.java          # /health 健康检查
│   │   │   │   ├── exception/                         # 自定义异常 + 全局处理
│   │   │   │   ├── model/
│   │   │   │   │   ├── ChatMessage.java               # 统一消息结构(含 tool_calls)
│   │   │   │   │   ├── ChatRequest.java               # 入参(sessionId/message/enableTools/enableRag/stream/attachments)
│   │   │   │   │   ├── ChatResponse.java              # 同步响应
│   │   │   │   │   ├── DeepSeekDtos.java              # DeepSeek 请求/响应 DTO
│   │   │   │   │   ├── Document.java / DocumentChunk.java
│   │   │   │   │   ├── AttachmentFile.java            # 对话附件 DTO(fileName/fileType/fileSize/content)
│   │   │   │   │   ├── Skill.java                     # Skill 元数据实体(id/name/fileName/enabled/uploadedAt)
│   │   │   │   │   └── StreamEvent.java               # 流式事件(Token/ToolCall/ToolResult/Error)
│   │   │   │   ├── rag/
│   │   │   │   │   ├── DocumentService.java / impl    # 文档上传解析分块
│   │   │   │   │   ├── RagService.java / impl         # 检索增强,拼入上下文
│   │   │   │   │   ├── VectorStore.java / impl Redis  # 向量增删查
│   │   │   │   │   └── TextVectorizer.java            # 文本向量化(n-gram)
│   │   │   │   ├── service/
│   │   │   │   │   ├── ChatService.java / impl        # 核心对话(同步+流式+工具循环+路由+摘要+统计+审计)
│   │   │   │   │   ├── MemoryService.java / Redis     # 会话记忆存取 + 摘要压缩
│   │   │   │   │   ├── AuditLogService.java           # 审计日志
│   │   │   │   │   ├── TokenUsageService.java         # Token/成本统计
│   │   │   │   │   ├── CancellationRegistry.java      # 取消信号注册中心
│   │   │   │   │   ├── ScheduledTaskService.java      # 定时任务 CRUD
│   │   │   │   │   └── ScheduledTaskRunner.java       # 定时任务调度执行
│   │   │   │   ├── skill/
│   │   │   │   │   ├── SkillService.java              # Skill 服务接口
│   │   │   │   │   └── impl/SkillServiceImpl.java     # 上传/列表/启用禁用/删除/读取/构建Prompt
│   │   │   │   └── tool/
│   │   │   │       ├── Tool.java                      # 工具统一接口
│   │   │   │       ├── ToolRegistry.java              # 自动注册 + JSON Schema 生成
│   │   │   │       ├── ToolResult.java                # 工具执行结果(success/content/duration/error/callId)
│   │   │   │       └── impl/                          # 10 个内置工具实现(含搜索/HTTP/浏览/定时)
│   │   │   └── resources/
│   │   │       ├── application.yml                    # 主配置(含 UTF-8 编码 + Skill 配置)
│   │   │       └── application-dev.yml                # 开发环境覆盖
│   │   └── test/                                      # 单元测试(向量化 smoke test)
│   └── pom.xml
│
├── frontend/
│   ├── index.html                                     # Vite 入口 HTML
│   ├── src/
│   │   ├── api/
│   │   │   ├── request.js                             # Axios 实例(带拦截器)
│   │   │   ├── chat.js                                # 对话接口封装(含取消)
│   │   │   ├── document.js                            # 文档接口封装
│   │   │   ├── skill.js                               # Skill 接口封装
│   │   │   ├── schedule.js                            # 定时任务接口封装
│   │   │   ├── stats.js                               # 统计接口封装
│   │   │   └── audit.js                               # 审计接口封装
│   │   ├── assets/styles/global.scss
│   │   ├── components/
│   │   │   ├── Layout.vue                             # 左侧菜单 + 头部 + 主区域 (<script setup>)
│   │   │   └── MessageItem.vue                        # 单条消息 + 工具调用卡片渲染
│   │   ├── router/index.js                            # createRouter /chat /documents /skills /about
│   │   ├── store/index.js                             # Pinia 全局状态(消息/会话)
│   │   ├── utils/sse.js                               # SSE EventSource 封装(Token/工具事件解析)
│   │   ├── views/
│   │   │   ├── Chat.vue                               # 对话主页: 输入框 + 附件上传 + 消息列表 + 流式渲染 + 停止
│   │   │   ├── Documents.vue                          # 文档上传/列表/删除
│   │   │   ├── Skills.vue                             # Skill 上传/列表/启用禁用/查看/删除
│   │   │   ├── Tasks.vue                              # 定时任务列表/新建/取消
│   │   │   ├── Stats.vue                              # Token/成本统计 + 审计日志
│   │   │   └── About.vue
│   │   ├── App.vue
│   │   └── main.js                                    # createApp + Element Plus 全局注册
│   ├── vite.config.js                                 # Vite 配置(代理/别名/构建)
│   ├── package.json
│   └── .gitignore
│
├── workspace/                 # 文件工具沙箱(运行时生成,不提交 Git)
├── data/docs/                 # 文档上传存储目录
├── data/vector/               # 向量索引持久化
├── data/skills/               # Skill 文件存储目录(运行时生成,不提交 Git)
├── logs/                      # 后端日志目录(agent.log,运行时生成)
├── .env.example               # 环境变量模板
├── .gitignore                 # 已排除 target/ node_modules/ workspace/ data/skills/ logs/ 等
└── README.md                  # 本文件
```

***

## 🚀 快速开始

### 环境要求

- JDK **21** (`java -version`)
- Maven **3.9+** (`mvn -v`)
- Node.js **≥ 16**（推荐 18+）(`node -v`)
- Redis **6+**（需支持 RediSearch 模块时建议 7+，纯内存回退也能跑）
- DeepSeek API Key：[申请入口](https://platform.deepseek.com/)

### 1. 克隆 & 配置

```bash
git clone https://github.com/JayLvu/Agent01.git
cd Agent01
git checkout dev
```

复制环境变量模板（Windows 手动新建 `.env` 或直接设置系统环境变量即可；Spring Boot 会自动从环境变量读取）：

```bash
cp .env.example .env
# 编辑 .env 填你的 DEEK_SEEK_API_KEY 等
```

**必填项**：

```bash
DEEK_SEEK_API_KEY=sk-your-deepseek-api-key-here
```

**推荐按需填**：

```bash
DEEK_SEEK_MODEL=deepseek-v4-pro     # 如需大模型推理能力
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 2. 启动 Redis

方式一：Docker

```bash
docker run -d --name redis-agent -p 6379:6379 redis:7-alpine
```

方式二：Windows 本地安装版直接启动 `redis-server`

确认 Redis 通：

```bash
redis-cli ping   # 返回 PONG
```

### 3. 启动后端

```bash
# 设置 API Key（PowerShell 示例）
$env:DEEK_SEEK_API_KEY = "sk-xxx"
$env:DEEK_SEEK_MODEL   = "deepseek-chat"

# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/agent01-0.0.1-SNAPSHOT.jar
```

验证：

```bash
curl http://localhost:8080/api/v1/health
# 返回: {"status":"UP","timestamp":"..."}
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
# 打开 http://localhost:8081  (vite.config.js 配置端口 8081, 已代理 /api 到后端 8080)
```

***

## 🧪 API 速查表

所有接口前缀：`/api/v1`

### 对话 — 同步

```http
POST /chat
Content-Type: application/json

{
  "sessionId":  "optional-auto-generated",
  "message":    "列出 workspace 里的文件,并读取 README.md",
  "enableTools": true,
  "enableRag":   false,
  "stream":      false,
  "attachments": [                                    // 可选: 附件文件列表
    {
      "fileName": "report.md",
      "fileType": "md",
      "fileSize": 12345,
      "content":  "这里是文件的文本内容..."
    }
  ]
}
```

### 对话 — 流式 SSE（推荐前端使用）

```http
POST /chat/stream
Content-Type: application/json
Accept: text/event-stream

# 请求体同上; 事件类型:
#   event: session    data: {sessionId, requestId}
#   event: tool_call  data: {toolName, arguments, callId, startedAt}
#   event: tool_result data: {toolName, callId, result, success, durationMs, startedAt, finishedAt}
#   event: token      data: "增量文本"
#   event: usage      data: {model, promptTokens, completionTokens, totalTokens, cost, currency}
#   event: cancelled  data: "取消原因"
#   event: done       data: {}
#   event: error      data: {message}
```

### 清空会话记忆

```http
DELETE /chat/{sessionId}
```

### 停止生成（真正取消）

```http
POST /chat/cancel/{requestId}      # 中断进行中的流式请求(关闭底层 LLM 连接)
```

### 定时任务

```http
GET    /schedules                  # 列表
POST   /schedules                  # 手动创建 {name,message,delaySeconds}
DELETE /schedules/{id}             # 取消
```

### Token/成本统计

```http
GET /stats/overview                # {summary:{...}, recent:[...]}
GET /stats/summary                 # 汇总
GET /stats/usage                   # 明细
```

### 审计日志

```http
GET /audit/logs?action=TOOL_CALL&sessionId=xxx   # 按类型/会话过滤
```

### 文档管理

```http
POST   /documents           multipart/form-data; field=file         # 上传文档(返回 id/name/size/chunks)
GET    /documents                                                # 列表: [{id,name,size,chunkCount,createTime}]
DELETE /documents/{documentId}                                   # 删除(同时删向量索引)
```

### Skill 管理

```http
POST   /skills                     multipart/form-data; field=file   # 上传 Skill(.md/.markdown/.txt)
GET    /skills                                                        # 列表: [{id,name,fileName,fileSize,contentLength,enabled,uploadedAt}]
GET    /skills/{id}/content                                           # 读取 Skill 内容: {content: "..."}
PATCH  /skills/{id}/enabled?enabled=true                               # 切换启用/禁用状态
DELETE /skills/{id}                                                    # 删除 Skill(同时删磁盘文件)
```

### 健康检查

```http
GET /health   → {"status":"UP","timestamp":"2026-08-08T00:00:00"}
```

***

## ⚙️ 关键配置说明

全部通过环境变量覆盖（对应 `application.yml` 的占位符）：

| 环境变量                                            | 默认值                        | 说明                                          |
| ----------------------------------------------- | -------------------------- | ------------------------------------------- |
| `SERVER_PORT`                                   | `8080`                     | 后端 HTTP 端口                                  |
| `DEEK_SEEK_API_KEY`                             | (必填)                       | DeepSeek API Key                            |
| `DEEK_SEEK_BASE_URL`                            | `https://api.deepseek.com` | 自定义代理/兼容 Base URL                           |
| `DEEK_SEEK_MODEL`                               | `deepseek-chat`            | 模型名; 可选 deepseek-v4-pro / deepseek-reasoner |
| `DEEK_SEEK_TEMPERATURE`                         | `0.7`                      | 采样温度                                        |
| `DEEK_SEEK_MAX_TOKENS`                          | `2048`                     | 单次最大输出 Token                                |
| `REDIS_HOST` / `PORT` / `PASSWORD` / `DATABASE` | localhost/6379/空/0         | Redis 连接参数                                  |
| `agent.memory.max-history`                      | 20                         | 保留最近 N 轮对话                                  |
| `agent.memory.ttl-hours`                        | 24                         | 会话在 Redis 的 TTL                             |
| `agent.memory.summarize-enabled`                | true                       | 是否启用记忆摘要压缩                                  |
| `agent.memory.summarize-threshold`              | 16                         | 历史消息数超过该值触发摘要                                |
| `agent.memory.summarize-keep-recent`            | 8                          | 摘要后保留最近 N 条消息                                 |
| `agent.router.enabled` / `default-model` / `reasoning-model` | true / 空 / 空 | 多模型路由开关与默认/推理模型                              |
| `agent.router.reasoning-keywords`               | 分析,推理,为什么,…            | 命中关键词路由到推理模型                                 |
| `agent.cost.enabled` / `currency`               | true / CNY                 | 是否启用成本统计与货币                                  |
| `agent.cost.prompt-price` / `completion-price`  | 2.0 / 8.0                  | 每百万 token 价格(元)                               |
| `agent.rag.enabled`                             | true                       | 是否启用 RAG                                    |
| `DOCS_UPLOAD_DIR`                               | `./data/docs`              | 文档落盘目录                                      |
| `DOCS_CHUNK_SIZE` / `OVERLAP`                   | 500 / 100                  | 文档分块                                        |
| `agent.tools.file.workspace-dir`                | `./workspace`              | 文件工具沙箱根目录                                   |
| `agent.tools.file.max-read-bytes`               | 204800 (200KB)             | 读上限                                         |
| `agent.tools.file.max-write-bytes`              | 512000 (500KB)             | 写上限                                         |
| `agent.skill.enabled`                           | `true`                     | 是否启用 Skill 注入                                |
| `agent.skill.dir`                               | `./data/skills`            | Skill 文件存储目录                                 |
| `agent.tools.max-iterations`                    | 8                          | 工具调用最大循环次数                                   |
| `agent.tools.parallel`                          | true                       | 是否并行执行无依赖工具                                  |
| `agent.tools.search.enabled` / `provider` / `api-key` | true / duckduckgo / 空 | 联网搜索开关、provider、Serper Key                     |
| `agent.tools.browser.enabled`                   | true                       | 浏览器自动化(browse_url)开关                          |
| `agent.tools.scheduler.enabled` / `tick-ms`     | true / 60000               | 定时任务开关与调度轮询间隔                                |
| `H2_PASSWORD`                                   | 空                          | H2 数据库密码(审计/统计/定时任务持久化)                      |

***

## 🧱 工具调用开发指南

### 新增一个自定义工具（3 步）

1. 在 `src/main/java/com/vanzy/agent/tool/impl/` 新建类，实现 `Tool` 接口，加 `@Component`：

```java
@Component
public class WeatherTool implements Tool {
    @Override public String getName()        { return "query_weather"; }
    @Override public String getDescription() { return "查询指定城市的天气"; }
    @Override public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "city", Map.of("type", "string", "description", "城市名,如 北京"),
                "unit", Map.of("type", "string", "enum", List.of("celsius","fahrenheit"))
            ),
            "required", List.of("city")
        );
    }
    @Override public ToolResult execute(Map<String, Object> args) {
        String city = (String) args.get("city");
        long t0 = System.currentTimeMillis();
        try {
            String content = callWeatherApi(city);            // 你的业务逻辑
            return ToolResult.ok(content, System.currentTimeMillis()-t0);
        } catch (Exception e) {
            return ToolResult.error("查询失败: " + e.getMessage());
        }
    }
}
```

1. 启动后端 → 日志里会出现：`注册工具: query_weather`，无需其他配置。
2. 前端直接问："上海今天天气怎么样？" → LLM 自动识别并调用 `query_weather`，前端 `MessageItem.vue` 已有的默认图标逻辑会自动渲染工具卡片。

### 工具调用链路（执行顺序）

```
User 消息(含可选附件)
  → buildMessages:
     1. System Prompt = 默认提示词 + Skill 注入(所有已启用 Skill 内容) + RAG 检索片段
     2. 历史对话(Redis)
     3. 用户消息(附件内容以"附件[N]: 文件名 + 内容块"格式拼到消息前)
  → chatWithToolsFlow / chatWithToolsSync
     → while (true):
        1. 带 tools schema 调 DeepSeek
        2. 返回 tool_calls:
              推送 ToolCall 事件 → 执行工具 → 推送 ToolResult 事件 → 加入 working 消息 → 继续下一轮
        3. 返回最终文本:
              推送 Token 事件 → sink.complete() → 保存记忆 → 结束
```

***

## 🧪 安全与限制

| 维度       | 策略                                                                               |
| -------- | -------------------------------------------------------------------------------- |
| 文件越权     | 所有路径经 `Path.normalize()` + `resolve(workspace)` + `startsWith(sandboxRoot)` 三重校验 |
| 文件类型     | read\_file 仅允许白名单文本扩展名；禁止读取未知/二进制                                                |
| 内容大小     | 读写都有字节上限，超限返回结构化错误，不会抛出 500                                                      |
| Shell 命令 | execute\_shell 直接执行宿主命令，生产环境建议 `agent.tools.enabled=false` 或仅内网部署                |
| Shell 编码 | PowerShell 子进程强制 `chcp 65001` + UTF-8 输出，Java 端用 UTF-8 读取，避免中文乱码              |
| Skill 文件 | 仅允许 .md/.markdown/.txt 格式；元数据存 Redis，文件存磁盘 `./data/skills/`                      |
| 对话附件     | 前端本地读取文本内容，二进制文件(pdf/docx/图片)直接拒绝；附件内容超 200KB 自动截断                            |
| API Key  | 通过环境变量注入，**禁止**写进 `application.yml` 或提交 Git                                      |
| 文档上传     | 限制 50MB/文件，100MB/请求；仅解析文本内容，不执行宏/脚本                                              |
| HTTP 编码  | 全局强制 UTF-8（server.servlet.encoding + WebMvcConfig），SSE/JSON 响应均带 charset=UTF-8  |

***

## 🛠️ 常见问题

1. **启动报** **`DEEK_SEEK_API_KEY`** **空？**\
   设置环境变量再启动：PowerShell → `$env:DEEK_SEEK_API_KEY = "sk-xxx"`\
   或直接改 `application.yml` 的 `deepseek.api-key`(仅限本地,勿提交)
2. **Redis 连不上？**\
   先确认 `redis-cli ping` 通；无 Redis 环境可临时把 `spring.data.redis.host` 指向可用实例，或把 MemoryService 改回内存实现（自定义 Bean 覆盖即可）。
3. **前端 API 报 404 / 跨域？**\
   Vite 开发代理已把 `/api/*` → `http://localhost:8080`；确认后端是 8080，前端是 8081。生产部署把前端 dist 用 Nginx 托管并加相同规则的反向代理。
4. **RAG 检索效果不好？**\
   把 `DOCS_CHUNK_SIZE` 调小（如 300）、`agent.rag.top-k` 调大（如 6）；或更换更强的 Embedding 模型替换当前 `TextVectorizer` 的 n-gram 实现。
5. **LLM 不调用工具？**
   - 设置 `enableTools=true`（前端默认已开）
   - 换用 deepseek-v4-pro 等工具调用能力更强的模型
   - 在问题里明确提及工具名，如："用 list\_files 列出 workspace"
6. **Shell 命令输出中文乱码？**
   - 已修复：ShellTool 会自动执行 `chcp 65001` + 设置 UTF-8 输出编码，Java 端用 UTF-8 读取
   - 如仍出现乱码，检查 PowerShell 版本是否支持 `-OutputFormat Text` 参数
7. **Skill 上传后没生效？**
   - 在 Skill 管理页确认该 Skill 的开关是否为"启用"状态
   - 检查 `agent.skill.enabled` 是否为 `true`
   - 查看后端日志 `logs/agent.log` 确认 Skill 上传成功
8. **附件文件无法上传？**
   - 确认文件格式为文本类型（md/txt/csv/json/py/java 等）
   - PDF/Word/图片等二进制文件会被前端直接拒绝，请先转换为 txt/md

***

## 📝 License

MIT
