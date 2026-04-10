# front-show

用于对接 Java Spring Boot 后端“编程小助手”接口的前端演示项目。

## 技术栈

- Vue 3（`<script setup>`）
- Vite
- Axios（普通请求：用于加载运行时配置）
- Fetch Stream（用于流式读取后端输出）

## 安装与运行

```bash
npm install
npm run dev
```

生产构建：

```bash
npm run build
```

## 后端地址配置

默认后端地址：`http://localhost:8080`

可在 `public/config.json` 中修改：

```json
{
  "apiBaseUrl": "http://localhost:8080"
}
```

前端启动后会通过 Axios 请求 `/config.json` 读取该地址。

## 开发环境跨域说明

如果你通过 `npm run dev` 启动前端，页面地址通常是 `http://localhost:5173`，直接请求 `http://localhost:8080` 可能被浏览器 CORS 策略拦截并出现 `Failed to fetch`。

本项目已在 `vite.config.js` 中配置开发代理：

- `/aihelper` -> `http://localhost:8080`

因此开发环境会自动使用相对路径走代理，避免跨域问题。

## 接口说明

页面调用流式接口：

- Method: `GET`
- URL: `/aihelper/chatStream/{memoryId}/{message}`

示例：

`/aihelper/chatStream/user-001/重庆的天气怎么样明天`

注意事项：

- 后端返回 `text/html;charset=UTF-8`，非标准 SSE，因此前端使用 `fetch + response.body.getReader()`。
- `memoryId` 和 `message` 会使用 `encodeURIComponent` 编码，避免中文、空格和特殊字符导致 URL 解析错误。

## 功能点

- 流式问答：助手回答逐步追加显示
- 会话隔离：通过 `memoryId` 区分不同会话
- 状态提示：空闲 / 请求中 / 流式输出中 / 出错
- 错误处理：网络错误、4xx/5xx、流式中断
- 手动停止：点击“停止生成”时显示“已停止生成”
