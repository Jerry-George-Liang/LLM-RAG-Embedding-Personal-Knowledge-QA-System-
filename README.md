# LLM-RAG-Embedding-Personal-Knowledge-QA-System
# AI+ 智能文档问答系统 — 项目文件职责说明文档

> 本文档详细描述了前后端项目中**每个源代码文件的作用、核心逻辑、接口定义及依赖关系**，供开发者快速理解项目架构和定位修改位置。

***

## 一、项目总览

| 层级        | 技术栈                                              | 端口   | 核心职责                    |
| --------- | ------------------------------------------------ | ---- | ----------------------- |
| **前端**    | Vue 3 + Vite + Pinia + TypeScript + Element Plus | 5173 | 用户界面、状态管理、SSE 流式接收      |
| **后端**    | Spring Boot 3.2 + LangChain4j 0.35 + Java 21     | 8080 | RAG 问答、文档解析向量化、SSE 流式推送 |
| **AI 服务** | DeepSeek API（LLM）+ Ollama（Embedding）             | —    | 大语言模型调用 + 文本向量化         |

**通信方式：**

- 前端通过 Vite Proxy (`/api` → `http://localhost:8080`) 转发请求到后端
- 问答接口使用 **SSE (Server-Sent Events)** 实现流式响应
- 其余接口使用传统 REST API（JSON）

***

## 二、前端项目结构

```
frontend/
├── index.html                          # HTML 入口
├── package.json                        # 依赖与脚本配置
├── vite.config.ts                      # Vite 构建与代理配置
├── tsconfig.json                       # TypeScript 基础编译配置
├── tsconfig.app.json                   # TypeScript 应用编译配置（继承基础配置）
├── env.d.ts                            # 环境变量 / .vue 模块类型声明
├── src/
│   ├── main.ts                         # 应用入口：创建 Vue 实例、注册插件
│   ├── App.vue                         # 根组件：仅渲染 <router-view />
│   ├── router/index.ts                 # 路由配置
│   ├── views/ChatView.vue              # 聊天页面视图（布局容器）
│   ├── stores/                         # Pinia 状态管理
│   │   ├── index.ts                    # Store 统一导出
│   │   ├── chat.ts                     # 聊天状态 Store
│   │   ├── session.ts                  # 会话管理 Store
│   │   └── document.ts                 # 文档管理 Store
│   ├── components/chat/                # 聊天相关 UI 组件
│   │   ├── ChatPanel.vue               # 主聊天面板
│   │   ├── Sidebar.vue                 # 左侧会话列表侧边栏
│   │   ├── MessageBubble.vue           # 单条消息气泡
│   │   └── CitationCard.vue            # 引用来源卡片
│   ├── components/upload/              # 文档上传相关 UI 组件
│   │   ├── DocumentUploader.vue        # 文件上传拖拽组件
│   │   └── DocumentList.vue            # 已上传文档列表
│   ├── api/request.ts                  # Axios HTTP 请求封装
│   ├── utils/sse.ts                    # SSE 流式请求工具函数
│   ├── types/index.ts                  # 全局 TypeScript 类型定义
│   └── styles/index.scss               # 全局样式（CSS Reset）
```

***

### 2.1 配置文件

#### `package.json`

- **路径**: `frontend/package.json`
- **职责**: 定义项目元信息、依赖包版本、npm 脚本命令
- **关键依赖**:
  - `vue@^3.4.21` — Vue 3 核心框架
  - `vue-router@^4.3.0` — 路由管理
  - `pinia@^2.1.7` — 状态管理
  - `axios@^1.6.7` — HTTP 客户端
  - `element-plus@^2.5.6` — UI 组件库（中文 locale: zh-cn）
  - `@element-plus/icons-vue@^2.3.1` — Element Plus 图标集
  - `marked@^12.0.0` — Markdown 渲染器（用于 AI 回答的富文本展示）
- **脚本命令**:
  - `dev` → `vite` — 启动开发服务器（端口 5173）
  - `build` → `vue-tsc && vite build` — 类型检查 + 生产构建
  - `preview` → `vite preview` — 预览构建产物

***

#### `vite.config.ts`

- **路径**: `frontend/vite.config.ts`
- **职责**: Vite 开发服务器配置，**核心功能是 API 代理转发**
- **关键配置**:

```typescript
// 路径别名：@ → src/
resolve: { alias: { '@': resolve(__dirname, 'src') } }

// 开发服务器
server: {
  port: 5173,                    // 前端开发端口
  proxy: {
    '/api': {                    // 匹配所有 /api 开头的请求
      target: 'http://localhost:8080',  // 转发到后端
      changeOrigin: true,
      secure: false,
      ws: true,                  // 支持 WebSocket
    }
  }
}
```

- **依赖关系**: 所有通过 `/api/*` 发起的请求都会被代理到后端 8080 端口，前端代码中使用相对路径 `/api/...` 即可

***

#### `tsconfig.json` / `tsconfig.app.json`

