// UI 개발용 목 데이터. 시나리오 플래그(세션 · 로그인 결과 · 빈 상태 · 위치 권한 · 깨진 썸네일 · 위치 동의)는 scenario.ts 에서 바꾸고 reload 한다.
export {
    MOCK_SCENARIO,
    type MockConsentSaveResult,
    type MockLocationConsent,
    type MockLocationPermission,
    type MockLoginResult,
} from "./scenario";

export { archiveMock } from "./archive";
export { mapViewMock } from "./mapView";
export { nearbyEmptyMock, nearbyMock } from "./nearby";
export { onboardingMock } from "./onboarding";
export { placeDetailsMock } from "./placeDetails";
export { placeSearchMock } from "./placeSearch";
export { saveResultsMock } from "./saveResults";
export { settingsMock } from "./settings";
