// 10 지도 탭 목 데이터. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 핀은 Figma 10(2103:427)처럼 7곳이다(recent 1 · weeks 2 · months 4). 01 · 07 과 같은 장소는 같은 이미지를 쓴다.
// placeId 는 placeDetailsMock 의 키다. 요약 시트에서 08 로 갈 때 쓴다.
// 좌표는 LocationIQ 지오코딩 결과로 대체 예정(문자열 lat/lon → 숫자 변환은 API 계층에서).
// 지금은 대전 중구 일대의 대략적인 숫자 좌표다.
import { img } from "./img";

export const mapViewMock = {
    // 위치 권한이 "denied" 가 아니면 이 자리에 현재 위치를 그린다.
    current: { latitude: 36.3262, longitude: 127.4248 },
    initialRegion: {
        latitude: 36.3266,
        longitude: 127.4262,
        latitudeDelta: 0.014,
        longitudeDelta: 0.012,
    },
    // 06 "{동네} 기억 보기" 가 여는 동네. 키는 nearbyEmptyMock.nearestArea 와 같다.
    areas: {
        대흥동: {
            latitude: 36.3252,
            longitude: 127.4228,
            latitudeDelta: 0.008,
            longitudeDelta: 0.007,
        },
    },
    pins: [
        {
            placeId: "sungsimdang",
            coordinate: { latitude: 36.3277, longitude: 127.4274 },
            time: "3주 전",
            place: "성심당 본점",
            walk: "도보 4분",
            category: "빵집",
            thumbUri: img(1080),
            fade: "recent",
        },
        {
            placeId: "afternoon-four",
            coordinate: { latitude: 36.3244, longitude: 127.4246 },
            time: "2주 전",
            place: "오후 네시",
            walk: "도보 9분",
            category: "카페",
            thumbUri: img(1060),
            fade: "weeks",
        },
        {
            placeId: "daeheung-cathedral",
            coordinate: { latitude: 36.3256, longitude: 127.4222 },
            time: "6주 전",
            place: "대흥동 성당",
            walk: "도보 11분",
            category: "가볼 곳",
            thumbUri: img(488),
            fade: "weeks",
        },
        {
            placeId: "skyroad",
            coordinate: { latitude: 36.3292, longitude: 127.4282 },
            time: "4개월 전",
            place: "으능정이 스카이로드",
            walk: "도보 8분",
            category: "가볼 곳",
            thumbUri: img(674),
            fade: "months",
        },
        {
            placeId: "hanbat-kalguksu",
            coordinate: { latitude: 36.3229, longitude: 127.4263 },
            time: "5개월 전",
            place: "한밭 손칼국수",
            walk: "도보 13분",
            category: "식당",
            thumbUri: img(292),
            fade: "months",
        },
        {
            placeId: "old-chungnam-office",
            coordinate: { latitude: 36.3275, longitude: 127.4204 },
            time: "4개월 전",
            place: "옛 충남도청",
            walk: "도보 12분",
            category: "가볼 곳",
            thumbUri: img(1031),
            fade: "months",
        },
        {
            placeId: "jungang-market",
            coordinate: { latitude: 36.3289, longitude: 127.4331 },
            time: "5개월 전",
            place: "대전 중앙시장",
            walk: "도보 10분",
            category: "식당",
            thumbUri: img(1036),
            fade: "months",
        },
    ],
} as const;
