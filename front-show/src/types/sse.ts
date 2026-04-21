export type StreamEventName =
  | "start"
  | "answer_delta"
  | "step_planned"
  | "tool_started"
  | "tool_finished"
  | "step_failed"
  | "degrade_started"
  | "final_answer"
  | "done"
  | "error";

export interface StreamEventEnvelope {
  event: string;
  rawData: string;
  data: unknown;
}
