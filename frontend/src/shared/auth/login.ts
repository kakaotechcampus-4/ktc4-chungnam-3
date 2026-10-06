// 로그인 흐름. 제공자 로그인 → (API 주소가 있으면) 백엔드에 제공자 토큰을 보내 세션을 받아 저장 → 00c.
// 목 모드(API 주소 없음)에서는 서버를 부르지 않고 세션도 저장하지 않는다.
// session.ts 는 API 클라이언트를 import 할 수 없어 이 파일에 둔다.
import { ApiError, request } from "../api/client";
import { isApiConfigured } from "../api/config";
import type { LoginProvider } from "./providers";
import { completeLogin, isSession, saveSession } from "./session";

// cancelled: 사용자가 제공자 창을 닫았다(00b 에 오류 없이 머문다).
// 통신 · 서버 오류는 예외로 던진다(00b-err).
export async function signIn(
    provider: LoginProvider,
): Promise<"success" | "cancelled"> {
    const result = await provider.signIn();
    if (result.type === "cancelled") return "cancelled";

    if (isApiConfigured) {
        const response = await request<unknown>(provider.endpoint, {
            method: "POST",
            body: provider.requestBody(result.accessToken),
            auth: false,
        });
        if (!isSession(response)) {
            throw new ApiError(
                null,
                "INVALID_SESSION_RESPONSE",
                "로그인 응답에 세션이 없습니다.",
            );
        }
        await saveSession(response);
    }

    completeLogin();
    return "success";
}
