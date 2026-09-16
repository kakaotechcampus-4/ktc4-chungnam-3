// 알림 수신 구독. 발생원(FCM/로컬)을 이 파일 안에서만 갈라 감춘다.

export function startListening(): () => void {
  return () => {};
}
