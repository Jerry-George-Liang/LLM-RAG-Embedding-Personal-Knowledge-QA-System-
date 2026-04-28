# Vue 3 + TypeScript 实现 SSE 流式聊天

> 本文详细介绍如何在 Vue 3 中使用 TypeScript 实现 SSE（Server-Sent Events）流式聊天，包括 SSE 解析、Pinia 状态管理和 Markdown 实时渲染。

## 📖 目录

1. [SSE 简介](#1-sse-简介)
2. [SSE vs WebSocket](#2-sse-vs-websocket)
3. [前端 SSE 实现](#3-前端-sse-实现)
4. [Pinia 状态管理](#4-pinia-状态管理)
5. [Markdown 实时渲染](#5-markdown-实时渲染)
6. [完整代码示例](#6-完整代码示例)

***

## 1. SSE 简介

### 什么是 SSE？

**Server-Sent Events (SSE)** 是一种让服务器主动向浏览器推送数据的技术。它基于 HTTP 协议，使用 `text/event-stream` 内容类型。

### SSE 数据格式

```
event: message
data: {"type":"content","data":"你好"}

event: message
data: {"type":"content","data":"，我是 AI"}

data: [DONE]
```

每个事件由以下部分组成：

- `event:` - 事件类型（可选）
- `data:` - 事件数据
- 空行 - 事件分隔符

***

## 2. SSE vs WebSocket

| 特性    | SSE         | WebSocket        |
| ----- | ----------- | ---------------- |
| 通信方向  | 单向（服务器→客户端） | 双向               |
| 协议    | HTTP/HTTPS  | ws\:// / wss\:// |
| 连接方式  | 持久 HTTP 连接  | 独立 TCP 连接        |
| 自动重连  | ✅ 内置支持      | ❌ 需手动实现          |
| 防火墙友好 | ✅ 通常不被阻止    | ⚠️ 可能被阻止         |
| 二进制数据 | ❌ 仅支持文本     | ✅ 支持二进制          |
| 浏览器支持 | 现代浏览器均支持    | 广泛支持             |
| 适用场景  | 聊天推送、实时通知   | 游戏、协作工具          |

### 选择建议

- **聊天/推送场景** → SSE 更简单、更适合
- **需要客户端发送消息的场景** → WebSocket
- **需要双向实时通信** → WebSocket

***

## 3. 前端 SSE 实现

### 3.1 SSE 工具函数

```typescript
/**
 * SSE 流式聊天配置接口
 */
interface SseStreamChatOptions {
  /** 接收到数据块时的回调 */
  onChunk: (chunk: string) => void
  /** 流结束时回调 */
  onComplete?: () => void
  /** 错误回调 */
  onError?: (error: Error) => void
}

/**
 * SSE 事件数据类型
 */
interface SseEventData {
  type: 'content' | 'citation' | 'done' | 'error'
  data: any
}

/**
 * SSE 流式聊天
 */
export async function sseStreamChat(
  sessionId: string,
  message: string,
  options: SseStreamChatOptions
): Promise<void> {
  const { onChunk, onComplete, onError } = options

  try {
    // 发起 POST 请求
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({ sessionId, message })
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    // 获取响应流
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法获取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    // 持续读取流数据
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) break

      // 解码数据块
      buffer += decoder.decode(value, { stream: true })
      
      // 处理缓冲区中的完整事件
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim()
          
          if (dataStr === '[DONE]') {
            onComplete?.()
            return
          }

          try {
            const eventData: SseEventData = JSON.parse(dataStr)
            
            switch (eventData.type) {
              case 'content':
                onChunk(eventData.data)
                break
              case 'citation':
                console.log('收到引用:', eventData.data)
                break
              case 'error':
                throw new Error(eventData.data)
            }
          } catch {
            // 非 JSON 数据直接作为内容处理
            if (dataStr) {
              onChunk(dataStr)
            }
          }
        }
      }
    }

    onComplete?.()
  } catch (error) {
    console.error('SSE 请求失败:', error)
    onError?.(error as Error)
    throw error
  }
}
```

### 3.2 AsyncGenerator 版本

对于需要更精细控制的场景，可以使用 AsyncGenerator：

```typescript
/**
 * SSE AsyncGenerator 版本
 * 适用于需要迭代处理的场景
 */
export async function* sseGenerator(
  sessionId: string,
  message: string
): AsyncGenerator<string, void, unknown> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({ sessionId, message })
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法获取响应流')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const dataStr = line.slice(5).trim()
        
        if (dataStr === '[DONE]') return

        try {
          const parsed = JSON.parse(dataStr)
          if (parsed.type === 'content') {
            yield parsed.data
          }
        } catch {
          if (dataStr) yield dataStr
        }
      }
    }
  }
}

// 使用示例
async function example() {
  for await (const chunk of sseGenerator(sessionId, '你好')) {
    console.log('收到:', chunk)
  }
}
```

***

## 4. Pinia 状态管理

### 4.1 聊天 Store

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatMessage } from '@/types'

export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // 添加消息
  const addMessage = (message: Omit<ChatMessage, 'id' | 'timestamp'>): string => {
    const newMessage: ChatMessage = {
      id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date().toISOString(),
      ...message
    }
    messages.value.push(newMessage)
    return newMessage.id
  }

  // 更新消息内容
  const updateMessageContent = (messageId: string, content: string) => {
    const message = messages.value.find(m => m.id === messageId)
    if (message) {
      message.content = content
    }
  }

  // 追加消息内容（用于 SSE 流式更新）
  const appendMessageContent = (messageId: string, chunk: string) => {
    const message = messages.value.find(m => m.id === messageId)
    if (message) {
      message.content += chunk
    }
  }

  // 清空消息
  const clearMessages = () => {
    messages.value = []
  }

  return {
    messages,
    isLoading,
    error,
    addMessage,
    updateMessageContent,
    appendMessageContent,
    clearMessages
  }
})
```

### 4.2 会话 Store

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/request'
import type { SessionInfo } from '@/types'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<SessionInfo[]>([])
  const currentSessionId = ref<string | null>(null)

  const currentSession = computed(() => {
    return sessions.value.find(s => s.id === currentSessionId.value) || null
  })

  // 创建会话
  const createSession = async (title?: string): Promise<string> => {
    const response = await chatApi.createSession(title || '新对话')
    if (response.data.code === 200) {
      const newSession: SessionInfo = response.data.data
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      return newSession.id
    }
    throw new Error(response.data.message)
  }

  // 切换会话
  const switchSession = async (sessionId: string) => {
    currentSessionId.value = sessionId
  }

  // 删除会话
  const deleteSession = async (sessionId: string) => {
    await chatApi.deleteSession(sessionId)
    const index = sessions.value.findIndex(s => s.id === sessionId)
    if (index > -1) {
      sessions.value.splice(index, 1)
    }
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = sessions.value[0]?.id || null
    }
  }

  // 加载会话列表
  const loadSessions = async () => {
    const response = await chatApi.getSessions()
    if (response.data.code === 200) {
      sessions.value = response.data.data || []
      if (!currentSessionId.value && sessions.value.length > 0) {
        currentSessionId.value = sessions.value[0].id
      }
    }
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    createSession,
    switchSession,
    deleteSession,
    loadSessions
  }
})
```

***

## 5. Markdown 实时渲染

### 5.1 安装依赖

```bash
npm install marked highlight.js
npm install -D @types/marked
```

### 5.2 配置 Marked

```typescript
import { marked } from 'marked'
import hljs from 'highlight.js'

// 配置 marked 选项
marked.setOptions({
  highlight: (code: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,      // 转换换行符
  gfm: true           // GitHub 风格 Markdown
})

// 渲染 Markdown
export const renderMarkdown = (content: string): string => {
  if (!content) return ''
  return marked.parse(content) as string
}
```

### 5.3 消息气泡组件

```vue
<template>
  <div class="message-bubble" :class="{ 'is-user': message.role === 'user' }">
    <div class="message-avatar">
      <el-avatar :size="36">
        {{ message.role === 'user' ? '我' : 'AI' }}
      </el-avatar>
    </div>

    <div class="message-content-wrapper">
      <div class="message-meta">
        <span class="message-role">
          {{ message.role === 'user' ? '我' : 'AI 助手' }}
        </span>
        <span class="message-time">{{ formatTime(message.timestamp) }}</span>
      </div>

      <div class="message-content">
        <!-- 用户消息 -->
        <div v-if="message.role === 'user'" class="content-text">
          {{ message.content }}
        </div>

        <!-- AI 消息：Markdown 渲染 -->
        <div v-else class="content-markdown" v-html="renderedContent" />
      </div>

      <!-- 引用卡片 -->
      <CitationCard 
        v-if="message.citations?.length" 
        :citations="message.citations"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import type { ChatMessage } from '@/types'
import CitationCard from './CitationCard.vue'

const props = defineProps<{
  message: ChatMessage
}>()

// 实时渲染 Markdown
const renderedContent = computed(() => {
  if (!props.message.content) return ''
  return marked.parse(props.message.content)
})

// 格式化时间
const formatTime = (timestamp: string): string => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped lang="scss">
.message-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  &.is-user {
    flex-direction: row-reverse;
    
    .message-content {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      
      .content-text,
      ::v-deep(.content-markdown) {
        color: white;
      }
    }
  }

  .message-content {
    padding: 12px 16px;
    background: white;
    border-radius: 16px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

    .content-markdown {
      line-height: 1.6;
      
      // Markdown 样式
      ::v-deep(pre) {
        background: #282c34;
        border-radius: 8px;
        padding: 12px;
        overflow-x: auto;
        
        code {
          color: #abb2bf;
        }
      }
      
      ::v-deep(code) {
        background: #f5f7fa;
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Fira Code', monospace;
      }
      
      ::v-deep(blockquote) {
        border-left: 4px solid #409eff;
        background: #f5f7fa;
        padding: 8px 16px;
        margin: 12px 0;
      }
    }
  }
}
</style>
```

***

## 6. 完整代码示例

### 6.1 ChatView 组件

```vue
<template>
  <div class="chat-view">
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <el-empty description="开始对话吧！" />
      </div>
      
      <div v-else class="messages-list">
        <MessageBubble
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
        />
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <ChatInput @send="handleSend" :disabled="isLoading" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { useSessionStore } from '@/stores/session'
import { sseStreamChat } from '@/utils/sse'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ChatInput from '@/components/chat/ChatInput.vue'

const chatStore = useChatStore()
const sessionStore = useSessionStore()
const { messages } = storeToRefs(chatStore)
const isLoading = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听消息变化
watch(messages, () => {
  scrollToBottom()
}, { deep: true })

// 发送消息
const handleSend = async (content: string) => {
  const sessionId = sessionStore.currentSessionId || await sessionStore.createSession()
  
  // 添加用户消息
  chatStore.addMessage({
    role: 'user',
    content,
    citations: []
  })

  isLoading.value = true

  try {
    // 添加 AI 消息占位
    const aiMessageId = chatStore.addMessage({
      role: 'assistant',
      content: '',
      citations: []
    })

    // SSE 流式调用
    await sseStreamChat(sessionId, content, {
      onChunk: (chunk: string) => {
        chatStore.appendMessageContent(aiMessageId, chunk)
      },
      onComplete: () => {
        isLoading.value = false
      },
      onError: (error: Error) => {
        chatStore.updateMessageContent(aiMessageId, '抱歉，发生了错误。')
        isLoading.value = false
      }
    })
  } catch (error) {
    console.error('发送消息失败:', error)
    isLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;

    .empty-state {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
    }
  }

  .chat-input-area {
    padding: 16px 20px;
    background: white;
    border-top: 1px solid #e4e7ed;
  }
}
</style>
```

***

## 📚 总结

本文完整介绍了 Vue 3 + TypeScript 实现 SSE 流式聊天的方案：

1. **SSE 原理**：基于 HTTP 的服务器推送技术
2. **前端实现**：使用 Fetch API + ReadableStream
3. **状态管理**：Pinia 管理消息和会话状态
4. **Markdown 渲染**：marked + highlight.js 实现代码高亮
5. **流式更新**：实时追加消息内容，滚动跟随

***

> 💡 提示：生产环境建议添加错误重试机制、心跳保活、连接状态显示等功能，提升用户体验。

