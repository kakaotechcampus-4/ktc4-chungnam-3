// 알림 API 의 목. 서버 계약 모양이라 API 모듈 옆에 둔다(geofencesMock 과 같은 자리).
// 목 서버가 처음 받은 도착 보고마다(geofencesMock) 알림을 하나 만든다. 서버처럼 위치 동의가 없으면 만들지 않는다.
// 서버의 꺼내기 판단(에이전트 · 회원 쿨다운)은 흉내 내지 않는다(시연에서 반복할 수 있게). 그 장소를 가진 저장물이 있으면 늘 PLACE 제안 1개다.
// 푸시 대신 로컬 알림을 "근처 알림" 채널로 띄운다(data 는 FCM 과 같은 { notificationId }, 식별자도 notificationId).
// 그래서 누름 처리는 실제 알림과 같은 길(notificationListener → opener)을 탄다. 권한이 없거나 Expo Go 면 띄우지 않고 알림만 만든다
// (시연 도구의 도착 기록 줄로는 계속 연다). 이번 실행 동안 메모리에 남는다.
// 문구: 제목은 장소 이름, 본문은 "{저장 시점}에 저장한 곳이 근처에 있어요"(저장 시점은 기억 탭과 같은 savedAgo, "방금"이면 "방금 저장한 곳…").
// 상세의 title · body 도 같은 문구다. 상세는 서버처럼 읽을 때 저장물을 다시 확인한다(지웠으면 items 가 빈다). 없는 id 는 404, 열람은 멱등.
import {
    NEARBY_CHANNEL_ID,
    presentLocalNotification,
} from "../permissions/notifications";
import { savedAgo } from "../ui/savedAt";
import { ApiError } from "./client";
import { isApiConfigured } from "./config";
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
    const now = new Date();
    const notification: MockNotification = {
        notificationId: mockUuid(),
        triggerEventId: event.triggerEventId,
        personalSaveId: saved.personalSaveId,
        placeId: event.placeId,
        title: saved.place.name,
        body: nearbyNotificationBody(new Date(saved.savedAt), now),
        sentAt: now.toISOString(),
        openedAt: null,
    };
    notifications.push(notification);
    // 실제 모드에서는 목 서버가 불리지 않지만, 로컬 알림은 목 모드에서만 띄운다는 것을 여기서도 지킨다.
    if (isApiConfigured) return;
    presentLocalNotification({
        identifier: notification.notificationId,
        title: notification.title,
        body: notification.body,
        data: { notificationId: notification.notificationId },
        channelId: NEARBY_CHANNEL_ID,
    });
}

// 로컬 알림 · 상세 본문. 저장 시점은 기억 탭과 같은 표현(savedAgo)이다.
export function nearbyNotificationBody(savedAt: Date, now: Date): string {
    const ago = savedAgo(savedAt, now);
    return ago === "방금"
        ? "방금 저장한 곳이 근처에 있어요"
        : `${ago}에 저장한 곳이 근처에 있어요`;
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
