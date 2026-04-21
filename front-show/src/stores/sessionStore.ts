import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { sessionApi } from "@/api/sessionApi";
import { streamChat } from "@/api/chatStreamApi";
import type {
  ChatMessageVO,
  SessionVO,
  UiChatMessage,
  UiProcessEvent
} from "@/types/chat";
import type { StreamEventEnvelope } from "@/types/sse";
import { toTimestamp } from "@/utils/date";

type PageStatus = "idle" | "loading" | "streaming" | "error";

function makeId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

function extractString(data: unknown, key: string): string {
  if (data && typeof data === "object" && key in data) {
    const value = (data as Record<string, unknown>)[key];
    if (typeof value === "string") {
      return value;
    }
    if (typeof value === "number" || typeof value === "boolean") {
      return String(value);
    }
  }
  return "";
}

function toUiMessage(message: ChatMessageVO): UiChatMessage {
  const role = message.role?.toLowerCase() === "user" ? "user" : "assistant";
  return {
    id: `history-${message.id}`,
    sessionId: message.sessionId,
    role,
    content: message.content || "",
    createdAt: toTimestamp(message.createdAt),
    streaming: false,
    processEvents: []
  };
}

function sortSessions(list: SessionVO[]): SessionVO[] {
  return [...list].sort((a, b) => toTimestamp(b.updatedAt) - toTimestamp(a.updatedAt));
}

function mergeFinalAnswer(currentContent: string, finalAnswer: string): string {
  const trimmedCurrent = currentContent.trim();
  const trimmedFinal = finalAnswer.trim();
  if (!trimmedCurrent) {
    return finalAnswer;
  }
  if (!trimmedFinal) {
    return currentContent;
  }
  if (trimmedCurrent === trimmedFinal) {
    return currentContent;
  }
  if (trimmedFinal.startsWith(trimmedCurrent)) {
    return finalAnswer;
  }
  if (trimmedCurrent.startsWith(trimmedFinal)) {
    return currentContent;
  }
  return `${currentContent}\n\n${finalAnswer}`;
}

function summarizeProcess(eventName: string, data: unknown): string {
  const stepNo = extractString(data, "stepNo");
  const message = extractString(data, "message");
  const toolName = extractString(data, "toolName");
  const reason = extractString(data, "reason");

  if (eventName === "step_planned") {
    const thought = extractString(data, "thoughtSummary");
    return thought ? `${stepNo ? `第${stepNo}步` : "步骤"}：${thought}` : `${stepNo ? `第${stepNo}步` : "步骤"}已规划`;
  }
  if (eventName === "tool_started") {
    return `调用工具 ${toolName || ""}`.trim();
  }
  if (eventName === "tool_finished") {
    return `工具完成 ${toolName || ""}`.trim();
  }
  if (eventName === "step_failed") {
    return extractString(data, "errorMessage") || "步骤执行失败";
  }
  if (eventName === "degrade_started") {
    return reason ? `进入降级：${reason}` : "进入降级回答";
  }
  if (eventName === "start") {
    return message || "开始生成";
  }
  return message || eventName;
}

