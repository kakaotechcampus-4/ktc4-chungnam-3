// 미확정: 백엔드 계약 없음
// 앱 모델. 에이전트 실행 궤적.

export type TraceEventKind = string;

export interface TraceEvent {
  kind: TraceEventKind;
  detail: string;
  occurredAt: string;
}

export interface ExecutionTrace {
  proposalId: string;
  events: TraceEvent[];
}
