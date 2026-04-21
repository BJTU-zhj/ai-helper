export type DateLike = string | number | Date | null | undefined;

export interface SessionVO {
  id: string;
  title: string;
  createdAt: DateLike;
  updatedAt: DateLike;
}

export interface ChatMessageVO {
  id: number;
  sessionId: string;
  turnNo: number;
  role: string;
  content: string;
  createdAt: DateLike;
}

export interface SessionDetailVO {
  session: SessionVO;
  messages: ChatMessageVO[];
}

export type UiMessageRole = "user" | "assistant";

export interface UiProcessEvent {
  id: string;
  event: string;
  message: string;
  timestamp?: string;
}

export interface UiChatMessage {
  id: string;
  sessionId: string;
  role: UiMessageRole;
  content: string;
  createdAt: number;
  streaming?: boolean;
  processEvents?: UiProcessEvent[];
}
