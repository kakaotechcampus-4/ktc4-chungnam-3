// 지오펜스 API 의 목. 서버 계약 모양이라 API 모듈 옆에 둔다(personalSavesMock 과 같은 자리).
// 목록은 서버(GeofenceService)와 같은 규칙이다. 위치 동의가 없으면(철회 포함, 약관 버전은 보지 않음) 빈 목록이고,
// 장소는 저장물 목(personalSavesMock)에서 분석 실패가 아닌 저장물의 장소를 요청 위치에서 가까운 순(같으면 placeId 순)으로 최대 99개, 반경 150m.
// 재조회 지오펜스는 중심이 요청 위치, 반경이 max(3000, 가장 먼 장소 거리 + 500)이다. 저장물을 지우면 그 장소도 빠진다.
// 도착 보고는 받은 이벤트를 이번 실행 동안 메모리에 남기고 sent 다. 같은 triggerEventId 는 기록하지 않고 조용히 sent(서버처럼).
// MOCK_SCENARIO.locationEventResult: networkError 면 기록하지 않고, timeout 이면 기록한 뒤 timeout(서버는 받았지만 앱이 응답을 못 받음).
import { distanceMeters } from "../geo/distanceMeters";
import { isMockLocationConsentActive } from "./consents";
import type {
    GeofenceResponse,
    LocationEventRequest,
    LocationEventResult,
} from "./geofences";
import { MOCK_SCENARIO } from "./mock";
import { mockGeofencePlaces } from "./personalSavesMock";

const MOCK_DELAY_MS = 600;
// 서버 설정 geofence.* 기본값. 장소 수는 Android 한도 100 에서 재조회 지오펜스 한 자리를 뺀다.
const PLACE_LIMIT = 99;
const PLACE_RADIUS_METERS = 150;
const RESYNC_MARGIN_METERS = 500;
const RESYNC_MIN_RADIUS_METERS = 3000;

const EMPTY: GeofenceResponse = { regions: [], resync: null, places: [] };

const events: LocationEventRequest[] = [];

export async function mockFetchGeofences(position: {
    latitude: number;
    longitude: number;
}): Promise<GeofenceResponse> {
    await mockDelay();
    if (!isMockLocationConsentActive()) return EMPTY;
    const selected = mockGeofencePlaces()
        .map((place) => ({
            ...place,
            distance: distanceMeters(position, place),
        }))
        .sort(
            (a, b) =>
                a.distance - b.distance ||
                (a.placeId < b.placeId ? -1 : a.placeId > b.placeId ? 1 : 0),
        )
        .slice(0, PLACE_LIMIT);
    const farthest = selected[selected.length - 1];
    if (farthest == null) return EMPTY;
    return {
        regions: [],
        resync: {
            geofenceId: "resync",
            latitude: position.latitude,
            longitude: position.longitude,
            radiusMeters: Math.max(
                RESYNC_MIN_RADIUS_METERS,
                farthest.distance + RESYNC_MARGIN_METERS,
            ),
        },
        places: selected.map(({ placeId, latitude, longitude }) => ({
            geofenceId: `place:${placeId}`,
            placeId,
            latitude,
            longitude,
            radiusMeters: PLACE_RADIUS_METERS,
        })),
    };
}

export async function mockReportLocationEvent(
    event: LocationEventRequest,
): Promise<LocationEventResult> {
    await mockDelay();
    const result = MOCK_SCENARIO.locationEventResult;
    if (result === "networkError") return { kind: "networkError" };
    if (!events.some((item) => item.triggerEventId === event.triggerEventId)) {
        events.push(event);
    }
    return result === "timeout" ? { kind: "timeout" } : { kind: "sent" };
}

// 목 서버가 받은 도착 보고(받은 순서). 시연 도구 · 확인용.
export function getMockLocationEvents(): readonly LocationEventRequest[] {
    return events;
}

function mockDelay() {
    return new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
}
