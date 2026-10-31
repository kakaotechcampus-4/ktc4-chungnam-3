// 알림 API 의 목. 서버 계약 모양이라 API 모듈 옆에 둔다(geofencesMock 과 같은 자리).
// 목 서버가 처음 받은 도착 보고마다(geofencesMock) 알림을 하나 만든다. 서버처럼 위치 동의가 없으면 만들지 않는다.
// 서버의 꺼내기 판단(에이전트 · 회원 쿨다운)은 흉내 내지 않는다(시연에서 반복할 수 있게). 그 장소를 가진 저장물이 있으면 늘 PLACE 제안 1개다.
// 푸시는 보내지 않는다. 시연 도구의 "알림 열기"로 연다. 이번 실행 동안 메모리에 남는다.
// 상세는 서버처럼 읽을 때 저장물을 다시 확인한다(지웠으면 items 가 빈다). 없는 id 는 404, 열람은 멱등.
import { ApiError } from "./client";
import { isMockLocationConsentActive } from "./consents";
import type { NotificationDetailResponse } from "./notifications";
import { mockSavedPlace, mockUuid } from "./personalSavesMock";

const MOCK_DELAY_MS = 600;

type MockNotification = {
    notificationId: string;
    triggerEventId: string;
    personalSaveId: string;
    placeId: string;
    title: string;
    body: string;
    sentAt: string;
    openedAt: string | null;
};

const notifications: MockNotification[] = [];

// geofencesMock 이 새 도착 보고를 기록할 때 부른다.
export function mockCreateNotification(event: {
    placeId: string;
    triggerEventId: string;
}): void {
    if (!isMockLocationConsentActive()) return;
    const saved = mockSavedPlace(event.placeId);
    if (saved == null) return;
    notifications.push({
        notificationId: mockUuid(),
        triggerEventId: event.triggerEventId,
        personalSaveId: saved.personalSaveId,
        placeId: event.placeId,
        title: `${saved.place.name} 근처예요`,
        body: saved.title ?? "저장해 둔 장소가 가까이 있어요.",
        sentAt: new Date().toISOString(),
        openedAt: null,
    });
}

export async function mockFetchNotificationDetail(
    notificationId: string,
): Promise<NotificationDetailResponse> {
    await mockDelay();
    const notification = findNotification(notificationId);
    const saved = mockSavedPlace(notification.placeId);
    const items =
        saved == null || saved.personalSaveId !== notification.personalSaveId
            ? []
            : [
                  {
                      contentId: saved.contentId,
                      source: "PERSONAL" as const,
                      personalSaveId: saved.personalSaveId,
                      title: saved.title,
                      summary: saved.summary,
                      place: {
                          placeId: saved.place.placeId,
                          name: saved.place.name,
                          address: saved.place.address,
                          latitude: saved.place.latitude,
                          longitude: saved.place.longitude,
                      },
                  },
              ];
    return {
        notificationId: notification.notificationId,
        title: notification.title,
        body: notification.body,
        proposalType: "PLACE",
        sentAt: notification.sentAt,
        openedAt: notification.openedAt,
        items,
    };
}

export async function mockOpenNotification(
    notificationId: string,
): Promise<void> {
    await mockDelay();
    const notification = findNotification(notificationId);
    notification.openedAt ??= new Date().toISOString();
}

// 목 서버가 만든 알림(만든 순서). 시연 도구가 도착 기록 줄에서 연다.
export function getMockNotifications(): readonly {
    notificationId: string;
    triggerEventId: string;
    openedAt: string | null;
}[] {
    return notifications;
}

function findNotification(notificationId: string): MockNotification {
    const notification = notifications.find(
        (item) => item.notificationId === notificationId,
    );
    if (notification == null) {
        throw new ApiError(404, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
    }
    return notification;
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}
