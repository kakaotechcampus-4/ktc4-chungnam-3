// 미확정: 백엔드 계약 없음
// 앱 모델. 꺼내기 결과. 제안 유형은 2분기로 닫지 않는다.

export type ProposalId = string;

export type ProposalKind = string;

export interface ProposalItem {
  contentId: string;
  placeId: string | null;
  order: number | null;
}

export interface Proposal {
  id: ProposalId;
  kind: ProposalKind;
  reason: string;
  items: ProposalItem[];
}
