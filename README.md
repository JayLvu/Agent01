# Agent01 · AI 对话 Agent

> 基于 Spring Boot 3 + DeepSeek 大模型 + Vue 2 的私有化 AI 对话 Agent，内置 **LLM 工具调用（Function Calling）框架**、**流式响应（SSE）**、**对话记忆（Redis）**、**RAG 文档检索（向量搜索）**四大核心能力。

---

## ✨ 功能特性

### 🤖 核心对话
- **DeepSeek 大模型对接**：官方原生 API，支持 deepseek-chat / deepseek-reasoner / deepseek-v4-pro 等模型
- **SSE 流式响应**：边生成边输出，打字机效果（前端通过 `EventSource` 实时消费）
- **同步响应接口**：同时提供同步接口，方便非流式场景调用
- **多轮对话记忆**：基于 Redis 保存会话上下文，默认保留最近 20 轮，TTL 24 小时

### 🛠️ LLM 工具调用框架（Function Calling）
LLM 可以自主决策、编排、链式调用以下工具，循环执行直到拿到最终答案（`while` 无限循环，退出条件：LLM 不再返回 tool_calls）：

| 工具名 | 类名 | 功能说明 |
|---|---|---|
| `calculate` | CalculatorTool | 数学表达式求值（支持 +、-、*、/、括号、小数、幂运算） |
| `query_datetime` | DateTimeTool | 查询当前日期时间，支持自定义格式与时区偏移 |
| `execute_shell` | ShellTool | 在本机执行 Shell / PowerShell / CMD 命令并返回输出 |
| `list_files` | FileListTool | 列出 workspace 沙箱内的文件与目录（支持递归） |
| `read_file` | FileReadTool | 读取沙箱内的文本文件（txt/md/json/csv/yaml/xml/py/java/js/html 等） |
| `write_file` | FileWriteTool | 在沙箱内创建/覆盖/追加文本文件，自动创建父目录 |

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

### 🎨 前端界面（Vue 2 + Element UI）
- 三个模块：**对话页 / 文档管理 / 关于**
- 对话页：
  - 气泡式消息流，支持 Markdown 渲染（marked）
  - 工具调用卡片：图标 → 输入参数 → 执行中 loading → 成功/失败结果（可折叠展开）
  - 流式 Token 实时追加，支持取消生成
  - 多 Session 会话切换，新会话按钮
- 开发环境：通过 `devServer.proxy` 把 `/api` 代理到后端 `8080`，解决跨域
- 生产构建：输出到 `frontend/dist/`，可由 Nginx 托管或放到 Spring Boot static 目录

---

## 🏗️ 技术栈

### 后端
| 模块 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2.5 |
| Web/HTTP | Spring Web + WebFlux (WebClient) | — |
| 参数校验 | Spring Validation (Jakarta) | — |
| 缓存/记忆/向量库 | Redis (Spring Data Redis + Jedis) | — |
| JSON | Jackson | 2.16.1 |
| PDF 解析 | Apache PDFBox | 3.0.2 |
| Office 解析 | Apache POI OOXML | 5.2.5 |
| 工具类 | Hutool + Apache Commons Lang3 | 5.8.27 / — |
| 代码简化 | Lombok | — |
| 构建 | Maven | 3.9+ |

### 前端
| 模块 | 选型 | 版本 |
|---|---|---|
| 语言 | JavaScript (ES6+) | — |
| 框架 | Vue | 2.7.16 |
| UI | Element UI | 2.15.14 |
| 路由 | Vue Router | 3.6.5 |
| 状态 | Vuex | 3.6.2 |
| HTTP | Axios | 1.6.8 |
| Markdown | marked | 12.0.1 |
| CSS | Sass / SCSS | 1.72.0 |
| 构建 | Vue CLI Service (Webpack) | 5.0.8 |
| 运行时 | Node.js | ≥ 14（推荐 18+） |

---

## 📂 项目结构