- **路径**: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`
- **职责**: TypeScript 编译选项配置
- **关键设置**:
  - `target: ES2020` — 编译目标为现代浏览器
  - `moduleResolution: bundler` — 使用 Vite/Rollup 的模块解析策略
  - `baseUrl: "."`, `paths: {"@/*": ["src/*"]}` — 支持 `@/` 路径别名
  - `strict: false` — 关闭严格模式（宽松类型检查）

***

#### `env.d.ts`

- **路径**: `frontend/env.d.ts`
- **职责**: 为 `.vue` 文件和 Vite 环境变量提供 TypeScript 类型声明
- **内容**: 声明 `*.vue` 模块的类型为 `DefineComponent`

***

### 2.2 应用入口与路由

#### `main.ts`

- **路径**: `src/main.ts`
- **职责**: Vue 应用启动入口，注册全局插件和组件
- **核心逻辑**:
  ```typescript
  // 1. 创建 Vue 应用实例
  const app = createApp(App)

  // 2. 全局注册所有 Element Plus 图标组件
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  // 3. 注册插件（按顺序）
  app.use(createPinia())          // Pinia 状态管理
  app.use(router)                  // Vue Router 路由
  app.use(ElementPlus, { locale: zhCn })  // Element Plus UI 库（中文）

  // 4. 挂载到 DOM
  app.mount('#app')
  ```
- **依赖关系**: 引入 `router`、`App.vue`、全局样式 `styles/index.scss`

***

#### `App.vue`

- **路径**: `src/App.vue`
- **职责**: 根组件，仅包含 `<router-view />` 作为路由出口
- **样式**: 设置 html/body/#app 为全屏高度（100%），无 margin/padding

***

#### `router/index.ts`

- **路径**: `src/router/index.ts`
- **职责**: 定义应用路由表
- **路由规则**:

| 路径      | 组件             | 说明           |
| ------- | -------------- | ------------ |
| `/`     | —              | 重定向到 `/chat` |
| `/chat` | `ChatView.vue` | 聊天主页面        |

***

### 2.3 视图层 (Views)

#### `ChatView.vue`

- **路径**: `src/views/ChatView.vue`
- **职责**: 聊天页面的**布局容器**，组合 Sidebar 和 ChatPanel 为左右分栏布局
- **模板结构**:
  ```
  .chat-layout (flex 横向布局)
  ├── Sidebar (左侧边栏，280px)
  └── ChatPanel (右侧聊天区，flex:1)
  ```
- **依赖**: `Sidebar.vue`、`ChatPanel.vue`

***

### 2.4 状态管理层 (Stores)

#### `stores/index.ts`

- **路径**: `src/stores/index.ts`
- **职责**: 统一导出所有 Pinia Store，方便其他模块一次性导入
- **导出内容**: `useChatStore`、`useSessionStore`、`useDocumentStore`

***

#### `stores/chat.ts` ⭐ 核心

- **路径**: `src/stores/chat.ts`
- **职责**: **聊天核心状态管理**，负责消息增删改查、SSE 流式接收与消息更新
- **State（状态）**:

| 状态名                | 类型               | 说明         |
| ------------------ | ---------------- | ---------- |
| `messages`         | `ref<Message[]>` | 当前会话的消息列表  |
| `currentSessionId` | `ref<string>`    | 当前活跃会话 ID  |
| `isStreaming`      | `ref<boolean>`   | 是否正在流式接收回答 |

- **Message 接口**:
  ```typescript
  interface Message {
    id: string                          // UUID，唯一标识
    role: 'user' | 'assistant'         // 消息角色
    content: string                     // 消息文本内容
    citations: Citation[]               // 引用来源列表
    timestamp: number                   // 时间戳
    loading?: boolean                   // 是否正在加载（打字动画）
  }
  ```
- **Actions（方法）**:

| 方法名                                      | 参数          | 返回值              | 功能说明                                       |
| ---------------------------------------- | ----------- | ---------------- | ------------------------------------------ |
| `addUserMessage(content)`                | 用户输入文本      | `void`           | 向 messages 中追加一条 user 角色消息                 |
| `addAssistantPlaceholder()`              | 无           | `string` (msgId) | 插入一条空的 assistant 占位消息（loading=true）        |
| `updateAssistantContent(msgId, content)` | 消息ID + 累积文本 | `void`           | **流式更新**：用新内容替换指定消息的内容（不可变更新 + triggerRef） |
| `updateAssistantDone(msgId, citations)`  | 消息ID + 引用列表 | `void`           | 标记消息完成，设置 citations 并关闭 loading            |
| `updateAssistantError(msgId, error)`     | 消息ID + 错误文本 | `void`           | 标记消息出错，显示错误信息                              |
| `sendMessage(question)`                  | 用户问题        | `Promise<void>`  | **核心方法**：完整发送流程（见下方详解）                     |
| `clearMessages()`                        | 无           | `void`           | 清空当前消息列表                                   |
| `setSessionId(sessionId)`                | 会话 ID       | `void`           | 切换当前会话                                     |
| `loadHistoryMessages(history)`           | 历史消息数组      | `void`           | 加载历史会话消息到 messages                         |

- **sendMessage 核心流程**:
  1. 调用 `addUserMessage(question)` 添加用户消息
  2. 调用 `addAssistantPlaceholder()` 创建 AI 占位消息，获取 msgId
  3. 设置 `isStreaming = true`
  4. 调用 `fetchSSE('/api/chat/stream', { question, sessionId })` 建立 SSE 连接
  5. **for await...of** 循环处理 SSE 事件流：
     - `type=chunk`: 累积 answer 文本 → `updateAssistantContent()` 实时更新 UI
     - `type=citation`: 收集引用信息到 `collectedCitations[]`
     - `type=done`: 调用 `updateAssistantDone()` 完成消息；若首次对话则保存 sessionId 并刷新会话列表
     - `type=error`: 调用 `updateAssistantError()` 显示错误
  6. finally 中设置 `isStreaming = false`
- **依赖关系**:
  - 调用 `utils/sse.ts` 的 `fetchSSE()` 进行 SSE 通信
  - 调用 `stores/session.ts` 的 `useSessionStore()` 处理会话关联

***

#### `stores/session.ts`

- **路径**: `src/stores/session.ts`
- **职责**: 会话 CRUD 管理，与会话历史相关操作
- **State（状态）**:

| 状态名                | 类型                   | 说明         |
| ------------------ | -------------------- | ---------- |
| `sessions`         | `ref<SessionInfo[]>` | 所有会话列表     |
| `currentSessionId` | `ref<string>`        | 当前选中的会话 ID |

- **Actions（方法）**:

| 方法名                              | 参数    | 返回值                             | API 路径                       | 功能说明         |
| -------------------------------- | ----- | ------------------------------- | ---------------------------- | ------------ |
| `createSession()`                | 无     | `Promise<SessionInfo>`          | `POST /sessions/create`      | 创建新会话        |
| `fetchSessions()`                | 无     | `Promise<void>`                 | `GET /sessions/list`         | 从服务端获取全部会话列表 |
| `deleteSession(sessionId)`       | 会话 ID | `Promise<void>`                 | `DELETE /sessions/{id}`      | 删除指定会话       |
| `switchSession(sessionId)`       | 会话 ID | `void`                          | （纯客户端）                       | 切换当前活跃会话     |
| `fetchSessionHistory(sessionId)` | 会话 ID | `Promise<ChatMessageHistory[]>` | `GET /sessions/{id}/history` | 获取某会话的历史消息   |

- **依赖关系**: 通过 `api/request.ts` 的 axios 实例发送 HTTP 请求

***

#### `stores/document.ts`

- **路径**: `src/stores/document.ts`
- **职责**: 文档上传、列表查询、删除等管理
- **State（状态）**:

| 状态名         | 类型                        | 说明      |
| ----------- | ------------------------- | ------- |
| `documents` | `ref<DocumentMetadata[]>` | 已上传文档列表 |
| `uploading` | `ref<boolean>`            | 是否正在上传  |

- **Actions（方法）**:

| 方法名                          | 参数      | 返回值             | API 路径                    | 功能说明                      |
| ---------------------------- | ------- | --------------- | ------------------------- | ------------------------- |
| `fetchDocuments()`           | 无       | `Promise<void>` | `GET /documents/list`     | 获取已上传文档列表                 |
| `uploadFile(file)`           | File 对象 | `Promise<any>`  | `POST /documents/upload`  | 上传文件（multipart/form-data） |
| `clearAllDocuments()`        | 无       | `Promise<void>` | `DELETE /documents/clear` | 清空所有文档                    |
| `deleteDocument(documentId)` | 文档 ID   | `Promise<void>` | `DELETE /documents/{id}`  | 删除指定文档                    |

***

### 2.5 工具层 (Utils & API)

#### `utils/sse.ts` ⭐ 核心

- **路径**: `src/utils/sse.ts`
- **职责**: **封装 SSE (Server-Sent Events) 流式请求**，将后端的 text/event-stream 解析为 AsyncGenerator
- **核心函数**:

```typescript
export async function* fetchSSE(url: string, body: object): AsyncGenerator<SSEEvent>
```

- **参数**:
  - `url`: SSE 请求地址（如 `/api/chat/stream`）
  - `body`: POST 请求体（JSON 对象，如 `{ question, sessionId }`）
- **返回值**: `AsyncGenerator<SSEEvent>` — 异步生成器，每次 yield 一个 SSE 事件对象
- **内部实现逻辑**:
  1. 使用原生 `fetch()` 发起 POST 请求，`Content-Type: application/json`
  2. 获取 `response.body` 的 ReadableStream Reader
  3. 循环 `reader.read()` 读取数据块
  4. 按 `\n` 分割数据行，提取 `data:` 前缀后的 JSON 内容
  5. `JSON.parse()` 解析后 `yield` 给调用方
  6. 遇到 `[DONE]` 标记或 stream 结束时终止
- **SSEEvent 接口**:
  ```typescript
  interface SSEEvent {
    type: string              // 'chunk' | 'citation' | 'done' | 'error'
    content?: string          // chunk 类型的文本片段
    citation?: any            // citation 类型的引用对象
    answer?: string           // done 类型中的完整答案
    citations?: any[]         // done 类型中的引用列表
    error?: string            // error 类型的错误信息
  }
  ```
- **依赖关系**: 被 `stores/chat.ts` 的 `sendMessage()` 方法调用

***

#### `api/request.ts`

- **路径**: `src/api/request.ts`
- **职责**: 基于 Axios 的 HTTP 请求封装实例，统一处理拦截器和错误提示
- **配置**:
  ```typescript
  baseURL: '/api'             // 所有请求自动加 /api 前缀
  timeout: 60000              // 60 秒超时
  headers: { 'Content-Type': 'application/json' }
  ```
- **请求拦截器**: 直接放行（可扩展添加 token 等）
- **响应拦截器**:
  - 成功：判断 `res.code !== 200` 时用 `ElMessage.error()` 提示并 reject
  - 失败：按 HTTP 状态码映射中文错误消息：
    - 400 → "请求参数错误"
    - 401 → "未授权，请登录"
    - 403 → "拒绝访问"
    - 404 → "资源不存在"
    - 413 → "文件大小超出限制"
    - 500 → "服务器内部错误"
    - timeout → "请求超时"
    - 其他 → "网络异常"
- **依赖关系**: 被 `session.ts`、`document.ts` 两个 Store 使用

***

#### `types/index.ts`

- **路径**: `src/types/index.ts`
- **职责**: 全局 TypeScript 类型定义，前后端数据结构对齐
- **导出的接口**:

| 接口名                      | 用途          | 关键字段                                           |
| ------------------------ | ----------- | ---------------------------------------------- |
| `ApiResponse<T>`         | 统一 API 响应包装 | code, message, data, timestamp                 |
| `DocumentUploadResponse` | 文档上传响应      | documentId, fileName, segmentCount, message    |
| `DocumentMetadata`       | 文档元信息       | documentId, fileName, uploadTime, segmentCount |
| `ChatRequest`            | 聊天请求体       | question, sessionId?                           |
| `Citation`               | 引用来源        | sourceFileName, content, relevanceScore        |
| `ChatResponse`           | 聊天响应体       | answer, citations\[], sessionId                |
| `SessionInfo`            | 会话信息        | sessionId, createdAt, messageCount, title?     |
| `ChatMessageHistory`     | 历史消息        | role ('user'\|'assistant'), content            |
| `SseChunkEvent`          | SSE 事件类型联合  | type, content?, citation?, error?, sessionId?  |

***

### 2.6 组件层 (Components)

#### `components/chat/ChatPanel.vue` ⭐ 核心界面

- **路径**: `src/components/chat/ChatPanel.vue`
- **职责**: **聊天主面板**，包含标题栏、消息区域、输入框三大区域
- **Props**: 无（直接使用 chatStore）
- **模板结构**:
  ```
  .chat-panel (flex 纵向布局)
  ├── .chat-header       — 标题栏："AI+ 智能文档助手" + "思考中..."标签
  ├── .message-container  — 消息滚动区域
  │   ├── .welcome-screen  — 空状态欢迎页（无消息时显示）
  │   └── MessageBubble×N  — 消息气泡列表（v-for 动态 key）
  └── .input-area         — 输入区域
      ├── el-input (textarea) — 多行输入框
      └── el-button (发送)     — 圆形发送按钮
  ```
- **核心交互逻辑**:

| 函数名                | 触发条件                        | 功能                                                                       |
| ------------------ | --------------------------- | ------------------------------------------------------------------------ |
| `handleSend()`     | 点击发送按钮 / Enter 键            | 1. 校验非空且不在流式中；2. 清空输入；3. 调用 `chatStore.sendMessage(question)`；4. 自动滚动到底部 |
| `scrollToBottom()` | messages.length 变化时 (watch) | 使用 nextTick + scrollTop 滚动到最新消息                                          |

- **输入框特性**:
  - 最大长度 2000 字符
  - Enter 发送，Shift+Enter 换行
  - 流式输出期间禁用输入
  - placeholder: "输入你的问题... (Enter 发送，Shift+Enter 换行)"
- **v-for 动态 Key 策略**（解决响应式更新问题）:
  ```vue
  :key="`${msg.id}-${msg.content.length}-${msg.loading ? '1' : '0'}`"
  ```
  当消息内容变化或 loading 状态切换时强制重新渲染 MessageBubble
- **依赖**: `useChatStore`、`MessageBubble.vue`

***

#### `components/chat/Sidebar.vue`

- **路径**: `src/components/chat/Sidebar.vue`
- **职责**: 左侧边栏，包含新建对话按钮、会话列表、知识库管理入口
- **模板结构**:
  ```
  .sidebar (深色主题 #1a1a2e)
  ├── .sidebar-header    — "新建对话" 按钮
  ├── .session-list      — 会话列表（可滚动）
  │   ├── 空状态提示     — 无会话时显示
  │   └── .session-item×N — 会话条目（标题 + 时间 + 删除按钮）
  └── .sidebar-footer    — "知识库管理" 按钮
  └── el-drawer          — 知识库管理抽屉（内含 DocumentUploader + DocumentList）
  ```
- **核心交互逻辑**:

| 函数名                       | 功能说明                                               |
| ------------------------- | -------------------------------------------------- |
| `handleNewChat()`         | 清空消息 + 清空 sessionId + 切换空会话（不立即创建）                 |
| `handleSwitchSession(id)` | 切换会话 → 清空消息 → 加载历史消息 → 渲染                          |
| `handleDeleteSession(id)` | 弹窗确认 → 调用 sessionStore.deleteSession() → 成功提示      |
| `handleClearAllDocs()`    | 弹窗确认 → 调用 documentStore.clearAllDocuments() → 成功提示 |
| `formatTime(timestamp)`   | 将时间戳格式化为 "M/D HH:mm" 格式，无效值返回"刚刚"                  |

- **生命周期**: `onMounted` 时调用 `sessionStore.fetchSessions()` 加载会话列表
- **依赖**: `useSessionStore`、`useDocumentStore`、`useChatStore`、`DocumentUploader.vue`、`DocumentList.vue`

***

#### `components/chat/MessageBubble.vue`

- **路径**: `src/components/chat/MessageBubble.vue`
- **职责**: **单条消息渲染组件**，区分用户/AI 角色，支持 Markdown 渲染和引用卡片
- **Props**:

| Prop 名    | 类型        | 说明                 |
| --------- | --------- | ------------------ |
| `message` | `Message` | 来自 chatStore 的消息对象 |

- **计算属性**:
  - `renderedContent`: 使用 `marked.parse()` 将 Markdown 文本转为 HTML
- **视觉差异**:
  - **user 消息**: 右对齐、蓝色头像(#409eff)、浅蓝背景(#ecf5ff)、圆角 `16px 16px 4px 16px`
  - **assistant 消息**: 左对齐、绿色头像(#67c23a)、灰色背景(#f5f7fa)、圆角 `16px 16px 16px 4px`
- **子组件**:
  - `CitationCard`: 当 `message.citations.length > 0` 时渲染引用卡片
  - 打字动画: 当 `message.loading === true` 时显示三个跳动圆点
- **样式细节**: 代码块深色主题(`#282c34`)、fadeInUp 进入动画(0.3s)
- **依赖**: `marked` 库、`CitationCard.vue`

***

#### `components/chat/CitationCard.vue`

- **路径**: `src/components/chat/CitationCard.vue`
- **职责**: 展示 AI 回答的引用来源，支持展开/折叠
- **Props**:

| Prop 名      | 类型           | 说明     |
| ----------- | ------------ | ------ |
| `citations` | `Citation[]` | 引用来源数组 |

- **交互**: 点击头部切换 `expanded` 状态，配合 `<transition name="slide">` 实现折叠动画
- **每条引用展示**:
  - 来源文件名（el-tag）
  - 相关性评分（el-rate，将 0\~1 分数转为 1\~5 星）
  - 截断内容预览（最多 150 字符）

***

#### `components/upload/DocumentUploader.vue`

- **路径**: `src/components/upload/DocumentUploader.vue`
- **职责**: 文件上传组件，支持拖拽和点击选择
- **支持的格式**: `.pdf`, `.docx`, `.txt`, `.md`
- **文件大小限制**: 最大 10MB
- **核心交互流程**:
  1. 用户选择/拖拽文件 → `handleFileChange()` 保存到 `currentFile`
  2. `handleBeforeUpload()` 校验格式和大小
  3. 用户点击"确认上传" → `doUpload()` → 调用 `documentStore.uploadFile(file)`
  4. 上传成功 → ElMessage 提示 → 清空文件选择
- **UI 元素**:
  - el-upload (drag 模式, auto-upload=false)
  - 格式标签提示（PDF / Word / TXT / Markdown）
  - 文件信息栏（文件名 + 大小 + 上传按钮/进度条 + 取消按钮）
- **依赖**: `useDocumentStore`

***

#### `components/upload/DocumentList.vue`

- **路径**: `src/components/upload/DocumentList.vue`
- **职责**: 已上传文档列表展示，支持删除单条文档
- **数据来源**: `computed(() => documentStore.documents)`
- **每条文档展示项**:
  - 文件图标（根据扩展名显示不同图标和颜色）
  - 文件名（超长截断省略号）
  - 元信息（段落数 + 上传时间）
  - "已就绪"标签
  - 删除按钮（hover 时显示）
- **删除流程**: ElMessageBox.confirm 确认 → `documentStore.deleteDocument(docId)` → 成功提示
- **生命周期**: `onMounted` 时调用 `documentStore.fetchDocuments()` 加载列表
- **依赖**: `useDocumentStore`

***

### 2.7 样式文件

#### `styles/index.scss`

- **路径**: `src/styles/index.scss`
- **职责**: 全局 CSS 重置和基础样式
- **内容**: box-sizing:border-box、margin/padding 清零、html/body/#app 全屏高度、字体栈设置、链接去下划线

***

## 三、后端项目结构

```
backend/
├── pom.xml                                    # Maven 依赖配置
├── src/main/resources/application.yml         # 应用配置（端口/模型/向量/RAG参数）
├── src/main/java/com/aiplus/rag/
│   ├── AiPlusRagApplication.java              # Spring Boot 启动类
│   ├── controller/                             # REST 控制器层
│   │   ├── ChatController.java                # 聊天/问答控制器（SSE 流式）
│   │   ├── SessionController.java             # 会话管理控制器
│   │   └── DocumentController.java            # 文档管理控制器
│   ├── service/                                # 业务逻辑层
│   │   ├── RagChatService.java                # RAG 问答核心服务 ⭐
│   │   ├── SessionManager.java                # 会话内存管理
│   │   ├── DocumentParseService.java          # 文档解析调度
│   │   ├── DocumentSplitService.java          # 文档分段/切分
│   │   ├── VectorizationService.java          # 向量化存储服务
│   │   └── prompt/
│   │       └── RagPromptTemplate.java         # RAG Prompt 模板
│   ├── config/                                 # 配置类
│   │   ├── LlmConfig.java                     # LLM 大模型配置
│   │   ├── EmbeddingConfig.java               # Embedding 向量模型配置
│   │   ├── RetrieverConfig.java               # RAG 检索器配置
│   │   ├── VectorStoreConfig.java             # 向量存储配置
│   │   └── WebConfig.java                     # CORS 跨域配置
│   ├── model/                                  # 数据模型 (DTO)
│   │   ├── ChatRequest.java                   # 聊天请求体
│   │   ├── ChatResponse.java                  # 聊天响应体
│   │   ├── SessionInfo.java                   # 会话信息
│   │   ├── Citation.java                      # 引用信息
│   │   ├── DocumentMetadata.java              # 文档元数据
│   │   ├── DocumentParseResult.java           # 文档解析结果
│   │   └── ApiResponse.java                   # 统一 API 响应包装
│   ├── parser/                                 # 文件解析器
│   │   ├── FileParser.java                    # 解析器接口
│   │   ├── FileParserFactory.java             # 解析器工厂
│   │   └── impl/
│   │       ├── PdfFileParser.java             # PDF 解析器
│   │       ├── TextFileParser.java            # TXT/MD 解析器
│   │       └── WordFileParser.java            # Word 解析器
│   └── exception/                              # 全局异常处理
│       ├── GlobalExceptionHandler.java        # 统一异常处理器
│       ├── FileParseException.java            # 文件解析异常
│       └── SessionNotFoundException.java      # 会话不存在异常
```

***

### 3.1 项目配置

#### `pom.xml`

- **路径**: `pom.xml`
- **职责**: Maven 项目对象模型，定义依赖和构建插件
- **项目信息**:
  - groupId: `com.aiplus`
  - artifactId: `rag-backend`
  - Java 版本: **21**
  - Spring Boot 版本: **3.2.5**
  - LangChain4j 版本: **0.35.0**
- **核心依赖**:

| 依赖                                        | 版本     | 用途                                       |
| ----------------------------------------- | ------ | ---------------------------------------- |
| `spring-boot-starter-web`                 | 3.2.5  | Spring MVC Web 框架（REST API）              |
| `langchain4j-spring-boot-starter`         | 0.35.0 | LangChain4j 与 Spring Boot 集成             |
| `langchain4j-open-ai`                     | 0.35.0 | OpenAI 兼容客户端（连接 DeepSeek）                |
| `langchain4j-ollama`                      | 0.35.0 | Ollama 客户端（本地 Embedding 模型）              |
| `langchain4j`                             | 0.35.0 | LangChain4j 核心（InMemoryEmbeddingStore 等） |
| `langchain4j-embeddings-all-minilm-l6-v2` | 0.35.0 | ONNX 本地嵌入模型（备用方案）                        |
| `pdfbox`                                  | 2.0.30 | PDF 文件解析（Apache）                         |
| `poi-ooxml`                               | 5.2.5  | Word (.docx) 文件解析（Apache POI）            |
| `lombok`                                  | —      | 简化 Java 样板代码                             |
| `spring-boot-starter-validation`          | —      | 请求参数校验（@NotBlank 等）                      |
| `spring-boot-starter-webflux`             | —      | WebFlux（SSE 流式支持）                        |

***

#### `application.yml`

- **路径**: `src/main/resources/application.yml`
- **职责**: 应用运行时配置，涵盖端口、AI 模型、RAG 参数等
- **完整配置详解**:

```yaml
# ===== 服务器配置 =====
server:
  port: 8080                          # 后端监听端口

# ===== Spring 配置 =====
spring:
  servlet:
    multipart:
      max-file-size: 10MB             # 单文件最大 10MB
      max-request-size: 10MB          # 请求最大 10MB
  application:
    name: ai-plus-rag                 # 应用名称

# ===== DeepSeek LLM 配置（OpenAI 兼容协议）=====
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com   # DeepSeek API 地址
      api-key: ${DEEPSEEK_API_KEY:sk-xxx}  # API 密钥（支持环境变量覆盖）
      model-name: deepseek-v4-flash         # 模型名称
      temperature: 0.7                       # 温度（创造性）
      max-tokens: 2048                       # 最大输出 token 数

# ===== Embedding 向量模型配置 =====
rag:
  embedding:
    type: ollama                        # 模型类型: ollama(推荐) / remote / local
    ollama:
      base-url: http://localhost:11434   # Ollama 服务地址
      model-name: mxbai-embed-large      # Ollama Embedding 模型名
    remote:                              # 远程 API 备选
      base-url: ${EMBEDDING_BASE_URL}
      api-key: ${EMBEDDING_API_KEY}
      model-name: text-embedding-ada-002
    local:                               # ONNX 本地备选
      model: all-MiniLM-L6-v2

  # ===== RAG 检索参数 =====
  retrieval:
    top-k: 5                            # 检索返回的最相关段落数
    min-score: 0.5                      # 相似度最低阈值

  # ===== 文档切分参数 =====
  splitting:
    max-segment-size: 1000              # 每个段落最大字符数
    overlap: 200                        # 段落间重叠字符数
    chunk-overlap: 200                  # 切分重叠（备用字段）

# ===== 日志配置 =====
logging:
  level:
    com.aiplus.rag: DEBUG               # 业务日志 DEBUG 级别
    dev.langchain4j: INFO               # LangChain4j 框架 INFO 级别
```

***

### 3.2 启动入口

#### `AiPlusRagApplication.java`

- **路径**: `src/main/java/com/aiplus/rag/AiPlusRagApplication.java`
- **职责**: Spring Boot 应用启动类
- **注解**: `@SpringBootApplication`（等同于 @Configuration + @EnableAutoConfiguration + @ComponentScan）
- **main 方法**: `SpringApplication.run(AiPlusRagApplication.class, args)`

***

### 3.3 控制器层 (Controllers)

#### `controller/ChatController.java` ⭐ 核心

- **路径**: `controller/ChatController.java`
- **职责**: **聊天问答控制器**，处理 SSE 流式问答请求，是前后端实时通信的核心入口
- **注解**: `@RestController` + `@RequestMapping("/api/chat")` + `@RequiredArgsConstructor`
- **注入依赖**:
  - `RagChatService ragChatService` — RAG 问答服务
  - `SessionManager sessionManager` — 会话管理器
  - `ObjectMapper objectMapper` — JSON 序列化

***

##### 接口详情

###### `POST /api/chat/stream` — 流式问答

- **Produces**: `MediaType.TEXT_EVENT_STREAM_VALUE`（SSE 流）
- **请求参数** (@RequestBody):

| 参数名         | 类型     | 必填 | 说明                   |
| ----------- | ------ | -- | -------------------- |
| `question`  | String | ✅  | 用户提问内容               |
| `sessionId` | String | ❌  | 会话 ID（可选，为空则自动创建新会话） |

- **请求示例**:
  ```json
  { "question": "什么是机器学习？", "sessionId": "" }
  ```
- **返回值**: `SseEmitter`（Spring MVC SSE 对象，非传统 JSON）
- **核心处理流程**:
  1. **会话处理**:
     - 若 `sessionId` 为空或不存在 → 调用 `sessionManager.createSession()` 创建新会话
     - 调用 `sessionManager.updateSessionTitle(sessionId, question)` 更新会话标题
  2. **创建 SseEmitter**（超时 120 秒），注册回调:
     - `onCompletion` → 记录日志
     - `onTimeout` → 记录警告日志
     - `onError` → 记录错误日志
  3. **异步线程执行 RAG 问答**（new Thread 启动）:
     - 调用 `ragChatService.chat(question, sessionId)` 获取 `ChatResponse`
     - **分块发送答案**（每 20 字符一个 chunk）→ 事件类型 `"type":"chunk"`
     - **逐条发送引用** → 事件类型 `"type":"citation"`
     - **发送完成事件** → 事件类型 `"type":"done"`（包含完整答案、sessionId、citations）
     - 调用 `emitter.complete()` 关闭连接
  4. **异常处理**:
     - 捕获异常 → 发送 `"type":"error"` 事件
     - 调用 `emitter.completeWithError(e)` 终止连接
- **SSE 事件格式示例**:
  ```
  data:{"type":"chunk","content":"机器学习"}
  data:{"type":"chunk","content":"是人工智能"}
  data:{"type":"citation","citation":{"sourceFileName":"doc.pdf","content":"...","relevanceScore":0.8}}
  data:{"type":"done","sessionId":"abc123","answer":"完整答案...","citations":[...]}
  ```

***

#### `controller/SessionController.java`

- **路径**: `controller/SessionController.java`
- **职责**: 会话 CRUD 接口
- **注解**: `@RestController` + `@RequestMapping("/api/sessions")` + `@RequiredArgsConstructor`
- **注入**: `SessionManager sessionManager`

***

##### 接口详情

###### `POST /api/sessions/create` — 创建新会话

- **请求参数**: 无（Body 为空）
- **返回值**: `ApiResponse<Map<String, String>>`
  ```json
  {
    "code": 200,
    "message": "success",
    "data": { "sessionId": "a1b2c3d4e5f6g7h8", "createdAt": "1714000000000" },
    "timestamp": 1714000000000
  }
  ```
- **功能**: 调用 `sessionManager.createSession()` 生成 16 位随机会话 ID

***

###### `GET /api/sessions/list` — 获取所有会话列表

- **请求参数**: 无
- **返回值**: `ApiResponse<List<SessionInfo>>`
  ```json
  {
    "code": 200,
    "data": [
      { "sessionId": "a1b2c3d4", "createdAt": 1714000000000, "messageCount": 5, "title": "什么是ML" },
      ...
    ]
  }
  ```

***

###### `DELETE /api/sessions/{sessionId}` — 删除指定会话

- **路径参数**:

| 参数名         | 类型                    | 说明        |
| ----------- | --------------------- | --------- |
| `sessionId` | String (PathVariable) | 要删除的会话 ID |

- **返回值**: `ApiResponse<Void>` — `{ "code": 200, "message": "会话已删除" }`

***

###### `GET /api/sessions/{sessionId}/history` — 获取会话历史消息

- **路径参数**: `sessionId` (String)
- **返回值**: `ApiResponse<List<Map<String, String>>>`
  ```json
  {
    "code": 200,
    "data": [
      { "role": "user", "content": "什么是机器学习？" },
      { "role": "assistant", "content": "机器学习是..." }
    ]
  }
  ```
- **功能**: 从 SessionManager 的 ChatMemory 中取出历史消息，转换为 role/content 格式

***

#### `controller/DocumentController.java`

- **路径**: `controller/DocumentController.java`
- **职责**: 文档上传、列表、删除接口
- **注解**: `@RestController` + `@RequestMapping("/api/documents")` + `@RequiredArgsConstructor`
- **注入**:
  - `DocumentParseService documentParseService` — 文档解析
  - `VectorizationService vectorizationService` — 向量化存储

***

##### 接口详情

###### `POST /api/documents/upload` — 上传文档

- **请求参数** (@RequestParam):

| 参数名    | 类型            | 必填 | 说明                     |
| ------ | ------------- | -- | ---------------------- |
| `file` | MultipartFile | ✅  | 上传的文件（PDF/DOCX/TXT/MD） |

- **Content-Type**: `multipart/form-data`
- **返回值**: `ApiResponse<DocumentMetadata>`
  ```json
  {
    "code": 200,
    "message": "文档上传成功",
    "data": {
      "documentId": "doc_1714000000_12345",
      "fileName": "example.pdf",
      "uploadTime": "2024-01-01T12:00:00",
      "segmentCount": 15
    }
  }
  ```
- **处理流程**:
  1. 校验文件非空
  2. 调用 `documentParseService.parse(file)` → 解析文件得到 Document 列表
  3. 调用 `vectorizationService.vectorize(parseResult)` → 切分 + 向量化 + 存储

***

###### `GET /api/documents/list` — 获取已上传文档列表

- **请求参数**: 无
- **返回值**: `ApiResponse<List<DocumentMetadata>>`
  ```json
  {
    "code": 200,
    "data": [
      { "documentId": "doc_xxx", "fileName": "doc.pdf", "uploadTime": "...", "segmentCount": 15 }
    ]
  }
  ```

***

###### `DELETE /api/documents/clear` — 清空所有文档

- **请求参数**: 无
- **返回值**: `ApiResponse<Void>` — `{ "code": 200, "message": "已清除所有文档数据" }`
- **功能**: 清除 InMemoryEmbeddingStore 中所有向量 + 清空文档注册表

***

###### `DELETE /api/documents/{documentId}` — 删除指定文档

- **路径参数**:

| 参数名          | 类型                    | 说明    |
| ------------ | --------------------- | ----- |
| `documentId` | String (PathVariable) | 文档 ID |

- **返回值**: `ApiResponse<Void>`
- **功能**: 从向量存储中移除该文档的所有嵌入向量 + 从注册表中移除元数据
- **异常**: 文档不存在时抛出 `IllegalArgumentException`(400)

***

### 3.4 服务层 (Services)

#### `service/RagChatService.java` ⭐ 核心业务

- **路径**: `service/RagChatService.java`
- **职责**: **RAG 问答核心业务逻辑**——检索增强生成（Retrieval-Augmented Generation）
- **注解**: `@Service` + `@RequiredArgsConstructor`
- **注入依赖**:
  - `ChatLanguageModel chatLanguageModel` — LLM 大语言模型（DeepSeek）
  - `RagRetriever ragRetriever` — RAG 检索器（向量相似度搜索）
  - `SessionManager sessionManager` — 会话管理器

***

##### 核心方法: `chat(String question, String sessionId)`

- **参数**:
  - `question`: 用户问题
  - `sessionId`: 会话 ID
- **返回值**: `ChatResponse`（包含 answer、citations、sessionId、hasRelevantContext）
- **处理流程**（RAG 标准流程）:
  ```
  用户问题(question)
      ↓
  ① 向量检索 (ragRetriever.retrieve)
      ↓  返回 List<TextSegment> (最相关的文档片段)
  ② 构建上下文 (buildContext)
      ↓  将检索结果格式化为 "[片段N] 来源: xxx\n内容...\n\n"
  ③ 构建引用列表 (buildCitations)
      ↓  从 TextSegment 提取 sourceFileName + content
  ④ 构建 Prompt (buildFullPrompt)
      ↓  SYSTEM_PROMPT + 上下文 + 用户问题（使用 RagPromptTemplate）
  ⑤ 调用 LLM (chatLanguageModel.chat)
      ↓  将完整 prompt 发送给 DeepSeek，获取回答
  ⑥ 保存消息 (sessionManager.addMessage)
      ↓  分别记录 UserMessage 和 AiMessage 到会话记忆
  ⑦ 返回 ChatResponse
  ```
- **Prompt 结构**:
  ```
  [系统提示] 你是一个智能文档助手。请根据以下检索到的文档片段回答用户问题...

  [参考文档内容]
  [片段1] 来源: doc.pdf
  机器学习是人工智能的一个子领域...

  [用户问题]
  什么是机器学习？

  请基于以上参考文档内容回答用户问题。
  ```

***

#### `service/SessionManager.java`

- **路径**: `service/SessionManager.java`
- **职责**: **内存级会话管理器**，管理会话生命周期和聊天记录
- **注解**: `@Component`（单例 Bean）
- **数据结构**（全部 ConcurrentHashMap，线程安全）:

| 字段              | 类型                                         | 说明                                                |
| --------------- | ------------------------------------------ | ------------------------------------------------- |
| `sessions`      | `ConcurrentHashMap<String, ChatMemory>`    | 会话 ID → 聊天记忆（LangChain4j MessageWindowChatMemory） |
| `creationTimes` | `ConcurrentHashMap<String, Long>`          | 会话 ID → 创建时间戳                                     |
| `messageCounts` | `ConcurrentHashMap<String, AtomicInteger>` | 会话 ID → 消息计数                                      |
| `sessionTitles` | `ConcurrentHashMap<String, String>`        | 会话 ID → 标题                                        |

- **常量**: `MAX_MESSAGES_PER_SESSION = 20`（每个会话最多保留 20 条消息，滑动窗口）

***

##### 公开方法一览

| 方法名                                    | 参数                  | 返回值                  | 功能说明                                              |
| -------------------------------------- | ------------------- | -------------------- | ------------------------------------------------- |
| `createSession()`                      | 无                   | `String` (sessionId) | 生成 16 位短 ID + 创建 MessageWindowChatMemory + 初始化计数器 |
| `getSession(sessionId)`                | 会话 ID               | `ChatMemory`         | 获取会话记忆（不存在抛 SessionNotFoundException）             |
| `deleteSession(sessionId)`             | 会话 ID               | `void`               | 从所有 Map 中移除该会话数据                                  |
| `listSessions()`                       | 无                   | `List<SessionInfo>`  | 遍历所有会话，组装 SessionInfo 列表                          |
| `addMessage(sessionId, message)`       | 会话 ID + ChatMessage | `void`               | 向会话记忆中添加消息，同时更新计数和标题                              |
| `updateSessionTitle(sessionId, title)` | 会话 ID + 标题          | `void`               | 仅在标题为空时设置（putIfAbsent），最长 50 字符                   |
| `getHistory(sessionId)`                | 会话 ID               | `List<ChatMessage>`  | 获取会话的全部历史消息                                       |
| `clearAllSessions()`                   | 无                   | `void`               | 清空所有会话数据                                          |
| `exists(sessionId)`                    | 会话 ID               | `boolean`            | 判断会话是否存在                                          |

***

#### `service/DocumentParseService.java`

- **路径**: `service/DocumentParseService.java`
- **职责**: 文档解析调度中心，根据文件扩展名分发到对应解析器
- **注解**: `@Service` + `@RequiredArgsConstructor`
- **注入**: `FileParserFactory parserFactory`

##### 核心方法: `parse(MultipartFile file)`

- **参数**: `MultipartFile`（上传的文件）
- **返回值**: `DocumentParseResult`（包含 fileName、documentId、segmentCount、documents 列表、耗时）
- **流程**:
  1. 校验文件名非空
  2. 提取文件扩展名
  3. 通过 `parserFactory.getParser(extension)` 获取对应解析器
  4. 调用 `parser.parse(inputStream, fileName)` 执行解析
  5. 封装结果（生成 documentId = `doc_时间戳_文件名hashCode`）

***

#### `service/DocumentSplitService.java`

- **路径**: `service/DocumentSplitService.java`
- **职责**: 将解析后的 Document 列表进一步**切分为固定大小的文本段落**（用于向量化）
- **注解**: `@Service`
- **配置参数**（来自 application.yml）:
  - `rag.splitting.max-segment-size`: 默认 **1000** 字符/段
  - `rag.splitting.overlap`: 默认 **200** 字符重叠

##### 核心方法: `split(List<Document> documents, String fileName)`

- **参数**: 解析后的 Document 列表 + 文件名
- **返回值**: `List<TextSegment>`（带 metadata 的文本段落列表）
- **切分策略**:
  1. 若文本 ≤ 1000 字符 → 整体作为一个段落
  2. 若文本 > 1000 字符 → 按 `splitByFixedSize()` 切分：
     - 每 1000 字符切一刀
     - 在 `overlap` 范围内寻找最佳断点（优先换行符 `.` `。` `！` `？` `；`）
     - 段落间有 200 字符重叠，保证语义连续性
  3. 每个 TextSegment 添加 metadata: `{ segment_index, source_file }`

***

#### `service/VectorizationService.java`

- **路径**: `service/VectorizationService.java`
- **职责**: 文档**向量化与存储**管理，将文本段落转为向量并存入内存向量数据库
- **注解**: `@Service` + `@RequiredArgsConstructor`
- **注入依赖**:
  - `EmbeddingModel embeddingModel` — 向量化模型
  - `InMemoryEmbeddingStore<TextSegment> embeddingStore` — 内存向量存储
  - `DocumentSplitService documentSplitService` — 文档切分服务
  - `ConcurrentHashMap<String, DocumentMetadata> documentRegistry` — 文档注册表
  - 内部维护 `documentEmbeddingIds: ConcurrentHashMap<String, List<String>>` — 文档 ID → 向量 ID 列表的映射

***

##### 公开方法一览

| 方法名                          | 参数                  | 返回值                      | 功能说明                                              |
| ---------------------------- | ------------------- | ------------------------ | ------------------------------------------------- |
| `vectorize(parseResult)`     | DocumentParseResult | DocumentMetadata         | 1. 切分文档为段落；2. 批量向量化；3. 存入 embeddingStore；4. 注册元数据 |
| `listDocuments()`            | 无                   | `List<DocumentMetadata>` | 返回所有已注册文档的元信息                                     |
| `clearAll()`                 | 无                   | `void`                   | 清空向量存储 + 注册表 + ID 映射                              |
| `deleteDocument(documentId)` | 文档 ID               | `boolean`                | 根据文档 ID 找到对应的向量 ID 列表 → 批量移除向量 → 移除注册信息           |
| `getTotalSegments()`         | 无                   | `int`                    | 统计所有文档的总段落数                                       |

***

### 3.5 配置类 (Config)

#### `config/LlmConfig.java`

- **路径**: `config/LlmConfig.java`
- **职责**: 配置大语言模型（LLM）Bean —— 连接 DeepSeek API
- **注解**: `@Configuration`
- **配置属性**（来自 application.yml 的 `langchain4j.open-ai.chat-model.*`）:

| 属性            | 默认值                        | 说明              |
| ------------- | -------------------------- | --------------- |
| `base-url`    | `https://api.deepseek.com` | DeepSeek API 地址 |
| `api-key`     | `sk-your-api-key-here`     | API 密钥          |
| `model-name`  | `deepseek-chat`            | 模型名称            |
| `temperature` | `0.7`                      | 温度参数            |
| `max-tokens`  | `2048`                     | 最大 token 数      |

- **Bean**: `ChatLanguageModel chatLanguageModel()` — 返回 `OpenAiChatModel` 实例

***

#### `config/EmbeddingConfig.java`

- **路径**: `config/EmbeddingConfig.java`
- **职责**: 配置文本向量化模型（Embedding Model）Bean
- **注解**: `@Configuration`
- **配置属性**（来自 `rag.embedding.*`）:

| 属性                            | 说明                                      |
| ----------------------------- | --------------------------------------- |
| `embedding.type`              | 模型类型: `ollama`(默认) / `remote` / `local` |
| `embedding.ollama.base-url`   | Ollama 服务地址（默认 localhost:11434）         |
| `embedding.ollama.model-name` | Ollama 模型名（硬编码 `mxbai-embed-large`）     |
| `embedding.remote.base-url`   | 远程 API 地址                               |
| `embedding.remote.api-key`    | 远程 API 密钥                               |
| `embedding.remote.model-name` | 远程模型名（默认 text-embedding-ada-002）        |

- **Bean**: `EmbeddingModel embeddingModel()`
  - `type=ollama` → 返回 `OllamaEmbeddingModel`（推荐，本地部署无需 API Key）
  - `type=remote` → 返回 `OpenAiEmbeddingModel`（远程 API 方案）
  - 其他 → 抛出 `IllegalArgumentException`

***

#### `config/RetrieverConfig.java`

- **路径**: `config/RetrieverConfig.java`
- **职责**: 配置 RAG 检索器 Bean —— 实现向量相似度搜索
- **注解**: `@Configuration`
- **配置属性**:
  - `rag.retrieval.top-k`: 默认 **5**（返回最相似的 top-k 个段落）
  - `rag.retrieval.min-score`: 默认 **0.5**（最低相似度阈值）
- **Bean**: `RagRetriever ragRetriever(embeddingModel, embeddingStore, topK, minScore)`

##### 内部类 `RagRetriever` 核心方法: `retrieve(String queryText)`

- **参数**: 用户查询文本
- **返回值**: `List<TextSegment>`（最相关的文本段落列表）
- **算法**:
  1. 将 queryText 通过 EmbeddingModel 转为查询向量
  2. 构建 `EmbeddingSearchRequest`（maxResults=topK, minScore=minScore）
  3. 调用 `embeddingStore.search(request)` 执行向量相似度搜索
  4. 返回匹配结果中的 TextSegment 列表

***

#### `config/VectorStoreConfig.java`

- **路径**: `config/VectorStoreConfig.java`
- **职责**: 配置向量存储和文档注册表的 Bean
- **注解**: `@Configuration`
- **Beans**:
  - `InMemoryEmbeddingStore<TextSegment> embeddingStore()` — **内存向量数据库**（LangChain4j 内置，应用重启后数据丢失）
  - `ConcurrentHashMap<String, DocumentMetadata> documentRegistry()` — 文档元数据注册表

***

#### `config/WebConfig.java`

- **路径**: `config/WebConfig.java`
- **职责**: CORS 跨域配置，允许前端访问后端 API
- **注解**: `@Configuration` + 实现 `WebMvcConfigurer`
- **CORS 规则**:
  - 映射路径: `/api/**`
  - 允许来源: `localhost:5173`, `127.0.0.1:5173`, `localhost:5174`, `127.0.0.1:5174`
  - 允许方法: GET, POST, PUT, DELETE, OPTIONS
  - 允许头: `*`（全部）
  - 允许凭证: `true`
  - 预检缓存: 3600 秒

***

### 3.6 数据模型 (Models/DTOs)

#### `model/ChatRequest.java`

- **路径**: `model/ChatRequest.java`
- **职责**: 聊天请求 DTO
- **字段**:

| 字段          | 类型     | 约束          | 说明        |
| ----------- | ------ | ----------- | --------- |
| `question`  | String | `@NotBlank` | 用户问题（必填）  |
| `sessionId` | String | —           | 会话 ID（可选） |

***

#### `model/ChatResponse.java`

- **路径**: `model/ChatResponse.java`
- **职责**: 聊天响应 DTO
- **字段**:

| 字段                   | 类型               | 说明          |
| -------------------- | ---------------- | ----------- |
| `answer`             | String           | AI 生成的完整回答  |
| `citations`          | `List<Citation>` | 引用来源列表      |
| `sessionId`          | String           | 会话 ID       |
| `hasRelevantContext` | boolean          | 是否检索到相关文档内容 |

***

#### `model/SessionInfo.java`

- **路径**: `model/SessionInfo.java`
- **职责**: 会话信息 DTO
- **字段**:

| 字段             | 类型     | 说明                    |
| -------------- | ------ | --------------------- |
| `sessionId`    | String | 会话唯一标识（16 位）          |
| `createdAt`    | long   | 创建时间戳（毫秒）             |
| `messageCount` | int    | 消息数量                  |
| `title`        | String | 会话标题（首条消息内容，最多 50 字符） |

***

#### `model/Citation.java`

- **路径**: `model/Citation.java`
- **职责**: 引用来源 DTO
- **字段**:

| 字段               | 类型     | 说明                    |
| ---------------- | ------ | --------------------- |
| `sourceFileName` | String | 来源文件名                 |
| `content`        | String | 引用的原文内容               |
| `relevanceScore` | double | 相关性评分（0\~1，目前硬编码 0.8） |

***

#### `model/DocumentMetadata.java`

- **路径**: `model/DocumentMetadata.java`
- **职责**: 文档元数据 DTO
- **字段**:

| 字段             | 类型            | 说明       |
| -------------- | ------------- | -------- |
| `documentId`   | String        | 文档唯一 ID  |
| `fileName`     | String        | 原始文件名    |
| `uploadTime`   | LocalDateTime | 上传时间     |
| `segmentCount` | int           | 切分后的段落数量 |

***

#### `model/DocumentParseResult.java`

- **路径**: `model/DocumentParseResult.java`
- **职责**: 文档解析中间结果 DTO
- **字段**:

| 字段             | 类型               | 说明                        |
| -------------- | ---------------- | ------------------------- |
| `documentId`   | String           | 生成的文档 ID                  |
| `fileName`     | String           | 原始文件名                     |
| `segmentCount` | int              | 解析出的段落数                   |
| `elapsedMs`    | long             | 解析耗时（毫秒）                  |
| `documents`    | `List<Document>` | LangChain4j Document 对象列表 |

***

#### `model/ApiResponse.java`

- **路径**: `model/ApiResponse.java`
- **职责**: **统一 API 响应包装类**，所有 REST 接口的返回格式
- **泛型**: `ApiResponse<T>`
- **字段**:

| 字段          | 类型     | 说明                |
| ----------- | ------ | ----------------- |
| `code`      | int    | 状态码（200=成功，其他=失败） |
| `message`   | String | 提示消息              |
| `data`      | T      | 业务数据（泛型）          |
| `timestamp` | long   | 响应时间戳             |

- **静态工厂方法**:
  - `ok(T data)` → `{ code:200, message:"success", data, timestamp }`
  - `ok(String message, T data)` → 自定义成功消息
  - `error(int code, String message)` → 错误响应（无 data）

***

### 3.7 文档解析器 (Parsers)

#### `parser/FileParser.java` (接口)

- **路径**: `parser/FileParser.java`
- **职责**: 文件解析器**抽象接口**，定义统一的解析契约
- **方法**:

| 方法签名                                              | 返回值              | 说明                                 |
| ------------------------------------------------- | ---------------- | ---------------------------------- |
| `parse(InputStream inputStream, String fileName)` | `List<Document>` | 解析文件输入流，返回 LangChain4j Document 列表 |
| `supports(String fileExtension)`                  | `boolean`        | 判断是否支持该文件扩展名                       |

***

#### `parser/FileParserFactory.java`

- **路径**: `parser/FileParserFactory.java`
- **职责**: 解析器**工厂类**，根据文件扩展名选择合适的解析器
- **注解**: `@Component`
- **支持的扩展名**: `pdf`, `docx`, `txt`, `md`, `markdown`
- **核心方法**:
  - `getParser(fileExtension)`: 遍历所有已注册的 parser，找到第一个 `supports(ext)==true` 的解析器；找不到则抛 `FileParseException`
  - `getSupportedExtensions()`: 返回支持的扩展名列表
  - `isSupported(fileExtension)`: 判断是否支持某扩展名
- **初始化**: `@PostConstruct init()` 中通过 Spring 自动注入的 `List<FileParser>` 构建 parserMap

***

#### `parser/impl/PdfFileParser.java`

- **路径**: `parser/impl/PdfFileParser.java`
- **职责**: **PDF 文件解析器**，使用 Apache PDFBox 逐页提取文本
- **注解**: `@Component`
- **支持格式**: `pdf`
- **解析策略**:
  1. 使用 `PDDocument.load(inputStream)` 加载 PDF
  2. 使用 `PDFTextStripper` 按位置排序提取文本（`setSortByPosition(true)`）
  3. **逐页解析**：每页生成一个 Document 对象
  4. Metadata 包含: `file_name`, `source_type=pdf`, `page_number`, `total_pages`
- **依赖**: Apache PDFBox 库

***

#### `parser/impl/TextFileParser.java`

- **路径**: `parser/impl/TextFileParser.java`
- **职责**: **纯文本/Markdown 文件解析器**，按空行分割段落
- **注解**: `@Component`
- **支持格式**: `txt`, `md`, `markdown`
- **解析策略**:
  1. 使用 `BufferedReader` 按 UTF-8 逐行读取
  2. **以空行为分隔符**：连续非空行合并为一个段落
  3. 自动识别 Markdown 文件（`.md`/`.markdown` 后缀）
  4. Metadata 包含: `file_name`, `source_type`(text/markdown), `segment_index`

***

#### `parser/impl/WordFileParser.java`

- **路径**: `parser/impl/WordFileParser.java`
- **职责**: **Word 文档(.docx)解析器**，使用 Apache POI 提取段落和表格
- **注解**: `@Component`
- **支持格式**: `docx`
- **解析策略**:
  1. 使用 `XWPFDocument` 加载 docx 文件
  2. **段落解析**：遍历所有 `XWPFParagraph`，以空段落作为分割点合并连续文本
  3. **表格解析**：额外提取所有表格内容，每个表格生成一个独立 Document（标注 `content_type=table`）
  4. Metadata 包含: `file_name`, `source_type=docx`, `paragraph_index` 或 `content_type=table`
- **依赖**: Apache POI (poi-ooxml) 库

***

### 3.8 Prompt 模板

#### `service/prompt/RagPromptTemplate.java`

- **路径**: `service/prompt/RagPromptTemplate.java`
- **职责**: RAG 问答的 **Prompt 模板**，定义系统提示词和用户消息格式
- **设计模式**: 工具类（`private` 构造函数，`final` 类）

##### 系统提示词 (SYSTEM\_PROMPT):

```
你是一个智能文档助手。请根据以下检索到的文档片段回答用户问题。

要求：
1. 仅基于提供的【参考文档内容】回答，不要编造文档中没有的信息
2. 如果参考文档中没有相关信息，请明确告知"在提供的文档中未找到相关内容"
3. 回答时引用来源文档名称和关键信息
4. 使用中文回答，语言简洁专业
5. 如果用户的问题与文档无关，礼貌地引导回文档内容范围
```

##### 用户消息构建: `buildUserMessage(question, context)`

- 有上下文时: 组合「参考文档内容」+「用户问题」+ 指令
- 无上下文时: 附带「未检索到相关文档内容」提示

***

### 3.9 异常处理

#### `exception/GlobalExceptionHandler.java`

- **路径**: `exception/GlobalExceptionHandler.java`
- **职责**: **全局异常处理器**，统一捕获和处理所有 Controller 层抛出的异常
- **注解**: `@RestControllerAdvice`（对所有 @RestController 生效）

***

##### 异常映射表

| 异常类型                              | HTTP 状态码                  | 响应 Code | 处理逻辑                  |
| --------------------------------- | ------------------------- | ------- | --------------------- |
| `FileParseException`              | 400 Bad Request           | 400     | 返回解析错误消息              |
| `SessionNotFoundException`        | 404 Not Found             | 404     | 返回会话不存在消息             |
| `MaxUploadSizeExceededException`  | 413 Payload Too Large     | 413     | 返回"文件大小超出限制（最大 10MB）" |
| `IllegalArgumentException`        | 400 Bad Request           | 400     | 返回参数错误消息              |
| `HttpMessageNotWritableException` | 500 Internal Server Error | 500     | JSON 序列化失败，附带完整原因链    |
| `Exception` (兜底)                  | 500 Internal Server Error | 500     | 通用服务器错误，附带完整原因链       |

- **辅助方法**: `getCauseMessage(Throwable e)` — 递归获取完整的异常原因链

***

#### `exception/FileParseException.java`

- **路径**: `exception/FileParseException.java`
- **职责**: 文件解析自定义异常
- **字段**: `fileName`（文件名）, `message`（错误描述）, `cause`（原始异常）

#### `exception/SessionNotFoundException.java`

- **路径**: `exception/SessionNotFoundException.java`
- **职责**: 会话不存在自定义异常
- **字段**: `sessionId`（会话 ID）

***

## 四、系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                           浏览器 (Frontend)                          │
│                                                                     │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │  Sidebar.vue │    │   ChatPanel.vue   │    │ DocumentUploader │  │
│  │  (会话列表)    │───▶│  (消息区+输入框)   │    │  + DocumentList  │  │
│  └──────────────┘    └────────┬─────────┘    └────────┬─────────┘  │
│                               │                        │            │
│  ┌────────────────────────────┼────────────────────────┼──────────┐ │
│  │                      Pinia Stores                     │          │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌────────────────┐  │          │ │
│  │  │ chat.ts  │  │  session.ts  │  │  document.ts   │  │          │ │
│  │  └────┬─────┘  └──────┬───────┘  └───────┬────────┘  │          │ │
│  │       │               │                  │            │          │ │
│  │  ┌────▼───────────────▼──────────────────▼───────────┐│          │ │
│  │  │  utils/sse.ts (SSE流)  │  api/request.ts (REST)   ││          │ │
│  │  └──────────────────────┴───────────────────────────┘│          │ │
│ └────────────────────────────────────────────────────────┼──────────┘ │
│                                                          │           │
│                                                   Vite Proxy (/api)  │
│                                                          ▼           │
└──────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Backend (:8080)                   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │                     Controllers (REST)                        │     │
│  │  ┌─────────────────┐ ┌──────────────┐ ┌──────────────────┐  │     │
│  │  │ ChatController   │ │SessionCtrl   │ │DocumentController │  │     │
│  │  │ /api/chat/stream │ │/api/sessions │ │/api/documents    │  │     │
│  │  │ (SSE 流式)       │ │ (CRUD)       │ │ (上传/列表/删除)  │  │     │
│  │  └────────┬────────┘ └──────┬───────┘ └────────┬─────────┘  │     │
│  └───────────┼─────────────────┼─────────────────┼────────────┘     │
│              │                 │                 │                   │
│  ┌───────────▼─────────────────▼─────────────────▼────────────┐     │
│  │                      Services (业务逻辑)                      │     │
│  │  ┌────────────────┐  ┌─────────────┐  ┌─────────────────┐  │     │
│  │  │ RagChatService │  │SessionMgr   │  │DocParseService  │  │     │
│  │  │ (RAG问答核心)   │  │(会话管理)    │  │(解析调度)       │  │     │
│  │  └───────┬────────┘  └─────────────┘  └────────┬────────┘  │     │
│  │          │                                       │           │     │
│  │  ┌───────▼──────────────────────────────────────▼────────┐  │     │
│  │  │ VectorizationService (切分→向量化→存储)                  │  │     │
│  │  └───────────────────────┬───────────────────────────────┘  │     │
│  └──────────────────────────┼──────────────────────────────────┘     │
│                             │                                        │
│  ┌──────────────────────────▼──────────────────────────────────┐     │
│  │                    Parsers (文件解析)                          │     │
│  │  ┌────────────┐ ┌─────────────┐ ┌──────────────────┐        │     │
│  │  │PdfFileParser│ │TextFileParser│ │WordFileParser    │        │     │
│  │  │ (PDFBox)    │ │(BufferedReader)│ │(Apache POI)     │        │     │
│  │  └────────────┘ └─────────────┘ └──────────────────┘        │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │                    Config (配置层)                           │     │
│  │  LlmConfig │ EmbeddingConfig │ RetrieverConfig │ WebConfig │     │
│  └───────────────────────────┬────────────────────────────────┘     │
│                              │                                       │
│  ┌───────────────────────────▼────────────────────────────────┐     │
│  │                    External Services                         │     │
│  │  ┌─────────────────────┐  ┌─────────────────────────────┐  │     │
│  │  │  DeepSeek API (LLM) │  │  Ollama (Embedding Model)   │  │     │
│  │  │  OpenAI Compatible  │  │  Local: localhost:11434     │  │     │
│  │  └─────────────────────┘  └─────────────────────────────┘  │     │
│  │                                                             │     │
│  │  ┌─────────────────────────────────────────────────────┐   │     │
│  │  │  InMemoryEmbeddingStore (向量数据库, 内存存储)        │   │     │
│  │  └─────────────────────────────────────────────────────┘   │     │
│  └─────────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────────┘
```

***

## 五、API 接口总览

| 方法         | 路径                           | 控制器                | 功能        | Content-Type          |
| ---------- | ---------------------------- | ------------------ | --------- | --------------------- |
| **POST**   | `/api/chat/stream`           | ChatController     | 流式问答（SSE） | `text/event-stream`   |
| **POST**   | `/api/sessions/create`       | SessionController  | 创建新会话     | `application/json`    |
| **GET**    | `/api/sessions/list`         | SessionController  | 获取会话列表    | `application/json`    |
| **DELETE** | `/api/sessions/{id}`         | SessionController  | 删除会话      | `application/json`    |
| **GET**    | `/api/sessions/{id}/history` | SessionController  | 获取会话历史    | `application/json`    |
| **POST**   | `/api/documents/upload`      | DocumentController | 上传文档      | `multipart/form-data` |
| **GET**    | `/api/documents/list`        | DocumentController | 文档列表      | `application/json`    |
| **DELETE** | `/api/documents/clear`       | DocumentController | 清空所有文档    | `application/json`    |
| **DELETE** | `/api/documents/{id}`        | DocumentController | 删除指定文档    | `application/json`    |

***

## 六、数据流向图

### 6.1 问答流程（核心链路）

```
用户输入问题
    │
    ▼
ChatPanel.handleSend()
    │
    ▼
chatStore.sendMessage(question)
    ├─► addUserMessage()          → messages 追加 user 消息
    ├─► addAssistantPlaceholder() → messages 追加 assistant 占位(loading)
    │
    ▼
fetchSSE('/api/chat/stream', {question, sessionId})
    │  (POST + ReadableStream 解析)
    ▼
ChatController.streamChat(@RequestBody ChatRequest)
    │
    ├─► sessionManager.createSession()  (若需要)
    │
    ▼ (新线程)
RagChatService.chat(question, sessionId)
    │
    ├─① ragRetriever.retrieve(question)
    │       │
    │       ▼
    │   EmbeddingModel.embed(query) → 向量
    │   InMemoryEmbeddingStore.search() → Top-K 相似段落
    │
    ├─② buildFullPrompt(context + question)
    │
    ├─③ chatLanguageModel.chat(prompt)
    │       │
    │       ▼
    │   DeepSeek API → AI 回答文本
    │
    ├─④ sessionManager.addMessage() (保存历史)
    │
    ▼
返回 ChatResponse(answer, citations, sessionId)
    │
    ▼
SseEmitter 分块发送:
    ├─ N × chunk 事件 (每 20 字符)
    ├─ N × citation 事件 (引用来源)
    └─ 1 × done 事件 (完成标记)
    │
    ▼
前端 for-await-of 循环消费:
    ├─ chunk → updateAssistantContent()  → 实时更新 UI
    ├─ citation → 收集到数组
    └─ done   → updateAssistantDone()    → 完成 + 保存 sessionId
```

### 6.2 文档上传流程

```
用户选择文件 → 确认上传
    │
    ▼
documentStore.uploadFile(file)
    │  POST /api/documents/upload (FormData)
    ▼
DocumentController.uploadDocument(@RequestParam MultipartFile)
    │
    ├─① DocumentParseService.parse(file)
    │       │
    │       ▼
    │   FileParserFactory.getParser(ext) → 选择解析器
    │   parser.parse(inputStream, fileName) → List<Document>
    │       │
    │       ├─ PdfFileParser  → PDFBox 逐页提取
    │       ├─ TextFileParser → BufferedReader 按空行分段
    │       └─ WordFileParser → POI 段落+表格提取
    │
    ├─② VectorizationService.vectorize(parseResult)
    │       │
    │       ├─ DocumentSplitService.split() → 固定大小切分(1000字+重叠200)
    │       ├─ EmbeddingModel.embedAll(segments) → 批量向量化
    │       └─ InMemoryEmbeddingStore.add(embedding, segment) → 存储向量
    │
    ▼
返回 DocumentMetadata (documentId, fileName, segmentCount)
    │
    ▼
前端 DocumentList 刷新展示
```

***

## 七、快速定位指南

### 我想修改...

| 需求              | 修改文件                                                | 说明                           |
| --------------- | --------------------------------------------------- | ---------------------------- |
| 修改 UI 布局/样式     | `ChatView.vue`, `ChatPanel.vue`, `Sidebar.vue`      | Vue 组件 + SCSS                |
| 修改消息渲染效果        | `MessageBubble.vue`                                 | Markdown 渲染/气泡样式             |
| 修改 Prompt 提示词   | `RagPromptTemplate.java`                            | 系统/用户提示词模板                   |
| 切换 LLM 模型       | `application.yml` → `langchain4j.open-ai.*`         | 或修改 `LlmConfig.java`         |
| 切换 Embedding 模型 | `application.yml` → `rag.embedding.*`               | ollama/remote/local 三种模式     |
| 调整 RAG 检索参数     | `application.yml` → `rag.retrieval.*`               | top-k / min-score            |
| 调整文档切分大小        | `application.yml` → `rag.splitting.*`               | max-segment-size / overlap   |
| 新增文件格式支持        | 新建 `XxxFileParser.java` implements `FileParser`     | + 在 `FileParserFactory` 自动发现 |
| 修改 SSE 分块大小     | `ChatController.java` → `chunkSize = 20`            | 当前每 20 字符一个 chunk            |
| 修改 API 错误处理     | `GlobalExceptionHandler.java`                       | 异常 → HTTP 状态码映射              |
| 修改 CORS 策略      | `WebConfig.java`                                    | 允许的域名/方法/头                   |
| 修改前端代理配置        | `vite.config.ts` → server.proxy                     | 目标地址/端口                      |
| 修改会话消息上限        | `SessionManager.java` → MAX\_MESSAGES\_PER\_SESSION | 当前 20 条                      |
| 添加新的 API 接口     | 新建 `XxxController.java`                             | + 对应 Service 方法              |

***

## 八、注意事项

1. **向量存储为内存存储**（InMemoryEmbeddingStore），应用重启后所有已上传文档的向量数据会丢失，需重新上传
2. **Ollama 需要提前安装并拉取 Embedding 模型**: `ollama pull mxbai-embed-large`
3. **DeepSeek API Key** 需要在 `application.yml` 中配置或通过环境变量 `DEEPSEEK_API_KEY` 注入
4. **SSE 超时时间**设置为 120 秒（`SseEmitter(120000L)`），复杂问题可能需要更长时间
5. **前端 v-for key 策略**使用动态拼接（id + contentLength + loading），确保 Vue 能正确检测消息变更并触发重渲染
6. **会话 ID** 为 16 位短 UUID（去掉连字符后截取），在前端显示时会截取前 8 位

