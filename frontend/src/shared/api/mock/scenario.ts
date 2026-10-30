// UI 개발용 목 시나리오 플래그. 바꾸고 reload 한다.
// 목 데이터 파일이 이 플래그를 읽으므로 index.ts 와 분리한다(순환 import 방지).
// 위치 권한은 runtime 작업 전까지 목 플래그로 둔다. 안드로이드 권한 3단계와 같다.
export type MockLocationPermission = "always" | "whileInUse" | "denied";
// "whileInUse"와 "denied"로 바꿔 reload하면 값이 "앱 사용 중에만 허용", "허용 안 함"으로 바뀌어야 함.
// 목 로그인 결과. success: 시작 · 로그인 성공, failed: 통신 · 서버 오류(00a-err · 00b-err),
// cancelled: 사용자가 제공자 창을 닫음(카카오를 다시 켰을 때 00b 유지. 게스트는 해당 없음).
export type MockLoginResult = "success" | "cancelled" | "failed";
// 목 위치기반서비스 동의. 시작 상태와 저장(PUT) 결과. fail 이면 시트의 오류 상태를 본다.
export type MockLocationConsent = "never" | "agreed" | "withdrawn";
export type MockConsentSaveResult = "ok" | "fail";
// 목 저장물(personalSavesMock). 저장(POST) 결과와, 저장하고 약 5초 뒤 상세 · 목록에 보일 분석 결과.
// networkError: 응답 없음(ApiError status null). invalid: 400 INVALID_REQUEST.
// failed 는 다시 저장하면 다시 분석되는 실패(EXTRACTION_API_ERROR), videoUnavailable 은 다시 분석되지 않는다.
export type MockSaveResult = "ok" | "networkError" | "invalid";
export type MockAnalysisResult =
    | "success"
    | "noPlace"
    | "failed"
    | "videoUnavailable";

export const MOCK_SCENARIO: {
    // false 면 저장된 세션이 없는 것으로 보고 온보딩(00a)부터 시작한다.
    signedIn: boolean;
    loginResult: MockLoginResult;
    nearbyEmpty: boolean;
    // 저장물 목(personalSavesMock)의 시작 상태. true 면 빈 저장소(07b), false 면 샘플 6개.
    archiveEmpty: boolean;
    locationPermission: MockLocationPermission;
    // true 면 목 썸네일 URL 을 깨뜨려 로드 실패(bg/placeholder) 를 본다.
    brokenThumbnails: boolean;
    locationConsent: MockLocationConsent;
    consentSaveResult: MockConsentSaveResult;
    saveResult: MockSaveResult;
    analysisResult: MockAnalysisResult;
} = {
    signedIn: false, // 현재는 개발 과정이므로 false로 두었음. 설계 문서에는 true가 기본 값이라고 명시해두었음.
    loginResult: "success",
    nearbyEmpty: false,
    archiveEmpty: false,
    locationPermission: "always",
    brokenThumbnails: false,
    locationConsent: "never",
    consentSaveResult: "ok",
    saveResult: "ok",
    analysisResult: "success",
};
