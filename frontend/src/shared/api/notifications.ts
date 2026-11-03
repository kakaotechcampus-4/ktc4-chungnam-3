// 알림 API. GET /api/notifications/{notificationId}(상세) · POST /api/notifications/{notificationId}/open(열람). 응답은 백엔드 NotificationDetail 그대로.
// 상세: 알림이 없거나 다른 회원 것이면 404 NOT_FOUND(만료 판정은 없다). 지금 볼 수 없는 항목(지운 저장물 · 없어진 장소 등)은 서버가 빼므로 items 가 빌 수 있다.
// items 수: PLACE 1개(장소 있음) · COURSE 2개 이상(장소가 서로 다름) · CONTENT 1개(장소 없음). PUBLIC 항목은 personalSaveId 가 null 이다.
// 열람: 본문 없는 204. 이미 열었으면 그대로 204(멱등), 없으면 404. 실패는 클라이언트의 ApiError 를 그대로 던진다.
// 목 모드(API 주소 없음)에서는 notificationsMock 이 답한다.
import { request } from "./client";
import { isApiConfigured } from "./config";
import {
    mockFetchNotificationDetail,
    mockOpenNotification,
} from "./notificationsMock";

export type ProposalType = "PLACE" | "COURSE" | "CONTENT";
export type ProposalSource = "PERSONAL" | "PUBLIC";

export type NotificationPlaceResponse = {
    placeId: string;
    name: string;
    address: string | null;
    latitude: number;
    longitude: number;
};

// personalSaveId 는 PERSONAL 항목만 있다. place 는 CONTENT 제안이면 null.
export type NotificationItemResponse = {
    contentId: string;
    source: ProposalSource;
    personalSaveId: string | null;
    title: string | null;
    summary: string | null;
    place: NotificationPlaceResponse | null;
};

// id 는 UUID 문자열, 시각은 ISO-8601 UTC.
export type NotificationDetailResponse = {
    notificationId: string;
    title: string;
    body: string;
    proposalType: ProposalType;
    sentAt: string | null;
    openedAt: string | null;
    items: NotificationItemResponse[];
};

export function fetchNotificationDetail(
    notificationId: string,
): Promise<NotificationDetailResponse> {
    if (!isApiConfigured) return mockFetchNotificationDetail(notificationId);
    return request<NotificationDetailResponse>(
        `/api/notifications/${encodeURIComponent(notificationId)}`,
    );
}

export async function openNotification(notificationId: string): Promise<void> {
    if (!isApiConfigured) return mockOpenNotification(notificationId);
    await request<void>(
        `/api/notifications/${encodeURIComponent(notificationId)}/open`,
        { method: "POST" },
    );
}
