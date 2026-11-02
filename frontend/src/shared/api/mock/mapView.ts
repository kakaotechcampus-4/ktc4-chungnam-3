// 지도 목 데이터. 서버 계약이 아니다. 지도 탭 핀은 내 장소 API(목 모드면 저장물 목)에서 받는다.
// current: 목 모드의 지금 위치(대전 중구 대흥동, MOCK_SCENARIO.nearbyEmpty 가 아닐 때). 저장물 목의 대전 7곳이 1km 안에 든다.
// initialRegion: 08 지도의 확대 수준(latitudeDelta · longitudeDelta)으로 쓴다.
export const mapViewMock = {
    current: { latitude: 36.3262, longitude: 127.4248 },
    initialRegion: {
        latitude: 36.3266,
        longitude: 127.4262,
        latitudeDelta: 0.014,
        longitudeDelta: 0.012,
    },
} as const;