```
Agent01/
├── backend (以项目根目录作为 Spring Boot 工程根)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/vanzy/agent/
│   │   │   │   ├── AgentApplication.java              # 启动类
│   │   │   │   ├── client/
│   │   │   │   │   └── DeepSeekClient.java            # DeepSeek HTTP 封装(同步/流式 + tools schema)
│   │   │   │   ├── config/
│   │   │   │   │   ├── AgentProperties.java           # agent.* 配置绑定(记忆/RAG/工具/文件沙箱)
│   │   │   │   │   ├── DeepSeekProperties.java        # deepseek.* 配置绑定
│   │   │   │   │   ├── RedisConfig.java               # Redis 序列化配置
│   │   │   │   │   └── WebClientConfig.java           # WebClient 超时+连接池
│   │   │   │   ├── controller/
│   │   │   │   │   ├── ChatController.java            # /chat  同步 /chat/stream SSE /clear
│   │   │   │   │   ├── DocumentController.java        # /documents 上传/列表/删除
│   │   │   │   │   └── HealthController.java          # /health 健康检查
│   │   │   │   ├── exception/                         # 自定义异常 + 全局处理
│   │   │   │   ├── model/
│   │   │   │   │   ├── ChatMessage.java               # 统一消息结构(含 tool_calls)
│   │   │   │   │   ├── ChatRequest.java               # 入参(sessionId/message/enableTools/enableRag/stream)
│   │   │   │   │   ├── ChatResponse.java              # 同步响应
│   │   │   │   │   ├── DeepSeekDtos.java              # DeepSeek 请求/响应 DTO
│   │   │   │   │   ├── Document.java / DocumentChunk.java
│   │   │   │   │   └── StreamEvent.java               # 流式事件(Token/ToolCall/ToolResult/Error)
│   │   │   │   ├── rag/
│   │   │   │   │   ├── DocumentService.java / impl    # 文档上传解析分块
│   │   │   │   │   ├── RagService.java / impl         # 检索增强,拼入上下文
│   │   │   │   │   ├── VectorStore.java / impl Redis  # 向量增删查
│   │   │   │   │   └── TextVectorizer.java            # 文本向量化(n-gram)
│   │   │   │   ├── service/
│   │   │   │   │   ├── ChatService.java / impl        # 核心对话(同步+流式+工具循环+RAG注入)
│   │   │   │   │   └── MemoryService.java / Redis     # 会话记忆存取
│   │   │   │   └── tool/
│   │   │   │       ├── Tool.java                      # 工具统一接口
│   │   │   │       ├── ToolRegistry.java              # 自动注册 + JSON Schema 生成
│   │   │   │       ├── ToolResult.java                # 工具执行结果(success/content/duration/error)
│   │   │   │       └── impl/                          # 6 个内置工具实现
│   │   │   └── resources/
│   │   │       ├── application.yml                    # 主配置
│   │   │       └── application-dev.yml                # 开发环境覆盖
│   │   └── test/                                      # 单元测试(向量化 smoke test)
│   └── pom.xml
│
├── frontend/
│   ├── public/index.html
│   ├── src/
│   │   ├── api/
│   │   │   ├── request.js                             # Axios 实例(带拦截器)
│   │   │   ├── chat.js                                # 对话接口封装
│   │   │   └── document.js                            # 文档接口封装
│   │   ├── assets/styles/global.scss
│   │   ├── components/
│   │   │   ├── Layout.vue                             # 左侧菜单 + 头部 + 主区域
│   │   │   └── MessageItem.vue                        # 单条消息 + 工具调用卡片渲染
│   │   ├── router/index.js                            # /chat /documents /about 三个子路由
│   │   ├── store/index.js                             # Vuex 全局状态(消息/会话)
│   │   ├── utils/sse.js                               # SSE EventSource 封装(Token/工具事件解析)
│   │   ├── views/
│   │   │   ├── Chat.vue                               # 对话主页: 输入框 + 消息列表 + 流式渲染
│   │   │   ├── Documents.vue                          # 文档上传/列表/删除
│   │   │   └── About.vue
│   │   ├── App.vue
│   │   └── main.js
│   ├── vue.config.js                                  # 代理 / 构建输出配置
│   ├── babel.config.js
│   ├── package.json
│   └── .gitignore
│
├── workspace/                 # 文件工具沙箱(运行时生成,不提交 Git)
├── data/docs/                 # 文档上传存储目录
├── data/vector/               # 向量索引持久化
├── .env.example               # 环境变量模板
├── .gitignore                 # 已排除 target/ node_modules/ workspace/ 等
└── README.md                  # 本文件
```

---

## 🚀 快速开始

### 环境要求
- JDK **21** (`java -version`)
- Maven **3.9+** (`mvn -v`)
- Node.js **≥ 14**（推荐 18+）(`node -v`)
- Redis **6+**（需支持 RediSearch 模块时建议 7+，纯内存回退也能跑）
- DeepSeek API Key：[申请入口](https://platform.deepseek.com/)

### 1. 克隆 & 配置

```bash
git clone https://github.com/VanzyLiu/Agent01.git
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
npm run serve
# 打开 http://localhost:8081  (注意 vue.config.js 默认是 8081, 已代理 /api 到后端 8080)
```

---

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
  "stream":      false
}
```

### 对话 — 流式 SSE（推荐前端使用）
```http
POST /chat/stream
Content-Type: application/json
Accept: text/event-stream

