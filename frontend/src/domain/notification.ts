// 미확정: 백엔드 계약 없음
// 앱 모델. 알림 페이로드. 발생원(FCM/로컬)과 무관한 형태.

export type NotificationKind = string;

export interface NotificationPayload {
  kind: NotificationKind;
  title: string;
  body: string;
  targetId: string | null;
}
