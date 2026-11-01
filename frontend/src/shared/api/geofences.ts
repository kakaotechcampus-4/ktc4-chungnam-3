// 지오펜스 API. GET /api/geofences(등록 목록) · POST /api/location-events(장소 도착 보고). 응답은 백엔드 그대로.
// 모양은 develop 코드(GeofenceResponse · LocationEventRequest)와 ADR003_지오펜스 기준이다. docs/API.md 의 regions · dwellDelayMs 모양은 옛 것이다.
// 목록: 현재 위치에서 가까운 장소(Android 최대 99개, 반경 150m)와 재조회용 지오펜스 하나. 위치 동의가 없으면 빈 목록 · resync null.
// 도착 보고: 서버는 실행 조건(중복 triggerEventId · 30분 지난 이벤트 · 회원 쿨다운 · 동의 없음)에 걸려도 똑같이 202 다.
// 서버가 요청 안에서 꺼내기 실행과 알림 발송까지 마친 뒤 응답하므로, 이 요청에만 10초 제한을 건다.
// 목 모드(API 주소 없음)에서는 geofencesMock 이 서버 규칙대로 답한다.
import { ApiError, request } from "./client";
import { isApiConfigured } from "./config";
import { mockFetchGeofences, mockReportLocationEvent } from "./geofencesMock";

// 앱은 Android 전용이다(iOS 빌드 없음).
const PLATFORM = "ANDROID";
// 도착 보고 시간 제한. 대기열이 시간 예산을 나눌 때도 쓴다.
export const LOCATION_EVENT_TIMEOUT_MS = 10_000;
// 목록 받기 시간 제한. 백그라운드 재조회가 태스크 예산(15초) 안에 끝나게 한다.
const GEOFENCES_TIMEOUT_MS = 8_000;

// 재조회용 지오펜스. 벗어나면 현재 위치로 목록을 다시 받는다. geofenceId 는 "resync".
export type ResyncGeofence = {
    geofenceId: string;
    latitude: number;
    longitude: number;
    radiusMeters: number;
};

// 장소 지오펜스. geofenceId 는 "place:{placeId}" 이고 다시 받아도 같다.
export type PlaceGeofence = {
    geofenceId: string;
    placeId: string;
    latitude: number;
    longitude: number;
    radiusMeters: number;
};

// regions 는 서버가 자리만 두고 늘 빈 배열이다.
export type GeofenceResponse = {
    regions: unknown[];
    resync: ResyncGeofence | null;
    places: PlaceGeofence[];
};

// 시각은 ISO-8601 UTC. location 은 네 값을 모두 알 때만 보낸다.
export type LocationEventRequest = {
    placeId: string;
    triggerEventId: string;
    eventType: "ENTER";
    eventOccurredAt: string;
    location?: {
        latitude: number;
        longitude: number;
        accuracyMeters: number;
        locatedAt: string;
    };
};

// sent: 202. timeout: 10초 안에 응답이 없었다(서버는 받았을 수 있다). networkError: 응답 없음.
// serverError: 5xx. rejected: 4xx(401 포함).
export type LocationEventResult =
    | { kind: "sent" }
    | { kind: "timeout" }
    | { kind: "networkError" }
    | { kind: "serverError"; status: number }
    | { kind: "rejected"; status: number; code: string };

export function fetchGeofences(position: {
    latitude: number;
    longitude: number;
}): Promise<GeofenceResponse> {
    if (!isApiConfigured) return mockFetchGeofences(position);
    const query = [
        `latitude=${position.latitude}`,
        `longitude=${position.longitude}`,
        `platform=${PLATFORM}`,
    ].join("&");
    return request<GeofenceResponse>(`/api/geofences?${query}`, {
        timeoutMs: GEOFENCES_TIMEOUT_MS,
    });
}

// 예외를 던지지 않는다. 다시 보낼지는 결과로 호출자가 정한다(같은 triggerEventId 로 보내면 서버가 중복을 거른다).
// 401 은 rejected 지만, 백그라운드 태스크에서는 세션을 복원하기 전일 수 있어 호출자가 다시 보낼 대상으로 다룬다.
export async function reportLocationEvent(
    event: LocationEventRequest,
): Promise<LocationEventResult> {
    if (!isApiConfigured) return mockReportLocationEvent(event);
    try {
        await request<void>("/api/location-events", {
            method: "POST",
            body: event,
            timeoutMs: LOCATION_EVENT_TIMEOUT_MS,
        });
        return { kind: "sent" };
    } catch (error) {
        return toLocationEventResult(error);
    }
}

// 응답을 받지 못한 ApiError(status null)와 그 밖의 예외는 시간 초과가 아니면 networkError 다.
function toLocationEventResult(error: unknown): LocationEventResult {
    if (error instanceof ApiError && error.status != null) {
        return error.status >= 500
            ? { kind: "serverError", status: error.status }
            : { kind: "rejected", status: error.status, code: error.code };
    }
    return error instanceof ApiError && error.code === "TIMEOUT"
        ? { kind: "timeout" }
        : { kind: "networkError" };
}
