// 내 장소 → 지도 핀 · 요약 시트(10b) 값. 순수 함수.
// 썸네일은 videoId 의 YouTube 썸네일, 바램 · 지난 시간은 저장 시각, 도보 시간은 지금 위치를 알 때만 붙인다.
import type { SavedPlace } from "../../domain/savedPlace";
import { youtubeThumbnailUrl } from "../../shared/external-links/youtubeLinks";
import { distanceMeters, type LatLng } from "../../shared/geo/distanceMeters";
import { distanceLabel } from "../../shared/ui/distanceLabel";
import { fadeFor, savedAgo } from "../../shared/ui/savedAt";
import type { ThumbFade } from "../../shared/ui/Thumb";

export type MapPin = {
    placeId: string;
    personalSaveId: string;
    coordinate: LatLng;
    place: string;
    time: string;
    walk?: string;
    thumbUri: string;
    fade: ThumbFade;
};

export function toMapPin(place: SavedPlace, now: Date, from?: LatLng): MapPin {
    const savedAt = new Date(place.savedAt);
    const coordinate = { latitude: place.latitude, longitude: place.longitude };
    return {
        placeId: place.placeId,
        personalSaveId: place.personalSaveId,
        coordinate,
        place: place.name,
        time: savedAgo(savedAt, now),
        walk:
            from == null
                ? undefined
                : distanceLabel(distanceMeters(from, coordinate)),
        thumbUri: youtubeThumbnailUrl(place.videoId),
        fade: fadeFor(savedAt, now),
    };
}
