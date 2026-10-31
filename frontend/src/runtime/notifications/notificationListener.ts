// 알림 누름 구독. 발생원(FCM/로컬)을 이 파일 안에서만 감춘다: 어느 쪽이든 data.notificationId 하나로 넘긴다.
// 리스너를 먼저 붙이고, 앱을 연 알림(종료 상태에서 누름)의 응답을 읽고 지운다.
// 이벤트와 마지막 응답이 겹쳐도 이번 실행에서 넘긴 notificationId 는 다시 넘기지 않는다.
// 앱(App 마운트)이 한 번 부른다. 화면을 직접 열지 않는다(onOpen 이 app 의 opener 다). 헤드리스 태스크에서는 부르지 않는다.
import {
    addNotificationResponseListener,
    takeLastNotificationResponse,
} from "../../shared/permissions/notifications";
import { toNotificationId } from "./router";

const handled = new Set<string>();

export function startListening(
    onOpen: (notificationId: string) => void,
): () => void {
    const receive = (data: unknown) => {
        const notificationId = toNotificationId(data);
        if (notificationId == null || handled.has(notificationId)) return;
        handled.add(notificationId);
        onOpen(notificationId);
    };
    const stop = addNotificationResponseListener(receive);
    receive(takeLastNotificationResponse());
    return stop;
}
