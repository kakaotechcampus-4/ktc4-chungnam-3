// UI 개발용 목 데이터. 빈 상태를 보려면 플래그를 true 로 바꾸고 reload 한다.
// 위치 권한은 runtime 작업 전까지 목 플래그로 둔다. 안드로이드 권한 3단계와 같다.
export type MockLocationPermission = "always" | "whileInUse" | "denied";
// "whileInUse"와 "denied"로 바꿔 reload하면 값이 "앱 사용 중에만 허용", "허용 안 함"으로 바뀌어야 함.

export const MOCK_SCENARIO: {
    nearbyEmpty: boolean;
    archiveEmpty: boolean;
    locationPermission: MockLocationPermission;
} = {
    nearbyEmpty: false,
    archiveEmpty: false,
    locationPermission: "always",
};

export { archiveMock } from "./archive";
export { nearbyEmptyMock, nearbyMock } from "./nearby";
export { placeDetailsMock } from "./placeDetails";
export { saveResultsMock } from "./saveResults";
export { settingsMock } from "./settings";
