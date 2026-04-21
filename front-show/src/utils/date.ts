import type { DateLike } from "@/types/chat";

function toDate(value: DateLike): Date | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function toTimestamp(value: DateLike): number {
  const date = toDate(value);
  return date ? date.getTime() : Date.now();
}

export function formatTime(value: DateLike): string {
  const date = toDate(value);
  if (!date) {
    return "--:--";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(date);
}

export function formatDateTime(value: DateLike): string {
  const date = toDate(value);
  if (!date) {
    return "";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(date);
}
