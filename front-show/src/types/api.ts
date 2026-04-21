export interface CommonResp<T> {
  success: boolean;
  message?: string;
  content: T;
}
