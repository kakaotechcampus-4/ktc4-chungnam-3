// 알림 응답의 data 에서 서버 알림 id 를 꺼낸다. 화면을 직접 열지 않는다(여는 것은 app/notifications/notificationOpener).
// 서버(FirebasePushSender)는 data.notificationId 에 UUID 문자열을 넣는다. 문자열이 아니거나 비어 있으면 null.

export function toNotificationId(data: unknown): string | null {
    if (typeof data !== "object" || data == null) return null;
    if (!("notificationId" in data)) return null;
    const id = data.notificationId;
    return typeof id === "string" && id.trim() !== "" ? id : null;
}
