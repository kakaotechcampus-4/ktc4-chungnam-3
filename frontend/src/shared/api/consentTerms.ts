// 약관 URL.
// 약관 버전은 앱에 두지 않는다. 동의할 때는 서버가 준 currentTermsVersion 을 보낸다(consents.ts).

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
