// 약관 버전 · 약관 URL.

// 위치기반서비스 약관 버전. 서버 설정 consent.location-based-service-terms-version(기본 v1)과 같아야 한다.
// 다르면 동의 PUT 이 400 이다. 앱이 서버의 현재 버전을 조회할 방법은 아직 없다.
export const LOCATION_TERMS_VERSION = "v1";

// 약관 페이지. 아직 없어 null 이다.
export const TERMS_URLS: {
    // 위치기반서비스 이용약관
    locationBasedService: string | null;
    // 약관 및 정책 목록
    policies: string | null;
} = {
    locationBasedService: null,
    policies: null,
};
