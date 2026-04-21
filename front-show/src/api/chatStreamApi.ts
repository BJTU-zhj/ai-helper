import type { StreamEventEnvelope } from "@/types/sse";
import { baseURL } from "@/api/http";
import { parseMaybeJson, SseParser } from "@/utils/sse";

interface StreamChatParams {
  sessionId: string;
  message: string;
  signal?: AbortSignal;
  onEvent: (event: StreamEventEnvelope) => void;
}

function isAbsoluteUrl(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

function trimRightSlash(input: string): string {
  return input.replace(/\/+$/, "");
}

function normalizeBaseUrl(input: string): string {
  if (!input) {
    return "/super-host";
  }
  if (isAbsoluteUrl(input)) {
    return trimRightSlash(input);
  }
  return trimRightSlash(input.startsWith("/") ? input : `/${input}`);
}

function buildStreamUrl(sessionId: string, message: string): string {
  const normalizedBase = normalizeBaseUrl(baseURL);
  const encodedSessionId = encodeURIComponent(sessionId);
  const encodedMessage = encodeURIComponent(message);
  return `${normalizedBase}/brain/chat/stream/${encodedSessionId}/${encodedMessage}`;
}

export async function streamChat(params: StreamChatParams): Promise<void> {
  const url = buildStreamUrl(params.sessionId, params.message);
  const response = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "text/event-stream"
    },
    signal: params.signal
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    const detail = body ? ` ${body}` : "";
    throw new Error(`流式请求失败(${response.status})${detail}`);
  }

  if (!response.body) {
    throw new Error("后端未返回可读取的流");
  }

  const parser = new SseParser();
  const decoder = new TextDecoder("utf-8");
  const reader = response.body.getReader();

  const emitRawEvent = (eventName: string, rawData: string): void => {
    params.onEvent({
      event: eventName,
      rawData,
      data: parseMaybeJson(rawData)
    });
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      const textChunk = decoder.decode(value, { stream: true });
      const parsedEvents = parser.push(textChunk);
      for (const evt of parsedEvents) {
        emitRawEvent(evt.event, evt.data);
      }
    }

    const tail = decoder.decode();
    if (tail) {
      const parsedTail = parser.push(tail);
      for (const evt of parsedTail) {
        emitRawEvent(evt.event, evt.data);
      }
    }

    const flushed = parser.flush();
    for (const evt of flushed) {
      emitRawEvent(evt.event, evt.data);
    }
  } finally {
    reader.releaseLock();
  }
}
