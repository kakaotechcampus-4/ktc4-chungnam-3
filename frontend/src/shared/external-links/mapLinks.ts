// 지도 앱 길찾기 URL. 순수 함수. 여는 것은 openMapRoute 가 한다.
// 카카오맵 공식 URL Scheme 의 도보 길찾기다. 앱 스킴과, 앱이 없을 때 여는 모바일웹 스킴(공식 문서 표기대로 http)을 함께 만든다.
// sp(출발) · ep(도착)는 "위도,경도"이고 이동수단(by)은 foot 고정이다. 출발지를 모르면 sp 를 뺀다.
// 좌표는 서버 값 그대로 쓴다(반올림 없음). 값이 숫자 · . · - 뿐이라 인코딩하지 않는다(문서도 쉼표를 그대로 쓴다).
// map.kakao.com/link/to 는 도착지만 넘겨 모바일에서 교통수단 선택을 거치므로 쓰지 않는다.
import type { LatLng } from "../geo/distanceMeters";

const KAKAO_MAP_APP_ROUTE = "kakaomap://route";
const KAKAO_MAP_WEB_ROUTE = "http://m.map.kakao.com/scheme/route";

export type MapRouteUrls = { app: string; web: string };

export function kakaoWalkingRouteUrls(
    destination: LatLng,
    origin?: LatLng,
): MapRouteUrls {
    const params = [
        ...(origin != null ? [`sp=${point(origin)}`] : []),
        `ep=${point(destination)}`,
        "by=foot",
    ].join("&");
    return {
        app: `${KAKAO_MAP_APP_ROUTE}?${params}`,
        web: `${KAKAO_MAP_WEB_ROUTE}?${params}`,
    };
}

function point({ latitude, longitude }: LatLng): string {
    return `${String(latitude)},${String(longitude)}`;
}
