// 근처 탭(01 · 06 · 06b) 값. 순수 함수.
// 반경(직선거리 NEARBY_RADIUS_METERS 이하, 서버 꺼내기 후보 반경과 같다) 안의 내 장소를 가까운 순으로 늘어놓는다.
// "지금" 카드는 가장 가까운 1곳, 나머지는 바램(recent → weeks → months)으로 묶고 묶음 안에서는 가까운 순이다.
// 반경 안에 없으면 06: 가장 가까운 장소 이름 · 직선거리(km), 썸네일은 가까운 3곳. 장소가 하나도 없으면 nearest 가 없다.
// 카드 요약은 영상 요약 → 영상 제목 → 숨김. 썸네일 · 바램 · 지난 시간은 그 장소의 가장 최근 저장물.
import type { SavedPlace } from "../../domain/savedPlace";
import { youtubeThumbnailUrl } from "../../shared/external-links/youtubeLinks";
import { distanceMeters, type LatLng } from "../../shared/geo/distanceMeters";
import { distanceLabel, kilometersLabel } from "../../shared/ui/distanceLabel";
import { fadeFor, savedAgo } from "../../shared/ui/savedAt";
import type { ThumbFade } from "../../shared/ui/Thumb";

export const NEARBY_RADIUS_METERS = 1000;
const STACK_SIZE = 3;
const FADE_ORDER: readonly ThumbFade[] = ["recent", "weeks", "months"];

export type NearbyItem = {
    placeId: string;
    personalSaveId: string;
    place: string;
    time: string;
    walk: string;
    summary?: string;
    thumbUri: string;
    fade: ThumbFade;
};

export type NearbyView =
    | {
          kind: "list";
          total: number;
          now: NearbyItem;
          groups: NearbyItem[][];
      }
    | {
          kind: "empty";
          nearest?: { placeId: string; name: string; distance: string };
          stackThumbUris: string[];
      };

export function toNearbyView(
    places: readonly SavedPlace[],
    from: LatLng,
    now: Date,
): NearbyView {
    const measured = places
        .map((place) => ({ place, meters: distanceMeters(from, place) }))
        .sort(
            (a, b) =>
                a.meters - b.meters ||
                (a.place.placeId < b.place.placeId
                    ? -1
                    : a.place.placeId > b.place.placeId
                      ? 1
                      : 0),
        );
    const inside = measured.filter(
        ({ meters }) => meters <= NEARBY_RADIUS_METERS,
    );
    const [first, ...rest] = inside;
    if (first == null) {
        const nearest = measured[0];
        return {
            kind: "empty",
            nearest:
                nearest == null
                    ? undefined
                    : {
                          placeId: nearest.place.placeId,
                          name: nearest.place.name,
                          distance: kilometersLabel(nearest.meters),
                      },
            stackThumbUris: measured
                .slice(0, STACK_SIZE)
                .map(({ place }) => youtubeThumbnailUrl(place.videoId)),
        };
    }
    const items = rest.map(({ place, meters }) => toItem(place, meters, now));
    return {
        kind: "list",
        total: inside.length,
        now: toItem(first.place, first.meters, now),
        groups: FADE_ORDER.map((fade) =>
            items.filter((item) => item.fade === fade),
        ).filter((group) => group.length > 0),
    };
}

// 06b 썸네일. 최근 저장순(useSavedPlaces 순서) 앞의 3곳.
export function recentThumbUris(places: readonly SavedPlace[]): string[] {
    return places
        .slice(0, STACK_SIZE)
        .map((place) => youtubeThumbnailUrl(place.videoId));
}

// 06b "위치 켜기"가 할 일. off: 다시 물을 수 있으면 권한 요청, 아니면 앱 설정.
// unknown: 휴대폰 위치가 꺼져 있으면 위치 설정, 켜져 있으면(실내 등에서 위치만 못 받음) 바로 다시 읽기.
export type LocationAction =
    | "requestPermission"
    | "openAppSettings"
    | "openLocationSettings"
    | "reread";

export function locationAction(
    status: "off" | "unknown",
    canAskAgain: boolean,
    servicesEnabled: boolean,
): LocationAction {
    if (status === "off") {
        return canAskAgain ? "requestPermission" : "openAppSettings";
    }
    return servicesEnabled ? "reread" : "openLocationSettings";
}

function toItem(place: SavedPlace, meters: number, now: Date): NearbyItem {
    const savedAt = new Date(place.savedAt);
    return {
        placeId: place.placeId,
        personalSaveId: place.personalSaveId,
        place: place.name,
        time: savedAgo(savedAt, now),
        walk: distanceLabel(meters),
        summary: present(place.summary) ?? present(place.title),
        thumbUri: youtubeThumbnailUrl(place.videoId),
        fade: fadeFor(savedAt, now),
    };
}

// null · 빈 문자열은 없는 것으로 본다.
function present(value: string | null): string | undefined {
    return value == null || value === "" ? undefined : value;
}
