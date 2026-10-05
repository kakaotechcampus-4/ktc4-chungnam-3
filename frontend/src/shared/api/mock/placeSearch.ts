// 장소 직접 찾기(02c) 목 검색 대상. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 검색 출처(LocationIQ 등)는 백엔드 계약 전이다. 앞 4개는 Figma 02c 문구 그대로.
export const placeSearchMock = [
    {
        id: "sungsimdang-cake",
        name: "성심당 케익부띠끄",
        address: "대전 중구 은행동",
    },
    {
        id: "sungsimdang-main",
        name: "성심당 본점",
        address: "대전 중구 은행동",
    },
    {
        id: "sungsimdang-dcc",
        name: "성심당 DCC점",
        address: "대전 유성구 도룡동",
    },
    {
        id: "sungsimdang-lotte",
        name: "성심당 롯데백화점 대전점",
        address: "대전 서구 괴정동",
    },
    { id: "afternoon-four", name: "오후 네시", address: "대전 중구 대흥동" },
    {
        id: "daeheung-cathedral",
        name: "대흥동 성당",
        address: "대전 중구 대흥동",
    },
    {
        id: "hanbat-kalguksu",
        name: "한밭 손칼국수",
        address: "대전 중구 선화동",
    },
] as const;
