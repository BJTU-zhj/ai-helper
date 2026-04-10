<script setup>
import axios from "axios";
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";

const DEFAULT_API_BASE_URL = "http://localhost:8080";

const memoryId = ref("user-001");
const question = ref("");
const apiBaseUrl = ref(DEFAULT_API_BASE_URL);
const messages = ref([]);
const status = ref("idle");
const errorMessage = ref("");
const messagesContainer = ref(null);

let streamController = null;

const isBusy = computed(() => status.value === "requesting" || status.value === "streaming");

const statusText = computed(() => {
  if (status.value === "requesting") return "请求中";
  if (status.value === "streaming") return "流式输出中";
  if (status.value === "stopped") return "空闲";
  if (status.value === "error") return "出错";
  return "空闲";
});

const statusClass = computed(() => {
  if (status.value === "requesting" || status.value === "streaming") return "status-warn";
  if (status.value === "error") return "status-error";
  return "status-ok";
});

function scrollToBottom() {
  nextTick(() => {
    if (!messagesContainer.value) return;
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  });
}

function pushMessage(role, content) {
  messages.value.push({
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    createdAt: new Date()
  });
  scrollToBottom();
}

function normalizeBaseUrl(url) {
  return url.endsWith("/") ? url.slice(0, -1) : url;
}

function shouldUseDevProxy(baseUrl) {
  if (!import.meta.env.DEV) return false;
  const normalized = normalizeBaseUrl(baseUrl).toLowerCase();
  return normalized === "http://localhost:8080" || normalized === "http://127.0.0.1:8080";
}

function buildStreamUrl(baseUrl, currentMemoryId, currentMessage) {
  const encodedMemoryId = encodeURIComponent(currentMemoryId);
  const encodedMessage = encodeURIComponent(currentMessage);
  const path = `/aihelper/chatStream/${encodedMemoryId}/${encodedMessage}`;
  if (shouldUseDevProxy(baseUrl)) {
    return path;
  }
  return `${normalizeBaseUrl(baseUrl)}${path}`;
}

async function loadRuntimeConfig() {
  try {
    const { data } = await axios.get("/config.json", { timeout: 3000 });
    if (typeof data?.apiBaseUrl === "string" && data.apiBaseUrl.trim()) {
      apiBaseUrl.value = data.apiBaseUrl.trim();
    }
  } catch {
    apiBaseUrl.value = DEFAULT_API_BASE_URL;
  }
}

function stopStreaming() {
  if (!streamController) return;
  streamController.abort();
}

async function ask() {
  const trimmedMemoryId = memoryId.value.trim();
  const trimmedQuestion = question.value.trim();

  if (!trimmedMemoryId) {
    status.value = "error";
    errorMessage.value = "memoryId 不能为空";
    return;
  }

  if (!trimmedQuestion) {
    status.value = "error";
    errorMessage.value = "请输入问题内容";
    return;
  }

  errorMessage.value = "";
  status.value = "requesting";
  pushMessage("user", trimmedQuestion);

  const assistantMessage = {
    id: `${Date.now()}-assistant`,
    role: "assistant",
    content: "",
    createdAt: new Date()
  };
  messages.value.push(assistantMessage);
  scrollToBottom();

  const url = buildStreamUrl(apiBaseUrl.value, trimmedMemoryId, trimmedQuestion);
  const decoder = new TextDecoder("utf-8");
  streamController = new AbortController();

  try {
    const response = await fetch(url, {
      method: "GET",
      signal: streamController.signal
    });

    if (!response.ok) {
      const bodyText = await response.text().catch(() => "");
      throw new Error(`请求失败 (${response.status})${bodyText ? `: ${bodyText}` : ""}`);
    }

    if (!response.body) {
      throw new Error("后端未返回可读取的数据流");
    }

    status.value = "streaming";
    const reader = response.body.getReader();

    while (true) {
      let chunk;
      try {
        chunk = await reader.read();
      } catch (streamError) {
        if (streamController?.signal?.aborted) {
          status.value = "stopped";
          errorMessage.value = "已停止生成";
          break;
        }
        throw streamError;
      }

      const { value, done } = chunk;
      if (done) {
        break;
      }

      if (value) {
        assistantMessage.content += decoder.decode(value, { stream: true });
        scrollToBottom();
      }
    }

    const tail = decoder.decode();
    if (tail) {
      assistantMessage.content += tail;
      scrollToBottom();
    }

    if (status.value !== "stopped") {
      status.value = "idle";
    }
  } catch (error) {
    if (error?.name === "AbortError") {
      status.value = "stopped";
      errorMessage.value = "已停止生成";
      return;
    }

    status.value = "error";
    if (error instanceof TypeError && error.message.includes("Failed to fetch")) {
      errorMessage.value = "请求失败：浏览器拦截了请求（可能是跨域/CORS）。开发环境请确认 Vite 代理已生效。";
    } else {
      errorMessage.value = error instanceof Error ? error.message : "请求失败，请稍后重试";
    }
  } finally {
    streamController = null;
    question.value = "";
  }
}

onMounted(async () => {
  await loadRuntimeConfig();
});

onBeforeUnmount(() => {
  if (streamController) {
    streamController.abort();
  }
});
</script>

<template>
  <div class="page">
    <main class="card">
      <header class="top">
        <h1>AI 编程小助手</h1>
        <p>支持普通问答与流式输出</p>
      </header>

      <section class="form">
        <label class="field">
          <span>memoryId</span>
          <input v-model="memoryId" type="text" placeholder="请输入会话 memoryId" :disabled="isBusy" />
        </label>

        <label class="field">
          <span>问题</span>
          <textarea
            v-model="question"
            rows="4"
            placeholder="请输入你想问的问题..."
            :disabled="isBusy"
          />
        </label>
      </section>

      <section class="actions">
        <button class="primary" :disabled="isBusy" @click="ask">提问</button>
        <button class="danger" :disabled="!isBusy" @click="stopStreaming">停止生成</button>
      </section>

      <section class="messages" ref="messagesContainer">
        <div v-if="messages.length === 0" class="empty">还没有消息，开始你的提问吧。</div>

        <article v-for="msg in messages" :key="msg.id" class="message" :class="msg.role">
          <div class="role">{{ msg.role === "user" ? "用户" : "助手" }}</div>
          <div class="bubble">{{ msg.content || "..." }}</div>
          <time class="time">{{ msg.createdAt.toLocaleTimeString("zh-CN", { hour12: false }) }}</time>
        </article>
      </section>

      <footer class="status">
        <span :class="['state', statusClass]">状态：{{ statusText }}</span>
        <span class="base-url">后端：{{ apiBaseUrl }}</span>
      </footer>
      <p v-if="errorMessage" class="error-tip">{{ errorMessage }}</p>
    </main>
  </div>
</template>
