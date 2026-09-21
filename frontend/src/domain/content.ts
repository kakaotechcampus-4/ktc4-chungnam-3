// 미확정: 백엔드 계약 없음
// 앱 모델. 저장물과 그 분석 상태.

export type ContentId = string;

export type AnalysisState = string;

export interface Content {
  id: ContentId;
  sourceUrl: string;
  title: string;
  summary: string;
  analysisState: AnalysisState;
  savedAt: string;
}
