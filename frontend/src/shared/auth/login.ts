// 로그인 흐름. 제공자 로그인 → (API 주소가 있으면) 백엔드에 자격 증명을 보내 세션을 받아 저장 → 00c.
// 게스트 세션은 만료 · 401 때 같은 설치 id 로 조용히 다시 받는다(refreshSession). 클라이언트에 등록해 쓴다.
// 다시 받은 직후 원래 요청들을 다시 보내기 전에 등록된 함수(setSessionRefreshedHandler, FCM 토큰 재등록)를 기다린다(최대 5초).
// 목 모드(API 주소 없음)에서는 서버를 부르지 않고 세션도 저장하지 않는다.
// session.ts 는 API 클라이언트를 import 할 수 없어 이 파일에 둔다.
import {
    ApiError,
    request,
    type SessionRefreshResult,
    setSessionRefresher,
} from "../api/client";
import { isApiConfigured } from "../api/config";
import { getProvider, type LoginProvider } from "./providers";
import {
    clearSession,
    completeLogin,
    getSessionProvider,
    isSession,
    saveSession,
} from "./session";

async function requestSession(provider: LoginProvider, credential: string) {
    const response = await request<unknown>(provider.endpoint, {
        method: "POST",
        body: provider.requestBody(credential),
        auth: false,
    });
    if (!isSession(response)) {
        throw new ApiError(
            null,
            "INVALID_SESSION_RESPONSE",
            "로그인 응답에 세션이 없습니다.",
        );
    }
    await saveSession(response, provider.id);
}

// cancelled: 사용자가 제공자 창을 닫았다(오류 없이 머문다).
// 통신 · 서버 오류는 예외로 던진다(00a-err · 00b-err).
export async function signIn(
    provider: LoginProvider,
): Promise<"success" | "cancelled"> {
    const result = await provider.signIn();
    if (result.type === "cancelled") return "cancelled";
    if (isApiConfigured) await requestSession(provider, result.credential);
    completeLogin();
    return "success";
}

// 동시에 여러 요청이 401 을 받아도 다시 받기는 한 번만 한다.
let refreshing: Promise<SessionRefreshResult> | null = null;

// 새 세션을 받은 직후(원래 요청들을 다시 보내기 전) 기다릴 함수. 서버가 새 세션마다 FCM 토큰을 지워서
// runtime 이 새 세션으로 다시 등록하는 함수를 넣는다(shared 는 runtime 을 import 하지 않는다).
// 이 함수는 다시 받기 안에서 돈다. 세션을 다시 받는 요청(refresh: false 가 아닌 요청)이나 그런 요청을 기다리는 일을 하면
// 진행 중인 다시 받기가 자기 자신을 기다리게 된다. 예외는 삼키고, REFRESHED_HANDLER_TIMEOUT_MS 를 넘으면 기다리지 않는다.
let onRefreshed: (() => Promise<void>) | undefined;
const REFRESHED_HANDLER_TIMEOUT_MS = 5_000;

export function setSessionRefreshedHandler(handler: () => Promise<void>): void {
    onRefreshed = handler;
}

async function runRefreshedHandler(): Promise<void> {
    if (onRefreshed == null) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<void>((resolve) => {
        timer = setTimeout(resolve, REFRESHED_HANDLER_TIMEOUT_MS);
    });
    try {
        await Promise.race([onRefreshed().catch(() => {}), timeout]);
    } finally {
        clearTimeout(timer);
    }
}

// 게스트 세션을 같은 설치 id 로 다시 받는다. 새 세션을 저장하면 onRefreshed 를 기다린 뒤 refreshed 를 돌려준다.
// refreshed: 새 세션을 저장했다. failed: 통신 오류 등(로그아웃하지 않는다).
// rejected: 게스트 로그인이 거절됐다(세션을 지우고 00a). notApplicable: 게스트 세션이 아니다.
async function refreshSession(): Promise<SessionRefreshResult> {
    if (getSessionProvider() !== "guest") return "notApplicable";
    refreshing ??= (async (): Promise<SessionRefreshResult> => {
        const guest = getProvider("guest");
        try {
            const result = await guest.signIn();
            if (result.type !== "success") return "failed";
            await requestSession(guest, result.credential);
            await runRefreshedHandler();
            return "refreshed";
        } catch (error) {
            const status = error instanceof ApiError ? error.status : null;
            if (status != null && status >= 400 && status < 500) {
                await clearSession("intro");
                return "rejected";
            }
            return "failed";
        } finally {
            refreshing = null;
        }
    })();
    return refreshing;
}

// 앱 시작 때 한 번. API 클라이언트가 만료 · 401 때 이 함수를 부른다.
export function registerSessionRefresher(): void {
    setSessionRefresher(refreshSession);
}
