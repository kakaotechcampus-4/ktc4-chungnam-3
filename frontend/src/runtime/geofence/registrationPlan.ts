// 지오펜스 등록 계획. 순수 함수. 서버 목록 + 현재 위치 + 이전 장소 상태 → OS 에 넘길 region 목록과 장소별 안/밖 상태.
// region: 장소는 들어옴 · 나감을 모두 받고(들어옴은 도착 보고, 나감은 상태 초기화), 재조회 지오펜스는 나감만 받는다(ADR003).
// identifier 는 서버 geofenceId 그대로다("place:{placeId}" · "resync").
// 장소 상태: 이전 상태가 있으면 그대로 둔다. 새로 목록에 들어온 장소만 현재 위치에서 반경 안이면 inside, 밖이면 outside.
// Android expo-location 은 초기 트리거(ENTER | EXIT)를 켜고 등록하므로, 이전 inside 장소의 초기 ENTER 는 handleGeofenceEvent 가 거르고,
// 이전 outside 인데 지금 안에 있는 장소(등록 직전에 들어옴)는 초기 ENTER 로 보고된다. 목록에서 빠진 장소의 상태는 남기지 않는다.
import type { GeofenceResponse } from "../../shared/api/geofences";
import { distanceMeters, type LatLng } from "../../shared/geo/distanceMeters";

// expo-location LocationRegion 과 같은 모양이다.
export type GeofenceRegion = {
    identifier: string;
    latitude: number;
    longitude: number;
    radius: number;
    notifyOnEnter: boolean;
    notifyOnExit: boolean;
};

export type PlaceFenceState = { placeId: string; inside: boolean };

export type RegistrationPlan = {
    regions: GeofenceRegion[];
    // geofenceId → 장소 상태. 목록에 있는 장소만 있다.
    places: Record<string, PlaceFenceState>;
    resyncId: string | null;
    center: LatLng;
};

export function planRegistration(
    response: GeofenceResponse,
    position: LatLng,
    previous: Readonly<Record<string, PlaceFenceState>>,
): RegistrationPlan {
    const regions: GeofenceRegion[] = [];
    const places: Record<string, PlaceFenceState> = {};
    for (const place of response.places) {
        regions.push({
            identifier: place.geofenceId,
            latitude: place.latitude,
            longitude: place.longitude,
            radius: place.radiusMeters,
            notifyOnEnter: true,
            notifyOnExit: true,
        });
        places[place.geofenceId] = {
            placeId: place.placeId,
            inside:
                previous[place.geofenceId]?.inside ??
                distanceMeters(position, place) <= place.radiusMeters,
        };
    }
    const { resync } = response;
    if (resync != null) {
        regions.push({
            identifier: resync.geofenceId,
            latitude: resync.latitude,
            longitude: resync.longitude,
            radius: resync.radiusMeters,
            notifyOnEnter: false,
            notifyOnExit: true,
        });
    }
    return {
        regions,
        places,
        resyncId: resync?.geofenceId ?? null,
        center: { latitude: position.latitude, longitude: position.longitude },
    };
}
