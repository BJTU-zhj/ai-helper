<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { NButton, NIcon, NScrollbar } from "naive-ui";
import { ArrowDownOutline } from "@vicons/ionicons5";
import type { UiChatMessage } from "@/types/chat";
import MessageItem from "@/components/MessageItem.vue";
import EmptyState from "@/components/EmptyState.vue";

interface Props {
  messages: UiChatMessage[];
}

const props = defineProps<Props>();
const scrollbarRef = ref<InstanceType<typeof NScrollbar> | null>(null);
const shouldStickToBottom = ref(true);

const watchKey = computed(() =>
  props.messages
    .map((msg) => `${msg.id}:${msg.content.length}:${msg.processEvents?.length || 0}:${msg.streaming ? 1 : 0}`)
    .join("|")
);

function scrollToBottom(force = false): void {
  if (!force && !shouldStickToBottom.value) {
    return;
  }
  nextTick(() => {
    scrollbarRef.value?.scrollTo({
      top: Number.MAX_SAFE_INTEGER,
      behavior: "smooth"
    });
  });
}

function handleScroll(event: Event): void {
  const target = event.target as HTMLElement | null;
  if (!target) {
    return;
  }
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
  shouldStickToBottom.value = distanceToBottom < 72;
}

watch(watchKey, () => {
  scrollToBottom();
});
</script>

<template>
  <section class="message-list-wrap">
    <n-scrollbar ref="scrollbarRef" class="message-list" @scroll="handleScroll">
      <div v-if="messages.length === 0" class="empty-container">
        <EmptyState />
      </div>
      <div v-else class="message-stack">
        <MessageItem v-for="message in messages" :key="message.id" :message="message" />
      </div>
    </n-scrollbar>
    <n-button
      v-if="messages.length > 0 && !shouldStickToBottom"
      class="jump-btn"
      circle
      tertiary
      @click="scrollToBottom(true)"
    >
      <template #icon>
        <n-icon><ArrowDownOutline /></n-icon>
      </template>
    </n-button>
  </section>
</template>

<style scoped lang="scss">
.message-list-wrap {
  position: relative;
  min-height: 0;
  height: 100%;
}

.message-list {
  height: 100%;
}

.empty-container {
  min-height: 100%;
  display: grid;
}

.message-stack {
  padding: 18px;
  display: grid;
  gap: 12px;
}

.jump-btn {
  position: absolute;
  right: 16px;
  bottom: 16px;
  box-shadow: 0 4px 12px rgba(18, 44, 73, 0.2);
}
</style>
