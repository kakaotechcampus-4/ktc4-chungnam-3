// 저장물 상세 → 08 시트 · 지도 값. 순수 함수. 장소가 여러 개면 placeId 와 같은 장소, 없으면 첫 장소를 그린다(장소별 전환 전).
// 응답에 없는 값은 08b 빈 필드 규칙대로 비운다: 도보 시간 없음, 메모는 장소 설명 → 영상 요약 → 숨김,
// 태그는 카테고리 1개 → 숨김, 주소가 없으면 주소를 비운다(주소 · 도보 시간이 모두 없으면 시트가 메타 줄을 숨긴다).
// 원본 영상 줄 둘째 줄은 채널명이 없어 "{영상 제목} · YouTube Shorts", 제목이 없으면 "YouTube Shorts".
import type {
    PersonalSaveDetailResponse,
    PersonalSavePlaceResponse,
} from "../../shared/api/personalSaves";
import { youtubeThumbnailUrl } from "../../shared/external-links/youtubeLinks";
import { fadeFor, savedAgo } from "../../shared/ui/savedAt";
import type { DetailSheetProps } from "./components/DetailSheet";

export type SavedPlaceSheet = DetailSheetProps & {
    coordinate: { latitude: number; longitude: number };
};

const SOURCE_PLATFORM = "YouTube Shorts";

// 그릴 장소. placeId 와 같은 장소, 없으면 첫 장소. 장소가 없으면 undefined.
export function pickPlace(
    detail: PersonalSaveDetailResponse,
    placeId?: string,
): PersonalSavePlaceResponse | undefined {
    return (
        detail.places.find((place) => place.placeId === placeId) ??
        detail.places[0]
    );
}

// 장소가 없으면 null(08 을 그릴 수 없다).
export function toSavedPlaceSheet(
    detail: PersonalSaveDetailResponse,
    now: Date,
    placeId?: string,
): SavedPlaceSheet | null {
    const place = pickPlace(detail, placeId);
    if (place == null) return null;
    const savedAt = new Date(detail.savedAt);
    const category = present(detail.category);
    const title = present(detail.title);
    return {
        time: savedAgo(savedAt, now),
        place: place.name,
        address: present(place.address),
        thumbUri: youtubeThumbnailUrl(detail.videoId),
        fade: fadeFor(savedAt, now),
        note: present(place.description) ?? present(detail.summary),
        tags: category == null ? undefined : [category],
        sourceMeta:
            title == null ? SOURCE_PLATFORM : `${title} · ${SOURCE_PLATFORM}`,
        coordinate: { latitude: place.latitude, longitude: place.longitude },
    };
}

// null · 빈 문자열은 없는 것으로 본다.
function present(value: string | null): string | undefined {
    return value == null || value === "" ? undefined : value;
}
