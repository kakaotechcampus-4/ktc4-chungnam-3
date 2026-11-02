// 서버 내 장소 목록(장소 × 저장물 쌍) + 저장물 목록 → 장소마다 가장 최근 저장물 하나(SavedPlace). 순수 함수.
// 장소 목록에 썸네일(videoId) · 저장 시각이 없어 저장물 목록과 personalSaveId 로 묶는다.
// 저장물 목록에 없는 쌍(두 요청 사이에 지운 저장물)은 버린다. 결과는 최신 저장순, 같으면 placeId 순.

import type { SavedPlace } from "../../../domain/savedPlace";
import type {
    PersonalSaveItemResponse,
    SavedPlacePinResponse,
} from "../personalSaves";

export function toSavedPlaces(
    pins: SavedPlacePinResponse[],
    items: PersonalSaveItemResponse[],
): SavedPlace[] {
    const saves = new Map(items.map((item) => [item.personalSaveId, item]));
    const byPlace = new Map<string, SavedPlace>();
    for (const pin of pins) {
        const save = saves.get(pin.personalSaveId);
        if (save == null) continue;
        const kept = byPlace.get(pin.placeId);
        // 시각 문자열은 소수 자릿수가 달라 문자열로 비교하지 않는다.
        if (kept != null && time(kept.savedAt) >= time(save.savedAt)) continue;
        byPlace.set(pin.placeId, {
            placeId: pin.placeId,
            name: pin.name,
            address: pin.address,
            latitude: pin.latitude,
            longitude: pin.longitude,
            personalSaveId: save.personalSaveId,
            videoId: save.videoId,
            title: save.title,
            savedAt: save.savedAt,
        });
    }
    return [...byPlace.values()].sort(
        (a, b) =>
            time(b.savedAt) - time(a.savedAt) ||
            (a.placeId < b.placeId ? -1 : a.placeId > b.placeId ? 1 : 0),
    );
}

function time(iso: string): number {
    return Date.parse(iso);
}
