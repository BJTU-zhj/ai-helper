<script setup lang="ts">
import { computed } from "vue";
import type { UiChatMessage } from "@/types/chat";
import { renderMarkdown } from "@/utils/markdown";
import { formatTime } from "@/utils/date";

interface Props {
  message: UiChatMessage;
}

const props = defineProps<Props>();

const htmlContent = computed(() => {
  if (props.message.role !== "assistant") {
    return "";
  }
  return renderMarkdown(props.message.content || "");
});
</script>

<template>
  <article class="message-item" :class="message.role">
    <header class="meta">
      <span class="role">{{ message.role === "user" ? "你" : "助手" }}</span>
      <time>{{ formatTime(message.createdAt) }}</time>
    </header>

    <div v-if="message.role === 'assistant'" class="bubble assistant-content">
      <div v-if="message.content" class="markdown" v-html="htmlContent" />
      <div v-else class="placeholder" :class="{ flashing: message.streaming }">正在生成...</div>
      <ul v-if="message.processEvents && message.processEvents.length > 0" class="process-list">
        <li v-for="event in message.processEvents" :key="event.id">{{ event.message }}</li>
      </ul>
    </div>

    <div v-else class="bubble user-content">
      <p>{{ message.content }}</p>
    </div>
  </article>
</template>

<style scoped lang="scss">
.message-item {
  max-width: min(90%, 760px);
  display: grid;
  gap: 6px;
}

.message-item.user {
  justify-self: end;
}

.message-item.assistant {
  justify-self: start;
}

.meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.role {
  font-weight: 600;
}

.bubble {
  border-radius: 8px;
  padding: 12px 14px;
  border: 1px solid var(--line-color);
  line-height: 1.65;
}

.assistant-content {
  background: #f7f9fc;
}

.user-content {
  background: #e8f2ff;
  border-color: #c7ddff;
}

.user-content p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.markdown {
  color: var(--text-primary);
  word-break: break-word;
}

.markdown :deep(p) {
  margin: 0 0 10px;
}

.markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown :deep(pre) {
  margin: 10px 0;
  border-radius: 8px;
  overflow: auto;
}

.markdown :deep(code) {
  font-family: "Consolas", "SFMono-Regular", monospace;
}

.process-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.process-list li {
  font-size: 12px;
  color: #44607f;
  background: #edf4ff;
  border: 1px solid #d4e6ff;
  border-radius: 8px;
  padding: 5px 8px;
}

.placeholder {
  color: var(--text-muted);
  font-size: 13px;
}

.placeholder.flashing {
  animation: blink 1.2s ease-in-out infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}
</style>
