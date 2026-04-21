<script setup lang="ts">
import { computed } from "vue";
import { NButton, NIcon, NInput, NTooltip } from "naive-ui";
import { PaperPlaneOutline, StopCircleOutline } from "@vicons/ionicons5";

interface Props {
  modelValue: string;
  disabled: boolean;
  streaming: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  send: [];
  stop: [];
}>();

const canSend = computed(() => !props.disabled && !!props.modelValue.trim());

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    if (canSend.value) {
      emit("send");
    }
  }
}
</script>

<template>
  <div class="chat-input">
    <n-input
      :value="modelValue"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 8 }"
      placeholder="输入问题，Enter 发送，Shift + Enter 换行"
      :disabled="disabled"
      @update:value="emit('update:modelValue', $event)"
      @keydown="handleKeydown"
    />
    <div class="actions">
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button type="primary" :disabled="!canSend" @click="emit('send')">
            <template #icon>
              <n-icon><PaperPlaneOutline /></n-icon>
            </template>
            发送
          </n-button>
        </template>
        发送消息
      </n-tooltip>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button
            type="warning"
            ghost
            :disabled="!streaming"
            @click="emit('stop')"
          >
            <template #icon>
              <n-icon><StopCircleOutline /></n-icon>
            </template>
            停止
          </n-button>
        </template>
        停止生成
      </n-tooltip>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-input {
  display: grid;
  gap: 10px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--line-color);
  background: #fbfcfe;
}

.actions {
  display: inline-flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
