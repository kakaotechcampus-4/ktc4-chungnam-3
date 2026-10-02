// 장소 상세 목 데이터. placeId 로 찾는다. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 문구는 Figma 08 · 08b · 08c 그대로. 태그의 # 은 컴포넌트가 붙인다. 이미지는 01 · 07 의 같은 장소와 같다.
// coordinate 는 10 지도 핀(mapViewMock.pins)과 같은 좌표다.
// 도보 시간(walk)은 위치 권한이 "denied" 면 화면이 숨긴다.
// sourceTitle 은 08 시트에는 안 보이고, 바꾸기 모드 02c 의 캡션에 쓴다(영상 제목).
import { img } from "./img";

export const placeDetailsMock = {
    // 08 장소 상세 (2061:971) · 08c 시트 펼침 (2106:695). 메모는 08c 의 긴 메모다.
    sungsimdang: {
        time: "3주 전",
        place: "성심당 본점",
        address: "대전 중구 은행동",
        coordinate: { latitude: 36.3277, longitude: 127.4274 },
        walk: "도보 4분",
        thumbUri: img(1080),
        fade: "recent",
        note: "튀김소보로는 오전에 가면 줄이 짧다. 부추빵도 같이 사라고 함. 2층 카페는 주말 오후에 자리가 거의 없고, 포장만 할 거면 1층 계산대 오른쪽 줄이 따로 있다고 한다. 명란바게트는 오후 3시 전에 자주 품절된다고 함.",
        tags: ["줄서는", "오전", "빵집"],
        sourceMeta: "@daejeon.bread · YouTube Shorts",
        sourceTitle: "대전 가면 꼭 들르는 빵집 3곳",
    },
    // 08b 빈 필드 (2106:625). 메모 · 태그가 없다.
    "daeheung-cathedral": {
        time: "6주 전",
        place: "대흥동 성당",
        address: "대전 중구 대흥동",
        coordinate: { latitude: 36.3256, longitude: 127.4222 },
        walk: "도보 11분",
        thumbUri: img(488),
        fade: "weeks",
        sourceMeta: "@red.brick.walk · YouTube Shorts",
        sourceTitle: "해질녘 대흥동 성당 산책",
    },
    // 아래는 10 지도 핀이 여는 08. 최소 필드만 둔다. 메모 · 태그를 일부러 비운 곳은 08b 빈 필드 규칙을 본다.
    "afternoon-four": {
        time: "2주 전",
        place: "오후 네시",
        address: "대전 중구 대흥동",
        coordinate: { latitude: 36.3244, longitude: 127.4246 },
        walk: "도보 9분",
        thumbUri: img(1060),
        fade: "weeks",
        note: "창가 자리에서 골목이 내려다보인다. 오후 4시쯤 볕이 제일 좋다고 함.",
        tags: ["조용한", "창가"],
        sourceMeta: "@slow.alley.cafe · YouTube Shorts",
        sourceTitle: "대흥동 골목 조용한 카페",
    },
    // 메모 없음, 태그만.
    skyroad: {
        time: "4개월 전",
        place: "으능정이 스카이로드",
        address: "대전 중구 은행동",
        coordinate: { latitude: 36.3292, longitude: 127.4282 },
        walk: "도보 8분",
        thumbUri: img(674),
        fade: "months",
        tags: ["저녁", "산책"],
        sourceMeta: "@night.daejeon · YouTube Shorts",
        sourceTitle: "해가 지면 켜지는 거리 천장",
    },
    // 태그 없음, 메모만.
    "hanbat-kalguksu": {
        time: "5개월 전",
        place: "한밭 손칼국수",
        address: "대전 중구 대흥동",
        coordinate: { latitude: 36.3229, longitude: 127.4263 },
        walk: "도보 13분",
        thumbUri: img(292),
        fade: "months",
        note: "국물이 진하다. 만두는 따로 주문해야 한다.",
        sourceMeta: "@noodle.map · YouTube Shorts",
        sourceTitle: "대전 칼국수 노포 한 그릇",
    },
    // 메모 · 태그 없음.
    "old-chungnam-office": {
        time: "4개월 전",
        place: "옛 충남도청",
        address: "대전 중구 선화동",
        coordinate: { latitude: 36.3275, longitude: 127.4204 },
        walk: "도보 12분",
        thumbUri: img(1031),
        fade: "months",
        sourceMeta: "@red.brick.walk · YouTube Shorts",
        sourceTitle: "근대 건축 따라 걷는 대전",
    },
    // 메모 · 태그 없음.
    "jungang-market": {
        time: "5개월 전",
        place: "대전 중앙시장",
        address: "대전 동구 원동",
        coordinate: { latitude: 36.3289, longitude: 127.4331 },
        walk: "도보 10분",
        thumbUri: img(1036),
        fade: "months",
        sourceMeta: "@market.bites · YouTube Shorts",
        sourceTitle: "중앙시장 먹거리 한 바퀴",
    },
} as const;
