<script setup lang="ts">
import { computed, ref } from "vue";
import { NButton, NIcon, NInput, NModal, NPopconfirm, NScrollbar, NTooltip } from "naive-ui";
import { AddOutline, ChatbubbleEllipsesOutline, PencilOutline, TrashOutline } from "@vicons/ionicons5";
import type { SessionVO } from "@/types/chat";
import { formatDateTime } from "@/utils/date";

interface Props {
  sessions: SessionVO[];
  currentSessionId: string | null;
  busy: boolean;
  loading: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  create: [];
  select: [sessionId: string];
  rename: [sessionId: string, title: string];
  remove: [sessionId: string];
}>();

const renameVisible = ref(false);
const renameTargetId = ref("");
const renameTitle = ref("");

const hasSessions = computed(() => props.sessions.length > 0);

function openRename(session: SessionVO): void {
  renameTargetId.value = session.id;
  renameTitle.value = session.title;
  renameVisible.value = true;
}

function confirmRename(): void {
  const next = renameTitle.value.trim();
  if (!renameTargetId.value || !next) {
    return;
  }
  emit("rename", renameTargetId.value, next);
  renameVisible.value = false;
}
</script>

<template>
  <div class="session-sidebar">
    <header class="sidebar-header">
      <div class="brand">
        <n-icon size="18"><ChatbubbleEllipsesOutline /></n-icon>
        <span>会话</span>
      </div>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button circle tertiary :disabled="busy" @click="emit('create')">
            <template #icon>
              <n-icon><AddOutline /></n-icon>
            </template>
          </n-button>
        </template>
        新建会话
      </n-tooltip>
    </header>

    <n-scrollbar class="session-scroll">
      <div v-if="!hasSessions" class="empty">
        <span>暂无会话</span>
      </div>

      <ul v-else class="session-list">
        <li
          v-for="session in sessions"
          :key="session.id"
          class="session-row"
          :class="{ active: currentSessionId === session.id }"
        >
          <button class="session-main" :disabled="busy" @click="emit('select', session.id)">
            <span class="title">{{ session.title || "新会话" }}</span>
            <span class="meta">{{ formatDateTime(session.updatedAt) }}</span>
          </button>
          <div class="actions">
            <n-tooltip trigger="hover">
              <template #trigger>
                <n-button text :disabled="busy" @click.stop="openRename(session)">
                  <template #icon>
                    <n-icon><PencilOutline /></n-icon>
                  </template>
                </n-button>
              </template>
              重命名
            </n-tooltip>
            <n-popconfirm @positive-click="emit('remove', session.id)">
              <template #trigger>
                <n-button text :disabled="busy" @click.stop>
                  <template #icon>
                    <n-icon><TrashOutline /></n-icon>
                  </template>
                </n-button>
              </template>
              删除该会话？
            </n-popconfirm>
          </div>
        </li>
      </ul>
    </n-scrollbar>

    <n-modal
      v-model:show="renameVisible"
      preset="card"
      title="重命名会话"
      :style="{ width: '420px' }"
      :mask-closable="false"
    >
      <div class="rename-panel">
        <n-input v-model:value="renameTitle" placeholder="请输入新标题" maxlength="60" />
      </div>
      <template #footer>
        <div class="rename-footer">
          <n-button @click="renameVisible = false">取消</n-button>
          <n-button type="primary" :disabled="!renameTitle.trim()" @click="confirmRename">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<style scoped lang="scss">
.session-sidebar {
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
  min-height: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  border-bottom: 1px solid var(--line-color);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text-primary);
}

.session-scroll {
  min-height: 0;
}

.empty {
  min-height: 120px;
  display: grid;
  place-items: center;
  color: var(--text-muted);
  font-size: 13px;
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.session-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  border-radius: 8px;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.session-row.active {
  border-color: var(--accent-line);
  background: var(--accent-soft);
}

.session-main {
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px;
  border-radius: 8px;
  width: 100%;
  cursor: pointer;
  display: grid;
  gap: 5px;
}

.session-main:disabled {
  cursor: not-allowed;
}

.title {
  color: var(--text-primary);
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  color: var(--text-muted);
  font-size: 12px;
}

.actions {
  display: inline-flex;
  align-items: center;
  padding-right: 4px;
}

.rename-panel {
  padding: 4px 0;
}

.rename-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 900px) {
  .session-sidebar {
    max-height: 40vh;
  }
}
</style>
