// [확장 포인트] 로그인 제공자 목록.
// 제공자 추가 = 여기에 한 항목 + (버튼이 있으면) 버튼 변형(SocialLoginButton · colors.provider) + 백엔드 엔드포인트.
// MVP 는 게스트만 켠다. 00a "시작하기"가 게스트 로그인을 부르고 00b 는 등록하지 않는다.
// 카카오를 다시 켜려면 enabled 를 true 로 바꾼다. 버튼이 있는 제공자가 켜지면 00b 와 버튼이 돌아온다.
import type { SessionProvider } from "./session";
import { signInAsGuest } from "./guest";
import { signInWithKakao } from "./kakao";

// 제공자 로그인 결과. credential 은 백엔드에 보낼 값(카카오: 카카오 액세스 토큰, 게스트: 설치 id).
// 사용자가 창을 닫으면 cancelled(오류 없이 머문다). 통신 · 서버 오류는 예외로 던진다(00a-err · 00b-err).
export type ProviderSignInResult =
    | { type: "success"; credential: string }
    | { type: "cancelled" };

export type LoginProvider = {
    id: SessionProvider;
    enabled: boolean;
    // 00b 버튼(C/SocialLoginButton 의 Provider 변형). 게스트는 버튼이 없다.
    button?: { variant: "kakao"; label: string };
    // 오류 문구에 쓰는 이름. "{name} 로그인을 마치지 못했어요."
    name: string;
    signIn: () => Promise<ProviderSignInResult>;
    // 백엔드 로그인 엔드포인트와 요청 바디. 응답은 {sessionToken, sessionExpiresAt}.
    endpoint: string;
    requestBody: (credential: string) => Record<string, string>;
};

export const LOGIN_PROVIDERS: readonly LoginProvider[] = [
    {
        id: "guest",
        enabled: true,
        name: "게스트",
        signIn: signInAsGuest,
        endpoint: "/api/auth/guest",
        requestBody: (installationId) => ({ installationId }),
    },
    {
        id: "kakao",
        // MVP 에서는 끈다. 다시 켤 때 카카오 SDK 연동(STRUCTURE.md "로그인 제공자 확장")을 함께 한다.
        enabled: false,
        button: { variant: "kakao", label: "카카오 로그인" },
        name: "카카오",
        signIn: signInWithKakao,
        endpoint: "/api/auth/kakao",
        requestBody: (accessToken) => ({ kakaoAccessToken: accessToken }),
    },
];

export type ButtonProvider = LoginProvider & {
    button: NonNullable<LoginProvider["button"]>;
};

// 00b 에 그릴 버튼 제공자. 하나도 없으면 00b 를 등록하지 않는다.
export const BUTTON_PROVIDERS: readonly ButtonProvider[] =
    LOGIN_PROVIDERS.filter(
        (provider): provider is ButtonProvider =>
            provider.enabled && provider.button != null,
    );

export function getProvider(id: SessionProvider): LoginProvider {
    const provider = LOGIN_PROVIDERS.find((item) => item.id === id);
    if (provider == null) throw new Error(`unknown provider: ${id}`);
    return provider;
}
