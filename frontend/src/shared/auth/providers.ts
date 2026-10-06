// [확장 포인트] 로그인 제공자 목록. 00b 는 이 목록을 순회해 버튼을 그린다.
// 제공자 추가 = 여기에 한 항목 + 버튼 변형(SocialLoginButton · colors.provider) + 백엔드 엔드포인트.
import { signInWithKakao } from "./kakao";

// 제공자 로그인 결과. 사용자가 창을 닫으면 cancelled(00b 에 오류 없이 머문다).
// 통신 · 서버 오류는 예외로 던진다(00b-err).
export type ProviderSignInResult =
    | { type: "success"; accessToken: string }
    | { type: "cancelled" };

export type LoginProvider = {
    id: "kakao";
    // C/SocialLoginButton 의 Provider 변형.
    variant: "kakao";
    label: string;
    // 오류 문구에 쓰는 이름. "{name} 로그인을 마치지 못했어요."
    name: string;
    signIn: () => Promise<ProviderSignInResult>;
    // 백엔드 로그인 엔드포인트와 요청 바디. 응답은 {sessionToken, sessionExpiresAt}.
    endpoint: string;
    requestBody: (accessToken: string) => Record<string, string>;
};

export const LOGIN_PROVIDERS: readonly LoginProvider[] = [
    {
        id: "kakao",
        variant: "kakao",
        label: "카카오 로그인",
        name: "카카오",
        signIn: signInWithKakao,
        endpoint: "/api/auth/kakao",
        requestBody: (accessToken) => ({ kakaoAccessToken: accessToken }),
    },
];