# 请求体同上; 事件类型:
#   event: sessionId   data: {sessionId}
#   event: tool_call   data: {name, argsJson, callId}
#   event: tool_result data: {name, callId, content, success, durationMs}
#   event: token       data: "增量文本"
#   event: done        data: {}
#   event: error       data: {message}
```

### 清空会话记忆
```http
DELETE /chat/{sessionId}
```

### 文档管理
```http
POST   /documents           multipart/form-data; field=file         # 上传文档(返回 id/name/size/chunks)
GET    /documents                                                # 列表: [{id,name,size,chunkCount,createTime}]
DELETE /documents/{documentId}                                   # 删除(同时删向量索引)
```

### 健康检查
```http
GET /health   → {"status":"UP","timestamp":"2026-08-08T00:00:00"}
```

---

## ⚙️ 关键配置说明

全部通过环境变量覆盖（对应 `application.yml` 的占位符）：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端 HTTP 端口 |
| `DEEK_SEEK_API_KEY` | (必填) | DeepSeek API Key |
| `DEEK_SEEK_BASE_URL` | `https://api.deepseek.com` | 自定义代理/兼容 Base URL |
| `DEEK_SEEK_MODEL` | `deepseek-chat` | 模型名; 可选 deepseek-v4-pro / deepseek-reasoner |
| `DEEK_SEEK_TEMPERATURE` | `0.7` | 采样温度 |
| `DEEK_SEEK_MAX_TOKENS` | `2048` | 单次最大输出 Token |
| `REDIS_HOST` / `PORT` / `PASSWORD` / `DATABASE` | localhost/6379/空/0 | Redis 连接参数 |
| `agent.memory.max-history` | 20 | 保留最近 N 轮对话 |
| `agent.memory.ttl-hours` | 24 | 会话在 Redis 的 TTL |
| `agent.rag.enabled` | true | 是否启用 RAG |
| `DOCS_UPLOAD_DIR` | `./data/docs` | 文档落盘目录 |
| `DOCS_CHUNK_SIZE` / `OVERLAP` | 500 / 100 | 文档分块 |
| `agent.tools.file.workspace-dir` | `./workspace` | 文件工具沙箱根目录 |
| `agent.tools.file.max-read-bytes` | 204800 (200KB) | 读上限 |
| `agent.tools.file.max-write-bytes` | 512000 (500KB) | 写上限 |

---

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

2. 启动后端 → 日志里会出现：`注册工具: query_weather`，无需其他配置。
3. 前端直接问："上海今天天气怎么样？" → LLM 自动识别并调用 `query_weather`，前端 `MessageItem.vue` 已有的默认图标逻辑会自动渲染工具卡片。

### 工具调用链路（执行顺序）

```
User 消息
  → buildMessages(记忆 + RAG 检索片段)
  → chatWithToolsFlow / chatWithToolsSync
     → while (true):
        1. 带 tools schema 调 DeepSeek
        2. 返回 tool_calls:
              推送 ToolCall 事件 → 执行工具 → 推送 ToolResult 事件 → 加入 working 消息 → 继续下一轮
        3. 返回最终文本:
              推送 Token 事件 → sink.complete() → 保存记忆 → 结束
```

---

## 🧪 安全与限制

| 维度 | 策略 |
|---|---|
| 文件越权 | 所有路径经 `Path.normalize()` + `resolve(workspace)` + `startsWith(sandboxRoot)` 三重校验 |
| 文件类型 | read_file 仅允许白名单文本扩展名；禁止读取未知/二进制 |
| 内容大小 | 读写都有字节上限，超限返回结构化错误，不会抛出 500 |
| Shell 命令 | execute_shell 直接执行宿主命令，生产环境建议 `agent.tools.enabled=false` 或仅内网部署 |
| API Key | 通过环境变量注入，**禁止**写进 `application.yml` 或提交 Git |
| 文档上传 | 限制 50MB/文件，100MB/请求；仅解析文本内容，不执行宏/脚本 |

---

## 🛠️ 常见问题

1. **启动报 `DEEK_SEEK_API_KEY` 空？**  
   设置环境变量再启动：PowerShell → `$env:DEEK_SEEK_API_KEY = "sk-xxx"`  
   或直接改 `application.yml` 的 `deepseek.api-key`(仅限本地,勿提交)

2. **Redis 连不上？**  
   先确认 `redis-cli ping` 通；无 Redis 环境可临时把 `spring.data.redis.host` 指向可用实例，或把 MemoryService 改回内存实现（自定义 Bean 覆盖即可）。

3. **前端 API 报 404 / 跨域？**  
   Vue CLI 开发代理已把 `/api/*` → `http://localhost:8080`；确认后端是 8080，前端是 8081。生产部署把前端 dist 用 Nginx 托管并加相同规则的反向代理。

4. **RAG 检索效果不好？**  
   把 `DOCS_CHUNK_SIZE` 调小（如 300）、`agent.rag.top-k` 调大（如 6）；或更换更强的 Embedding 模型替换当前 `TextVectorizer` 的 n-gram 实现。

5. **LLM 不调用工具？**  
   - 设置 `enableTools=true`（前端默认已开）  
   - 换用 deepseek-v4-pro 等工具调用能力更强的模型  
   - 在问题里明确提及工具名，如："用 list_files 列出 workspace"

---

## 📝 License

MIT
