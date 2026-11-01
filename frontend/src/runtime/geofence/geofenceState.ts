// 지오펜스 상태 파일 하나(storage 키 geofence-state). 앱을 다시 켜도 남는다.
// places: 등록한 장소 geofenceId → { placeId, inside }. resyncId: 등록한 재조회 지오펜스 id.
// registration: 마지막 등록(시각 · 중심 · 등록한 id 목록). queue: 아직 보내지 못한 도착 보고.
// 처음 한 번 읽은 값을 메모리 기준으로 쓰고, 바꿀 때마다 통째로 쓴다. 파일이 깨졌거나 모양이 다르면 빈 상태로 시작한다.
// 바꾸는 쪽은 await 없이 메모리 값을 고친 뒤 saveGeofenceState 를 부른다(중간에 다른 처리가 끼지 않게).
import type { LocationEventRequest } from "../../shared/api/geofences";
import type { LatLng } from "../../shared/geo/distanceMeters";
import { read, remove, write } from "../../shared/storage/storage";
import type { PlaceFenceState } from "./registrationPlan";

const STORAGE_KEY = "geofence-state";

export type GeofenceState = {
    places: Record<string, PlaceFenceState>;
    resyncId: string | null;
    registration: { at: string; center: LatLng; ids: string[] } | null;
    queue: LocationEventRequest[];
};

let state: Promise<GeofenceState> | undefined;

export function emptyGeofenceState(): GeofenceState {
    return { places: {}, resyncId: null, registration: null, queue: [] };
}

export function loadGeofenceState(): Promise<GeofenceState> {
    state ??= read(STORAGE_KEY).then(
        (value) => parseGeofenceState(value) ?? emptyGeofenceState(),
    );
    return state;
}

export function saveGeofenceState(value: GeofenceState): void {
    write(STORAGE_KEY, value);
}

// 메모리와 파일을 모두 비운다.
export function resetGeofenceState(): void {
    state = Promise.resolve(emptyGeofenceState());
    remove(STORAGE_KEY);
}

export function parseGeofenceState(value: unknown): GeofenceState | null {
    if (!isRecord(value)) return null;
    const { places, resyncId, registration, queue } = value;
    if (!isRecord(places) || !Object.values(places).every(isPlaceState)) {
        return null;
    }
    if (resyncId !== null && typeof resyncId !== "string") return null;
    if (registration !== null && !isRegistration(registration)) return null;
    if (!Array.isArray(queue) || !queue.every(isLocationEvent)) return null;
    return {
        places: places as Record<string, PlaceFenceState>,
        resyncId,
        registration,
        queue,
    };
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value != null && !Array.isArray(value);
}

function isPlaceState(value: unknown): value is PlaceFenceState {
    return (
        isRecord(value) &&
        typeof value.placeId === "string" &&
        typeof value.inside === "boolean"
    );
}

function isLatLng(value: unknown): value is LatLng {
    return (
        isRecord(value) &&
        typeof value.latitude === "number" &&
        typeof value.longitude === "number"
    );
}

function isRegistration(
    value: unknown,
): value is NonNullable<GeofenceState["registration"]> {
    return (
        isRecord(value) &&
        typeof value.at === "string" &&
        isLatLng(value.center) &&
        Array.isArray(value.ids) &&
        value.ids.every((id) => typeof id === "string")
    );
}

function isLocationEvent(value: unknown): value is LocationEventRequest {
    return (
        isRecord(value) &&
        typeof value.placeId === "string" &&
        typeof value.triggerEventId === "string" &&
        value.eventType === "ENTER" &&
        typeof value.eventOccurredAt === "string" &&
        (value.location === undefined || isRecord(value.location))
    );
}
