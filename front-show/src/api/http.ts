import axios from "axios";
import type { CommonResp } from "@/types/api";

const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() || "/super-host";

export const http = axios.create({
  baseURL,
  timeout: 15000
});

export async function unwrapResp<T>(request: Promise<{ data: CommonResp<T> }>): Promise<T> {
  const response = await request;
  const payload = response.data;
  if (!payload.success) {
    throw new Error(payload.message || "请求失败");
  }
  return payload.content;
}

export { baseURL };
