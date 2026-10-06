// 카카오 로그인. 카카오 액세스 토큰을 돌려준다(백엔드 POST /api/auth/kakao 가 받는다).
// 지금은 목이다(MOCK_SCENARIO.loginResult). 3번 커밋에서 @react-native-kakao/user 의 login() 으로 바꾼다.
import { MOCK_SCENARIO } from "../api/mock";
import type { ProviderSignInResult } from "./providers";

const MOCK_DELAY_MS = 600;

export async function signInWithKakao(): Promise<ProviderSignInResult> {
    await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
    switch (MOCK_SCENARIO.loginResult) {
        case "success":
            return { type: "success", accessToken: "mock-kakao-access-token" };
        case "cancelled":
            return { type: "cancelled" };
        case "failed":
            throw new Error("mock kakao login failed");
    }
}
