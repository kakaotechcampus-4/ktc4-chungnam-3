// 장소 상세 목 데이터. placeId 로 찾는다. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 문구는 Figma 08 · 08b · 08c 그대로. 태그의 # 은 컴포넌트가 붙인다. 이미지는 01 · 07 의 같은 장소와 같다.
// 도보 시간(walk)은 위치 권한이 "denied" 면 화면이 숨긴다.
// sourceTitle 은 08 시트에는 안 보이고, 바꾸기 모드 02c 의 캡션에 쓴다(영상 제목).
import { img } from "./img";

export const placeDetailsMock = {
    // 08 장소 상세 (2061:971) · 08c 시트 펼침 (2106:695). 메모는 08c 의 긴 메모다.
    sungsimdang: {
        time: "3주 전",
        place: "성심당 본점",
        address: "대전 중구 은행동",
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
        walk: "도보 11분",
        thumbUri: img(488),
        fade: "weeks",
        sourceMeta: "@red.brick.walk · YouTube Shorts",
        sourceTitle: "해질녘 대흥동 성당 산책",
    },
} as const;
