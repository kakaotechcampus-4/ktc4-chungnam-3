// UI 개발용 목 시나리오 플래그. 바꾸고 reload 한다.
// 목 데이터 파일이 이 플래그를 읽으므로 index.ts 와 분리한다(순환 import 방지).
// 위치 권한은 runtime 작업 전까지 목 플래그로 둔다. 안드로이드 권한 3단계와 같다.
export type MockLocationPermission = "always" | "whileInUse" | "denied";
// "whileInUse"와 "denied"로 바꿔 reload하면 값이 "앱 사용 중에만 허용", "허용 안 함"으로 바뀌어야 함.

export const MOCK_SCENARIO: {
    nearbyEmpty: boolean;
    archiveEmpty: boolean;
    locationPermission: MockLocationPermission;
    // true 면 목 썸네일 URL 을 깨뜨려 로드 실패(bg/placeholder) 를 본다.
    brokenThumbnails: boolean;
} = {
    nearbyEmpty: false,
    archiveEmpty: false,
    locationPermission: "always",
    brokenThumbnails: false,
};
