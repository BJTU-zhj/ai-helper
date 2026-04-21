<script setup lang="ts">
import { computed } from "vue";
import { NTag } from "naive-ui";

interface Props {
  title: string;
  streaming: boolean;
}

const props = defineProps<Props>();

const stateLabel = computed(() => (props.streaming ? "生成中" : "就绪"));
const tagType = computed(() => (props.streaming ? "warning" : "success"));
</script>

<template>
  <header class="chat-header">
    <div class="title-wrap">
      <h1>{{ title }}</h1>
      <p>AI Kitchen Brain Service</p>
    </div>
    <n-tag :type="tagType" size="small" round>{{ stateLabel }}</n-tag>
  </header>
</template>

<style scoped lang="scss">
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line-color);
}

.title-wrap {
  min-width: 0;
}

h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
