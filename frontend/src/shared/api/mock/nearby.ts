// 근처 탭 목 데이터. 서버 계약이 아니다. 카드는 내 장소 API(목 모드면 저장물 목)에서 받는다.
// area: 목 모드 헤더의 동네 이름(역지오코딩 대신). 지금 위치는 mapViewMock.current.
// empty: MOCK_SCENARIO.nearbyEmpty 일 때 목 지금 위치를 옮기는 곳(유성구 봉명동). 저장물 목의 대전 7곳이 모두 1km 밖이라 06 이 된다.
export const nearbyMock = {
    area: "대흥동",
    empty: {
        area: "봉명동",
        current: { latitude: 36.355, longitude: 127.342 },
    },
} as const;
