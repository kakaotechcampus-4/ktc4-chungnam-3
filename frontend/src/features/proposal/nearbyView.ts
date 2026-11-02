// 근처 탭(01 · 06 · 06b) 값. 순수 함수. 무엇을 보일지(nearbyGate) · 06b 버튼이 할 일(locationAction)도 여기서 정한다.
// 반경(직선거리 NEARBY_RADIUS_METERS 이하, 서버 꺼내기 후보 반경과 같다) 안의 내 장소를 가까운 순으로 늘어놓는다.
// "지금" 카드는 가장 가까운 1곳, 나머지는 바램(recent → weeks → months)으로 묶고 묶음 안에서는 가까운 순이다.
// 반경 안에 없으면 06: 가장 가까운 장소 이름 · 직선거리(km), 썸네일은 가까운 3곳. 장소가 하나도 없으면 nearest 가 없다.
// 카드 요약은 영상 요약 → 영상 제목 → 숨김. 썸네일 · 바램 · 지난 시간은 그 장소의 가장 최근 저장물.
import type { SavedPlace } from "../../domain/savedPlace";
import type { LocationConsentView } from "../../shared/api/useLocationConsent";
import { youtubeThumbnailUrl } from "../../shared/external-links/youtubeLinks";
import { distanceMeters, type LatLng } from "../../shared/geo/distanceMeters";
import type { DeviceLocation } from "../../shared/permissions/useDeviceLocation";
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

// 근처 탭에 무엇을 보일지. loading 이면 헤더만, off 면 06b, on 이면 장소(01 · 06).
// 위치 · 동의를 읽는 동안은 loading(목록을 보였다가 06b 로 바뀌지 않게).
// 위치 정보 이용 동의가 agreed 가 아니면(never · withdrawn · outdated) 위치 권한이 있어도 06b(근처 기능 꺼짐).
// 동의를 읽지 못하면(failed) 게이트를 걸지 않고 위치 규칙대로 둔다(서버가 안 될 때 동의를 잘못 안내하지 않게).
export type NearbyGate = "loading" | "off" | "on";

export function nearbyGate(
    location: DeviceLocation["status"],
    consent: LocationConsentView,
): NearbyGate {
    if (location === "loading" || consent.status === "loading") {
        return "loading";
    }
    if (consent.status === "loaded" && consent.state !== "agreed") {
        return "off";
    }
    return location === "located" ? "on" : "off";
}

// 06b "위치 켜기"가 할 일. 동의가 먼저다(동의 시트, 저장하면 같은 누름 안에서 다시 이 함수로 위치 단계).
// 위치 꺼짐(off): 다시 물을 수 있으면 권한 요청, 아니면 앱 설정.
// 위치 모름(unknown): 휴대폰 위치가 꺼져 있으면 위치 설정, 켜져 있으면(실내 등에서 위치만 못 받음) 바로 다시 읽기.
// 동의도 위치도 있으면 할 일이 없다(none, 다시 읽은 위치 · 동의로 목록이 보인다).
export type LocationAction =
    | "openConsentSheet"
    | "requestPermission"
    | "openAppSettings"
    | "openLocationSettings"
    | "reread"
    | "none";

export function locationAction(input: {
    consentAgreed: boolean;
    location: "off" | "unknown" | "located";
    canAskAgain: boolean;
    servicesEnabled: boolean;
}): LocationAction {
    if (!input.consentAgreed) return "openConsentSheet";
    if (input.location === "off") {
        return input.canAskAgain ? "requestPermission" : "openAppSettings";
    }
    if (input.location === "unknown") {
        return input.servicesEnabled ? "reread" : "openLocationSettings";
    }
    return "none";
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