export const useSessionStore = defineStore("session", () => {
  const sessions = ref<SessionVO[]>([]);
  const currentSessionId = ref<string | null>(null);
  const messages = ref<UiChatMessage[]>([]);
  const status = ref<PageStatus>("idle");
  const loadingSessions = ref(false);
  const loadingMessages = ref(false);
  const errorMessage = ref("");
  const stopHint = ref("");
  const streamController = ref<AbortController | null>(null);

  const currentSession = computed<SessionVO | null>(() => {
    if (!currentSessionId.value) {
      return null;
    }
    return sessions.value.find((session) => session.id === currentSessionId.value) || null;
  });

  const currentSessionTitle = computed(() => currentSession.value?.title || "新会话");
  const isStreaming = computed(() => status.value === "streaming");
  const isBusy = computed(() => loadingMessages.value || isStreaming.value);

  function setError(message: string): void {
    status.value = "error";
    errorMessage.value = message;
  }

  function upsertSession(target: SessionVO): void {
    const index = sessions.value.findIndex((session) => session.id === target.id);
    if (index >= 0) {
      sessions.value[index] = target;
    } else {
      sessions.value.unshift(target);
    }
    sessions.value = sortSessions(sessions.value);
  }

  function updateMessage(messageId: string, updater: (message: UiChatMessage) => UiChatMessage): void {
    messages.value = messages.value.map((message) => {
      if (message.id !== messageId) {
        return message;
      }
      return updater(message);
    });
  }

  function appendProcessEvent(messageId: string, event: string, content: string, timestamp?: string): void {
    if (!content) {
      return;
    }
    const processEvent: UiProcessEvent = {
      id: makeId("process"),
      event,
      message: content,
      timestamp
    };
    updateMessage(messageId, (message) => ({
      ...message,
      processEvents: [...(message.processEvents || []), processEvent]
    }));
  }

  async function initialize(): Promise<void> {
    await refreshSessions();
    if (sessions.value.length > 0) {
      await selectSession(sessions.value[0].id);
    }
  }

  async function refreshSessions(): Promise<void> {
    loadingSessions.value = true;
    try {
      const list = await sessionApi.listSessions();
      sessions.value = sortSessions(list);
      if (currentSessionId.value && !sessions.value.some((s) => s.id === currentSessionId.value)) {
        currentSessionId.value = null;
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : "会话列表加载失败");
    } finally {
      loadingSessions.value = false;
    }
  }

  async function ensureCurrentSession(): Promise<string> {
    if (currentSessionId.value) {
      return currentSessionId.value;
    }
    const created = await sessionApi.createSession();
    upsertSession(created);
    currentSessionId.value = created.id;
    messages.value = [];
    return created.id;
  }

  async function createSession(title?: string): Promise<void> {
    if (isStreaming.value) {
      return;
    }
    errorMessage.value = "";
    try {
      const created = await sessionApi.createSession(title);
      upsertSession(created);
      currentSessionId.value = created.id;
      messages.value = [];
      status.value = "idle";
    } catch (error) {
      setError(error instanceof Error ? error.message : "创建会话失败");
    }
  }

  async function selectSession(sessionId: string): Promise<void> {
    if (!sessionId || isStreaming.value) {
      return;
    }
    status.value = "loading";
    loadingMessages.value = true;
    errorMessage.value = "";
    stopHint.value = "";
    try {
      const detail = await sessionApi.getSessionDetail(sessionId);
      currentSessionId.value = detail.session.id;
      upsertSession(detail.session);
      messages.value = (detail.messages || []).map(toUiMessage);
      status.value = "idle";
    } catch (error) {
      setError(error instanceof Error ? error.message : "加载会话详情失败");
    } finally {
      loadingMessages.value = false;
    }
  }

  async function refreshCurrentMessages(sessionId: string, preserveProcessFromId?: string): Promise<void> {
    const preservedProcessEvents = preserveProcessFromId
      ? messages.value.find((message) => message.id === preserveProcessFromId)?.processEvents || []
      : [];
    const history = await sessionApi.listSessionMessages(sessionId);
    const nextMessages = history.map(toUiMessage);
    if (preservedProcessEvents.length > 0) {
      let lastAssistantIndex = -1;
      for (let index = nextMessages.length - 1; index >= 0; index--) {
        if (nextMessages[index].role === "assistant") {
          lastAssistantIndex = index;
          break;
        }
      }
      if (lastAssistantIndex >= 0) {
        nextMessages[lastAssistantIndex] = {
          ...nextMessages[lastAssistantIndex],
          processEvents: preservedProcessEvents
        };
      }
    }
    messages.value = nextMessages;
  }

  async function renameSession(sessionId: string, title: string): Promise<void> {
    if (isStreaming.value) {
      return;
    }
    const nextTitle = title.trim();
    if (!nextTitle) {
      setError("标题不能为空");
      return;
    }
    try {
      const updated = await sessionApi.updateSessionTitle(sessionId, nextTitle);
      upsertSession(updated);
      status.value = "idle";
    } catch (error) {
      setError(error instanceof Error ? error.message : "重命名失败");
    }
  }

  async function deleteSession(sessionId: string): Promise<void> {
    if (isStreaming.value) {
      return;
    }
    try {
      await sessionApi.deleteSession(sessionId);
      sessions.value = sessions.value.filter((session) => session.id !== sessionId);
      if (currentSessionId.value === sessionId) {
        const next = sessions.value[0];
        if (next) {
          await selectSession(next.id);
        } else {
          currentSessionId.value = null;
          messages.value = [];
        }
      }
      status.value = "idle";
    } catch (error) {
      setError(error instanceof Error ? error.message : "删除会话失败");
    }
  }

  function handleStreamEvent(event: StreamEventEnvelope, assistantMessageId: string): boolean {
    const payload = event.data;
    const timestamp = extractString(payload, "timestamp");
    const isReactEvent = [
      "start",
      "step_planned",
      "tool_started",
      "tool_finished",
      "step_failed",
      "degrade_started"
    ].includes(event.event);

    if (isReactEvent) {
      appendProcessEvent(assistantMessageId, event.event, summarizeProcess(event.event, payload), timestamp);
    }

    if (event.event === "answer_delta") {
      const delta = extractString(payload, "delta");
      if (delta) {
        updateMessage(assistantMessageId, (message) => ({
          ...message,
          content: message.content + delta
        }));
      }
      return false;
    }

    if (event.event === "final_answer") {
      const finalAnswer = extractString(payload, "finalAnswer");
      if (finalAnswer) {
        updateMessage(assistantMessageId, (message) => ({
          ...message,
          content: mergeFinalAnswer(message.content, finalAnswer)
        }));
      }
      return false;
    }

    if (event.event === "error") {
      const errorText = extractString(payload, "errorMessage") || "流式返回错误";
      setError(errorText);
      appendProcessEvent(assistantMessageId, "error", errorText, timestamp);
      return false;
    }

    if (event.event === "done") {
      appendProcessEvent(assistantMessageId, "done", "回答完成", timestamp);
      return true;
    }

    if (
      event.event !== "start" &&
      event.event !== "step_planned" &&
      event.event !== "tool_started" &&
      event.event !== "tool_finished" &&
      event.event !== "step_failed" &&
      event.event !== "degrade_started"
    ) {
      console.info("[SSE] unknown event", event.event, event.data);
    }

    return false;
  }

  async function sendMessage(rawContent: string): Promise<void> {
    const content = rawContent.trim();
    if (!content || isStreaming.value) {
      return;
    }

    errorMessage.value = "";
    stopHint.value = "";
    status.value = "streaming";

    let sessionId = "";
    try {
      sessionId = await ensureCurrentSession();
    } catch (error) {
      setError(error instanceof Error ? error.message : "创建会话失败");
      return;
    }

    const userMessage: UiChatMessage = {
      id: makeId("user"),
      sessionId,
      role: "user",
      content,
      createdAt: Date.now(),
      streaming: false,
      processEvents: []
    };
    const assistantMessage: UiChatMessage = {
      id: makeId("assistant"),
      sessionId,
      role: "assistant",
      content: "",
      createdAt: Date.now(),
      streaming: true,
      processEvents: []
    };

    messages.value.push(userMessage, assistantMessage);

    const controller = new AbortController();
    streamController.value = controller;
    let doneReceived = false;
    let streamFailed = false;

    try {
      await streamChat({
        sessionId,
        message: content,
        signal: controller.signal,
        onEvent: (event) => {
          const done = handleStreamEvent(event, assistantMessage.id);
          if (done) {
            doneReceived = true;
          }
          if (event.event === "error") {
            streamFailed = true;
          }
        }
      });

      if (!doneReceived) {
        appendProcessEvent(assistantMessage.id, "done", "连接结束");
      }
      if (!streamFailed) {
        status.value = "idle";
      }
    } catch (error) {
      if (isAbortError(error)) {
        stopHint.value = "已停止生成";
        appendProcessEvent(assistantMessage.id, "stop", "已停止生成");
        status.value = "idle";
      } else {
        setError(error instanceof Error ? error.message : "流式请求失败");
      }
    } finally {
      updateMessage(assistantMessage.id, (message) => ({
        ...message,
        streaming: false
      }));
      streamController.value = null;
      if (sessionId && currentSessionId.value === sessionId) {
        try {
          await refreshCurrentMessages(sessionId, assistantMessage.id);
        } catch (error) {
          console.warn("refresh messages failed", error);
        }
      }
      await refreshSessions();
    }
  }

  function stopStreaming(): void {
    if (streamController.value) {
      streamController.value.abort();
    }
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    currentSessionTitle,
    messages,
    status,
    loadingSessions,
    loadingMessages,
    isStreaming,
    isBusy,
    errorMessage,
    stopHint,
    initialize,
    refreshSessions,
    createSession,
    selectSession,
    renameSession,
    deleteSession,
    sendMessage,
    stopStreaming
  };
});
