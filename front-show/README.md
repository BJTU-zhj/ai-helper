# front-show

`ai-kitchen-brain-service` 的前端聊天工作台，支持多会话管理、历史消息加载和 SSE 流式输出。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router
- Pinia
- Naive UI
- Axios（普通 REST）
- fetch + ReadableStream（SSE 流式）
- markdown-it + highlight.js

## 运行方式

```bash
npm install
npm run dev
```

生产构建：

```bash
npm run build
```

## 环境变量

默认读取 `.env`：

```env
VITE_API_BASE_URL=/super-host
```

说明：
- 推荐通过网关访问：`http://localhost:8080/super-host`
- Vite 开发代理已配置：`/super-host -> http://localhost:8080`
- 直连后端可改为：`VITE_API_BASE_URL=http://localhost:8082`

## 已接入接口

目标服务：`ai-kitchen-brain-service`，前缀 `/brain`

- `POST /brain/sessions`
- `GET /brain/sessions`
- `GET /brain/sessions/{sessionId}`
- `GET /brain/sessions/{sessionId}/messages`
- `PUT /brain/sessions/{sessionId}/title`
- `DELETE /brain/sessions/{sessionId}`
- `GET /brain/chat/stream/{sessionId}/{message}`（核心）

## SSE 处理说明

- 手动解析 `event:` / `data:` 协议块
- 处理 `answer_delta`、`final_answer`、`done`、`error`
- 兼容 ReAct 事件：`step_planned`、`tool_started`、`tool_finished`、`step_failed`、`degrade_started`
- 支持 `AbortController` 停止生成

## 已知限制

- 构建产物体积较大（Naive UI + Markdown + Highlight），Vite 会有 chunk size warning。
- 如果后端未启动，前端会显示错误提示，但无法完成真实联调。
