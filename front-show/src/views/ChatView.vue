<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { NAlert } from "naive-ui";
import AppShell from "@/components/AppShell.vue";
import SessionSidebar from "@/components/SessionSidebar.vue";
import ChatHeader from "@/components/ChatHeader.vue";
import MessageList from "@/components/MessageList.vue";
import ChatInput from "@/components/ChatInput.vue";
import { useSessionStore } from "@/stores/sessionStore";

const store = useSessionStore();
const inputText = ref("");

const busy = computed(() => store.isBusy);

async function handleSend(): Promise<void> {
  const payload = inputText.value;
  if (!payload.trim()) {
    return;
  }
  inputText.value = "";
  await store.sendMessage(payload);
}

onMounted(async () => {
  await store.initialize();
});
</script>

<template>
  <div class="page-bg">
    <AppShell>
      <template #sidebar>
        <SessionSidebar
          :sessions="store.sessions"
          :current-session-id="store.currentSessionId"
          :busy="busy"
          :loading="store.loadingSessions"
          @create="store.createSession()"
          @select="store.selectSession"
          @rename="store.renameSession"
          @remove="store.deleteSession"
        />
      </template>

      <div class="chat-panel">
        <ChatHeader :title="store.currentSessionTitle" :streaming="store.isStreaming" />
        <div class="message-area">
          <MessageList :messages="store.messages" />
        </div>
        <div class="status-area">
          <n-alert v-if="store.errorMessage" type="error" :show-icon="false" closable>
            {{ store.errorMessage }}
          </n-alert>
          <n-alert v-if="store.stopHint" type="warning" :show-icon="false" closable>
            {{ store.stopHint }}
          </n-alert>
        </div>
        <ChatInput
          v-model="inputText"
          :disabled="store.isStreaming"
          :streaming="store.isStreaming"
          @send="handleSend"
          @stop="store.stopStreaming"
        />
      </div>
    </AppShell>
  </div>
</template>

<style scoped lang="scss">
.page-bg {
  min-height: 100vh;
  background:
    radial-gradient(circle at 8% 8%, rgba(71, 128, 206, 0.14), transparent 34%),
    radial-gradient(circle at 94% 92%, rgba(124, 171, 124, 0.14), transparent 30%),
    #f1f4f9;
}

.chat-panel {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  min-height: 0;
}

.message-area {
  min-height: 0;
}

.status-area {
  display: grid;
  gap: 8px;
  padding: 6px 14px 0;
}
</style>
