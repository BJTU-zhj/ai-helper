import { http, unwrapResp } from "@/api/http";
import type { ChatMessageVO, SessionDetailVO, SessionVO } from "@/types/chat";

export const sessionApi = {
  createSession(title?: string): Promise<SessionVO> {
    const body = title && title.trim() ? { title: title.trim() } : undefined;
    return unwrapResp(http.post("/brain/sessions", body));
  },

  listSessions(): Promise<SessionVO[]> {
    return unwrapResp(http.get("/brain/sessions"));
  },

  getSessionDetail(sessionId: string): Promise<SessionDetailVO> {
    return unwrapResp(http.get(`/brain/sessions/${encodeURIComponent(sessionId)}`));
  },

  listSessionMessages(sessionId: string): Promise<ChatMessageVO[]> {
    return unwrapResp(http.get(`/brain/sessions/${encodeURIComponent(sessionId)}/messages`));
  },

  updateSessionTitle(sessionId: string, title: string): Promise<SessionVO> {
    return unwrapResp(
      http.put(`/brain/sessions/${encodeURIComponent(sessionId)}/title`, { title: title.trim() })
    );
  },

  async deleteSession(sessionId: string): Promise<boolean> {
    const deleted = await unwrapResp(http.delete(`/brain/sessions/${encodeURIComponent(sessionId)}`));
    return !!deleted;
  }
};
