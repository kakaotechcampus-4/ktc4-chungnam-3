// HTTP 클라이언트. 기본 주소 · Bearer 헤더 · ApiError · 401 처리.
// 응답은 래핑하지 않는다. 실패 바디 {code, message} 와 네트워크 오류 · 시간 초과는 ApiError 로 바꾼다.
// 게스트 세션은 만료됐으면 요청 전에, 401 이면 그때 같은 설치 id 로 조용히 다시 받고 원래 요청을 딱 한 번 다시 보낸다.
// 다시 보낸 요청이 또 401 이면 반복하지 않는다. 게스트가 아닌 세션(카카오)의 401 은 세션을 지우고 00b 로 간다.
// 다시 받는 함수는 login.ts 가 등록한다(setSessionRefresher). 이 파일은 login.ts 를 import 하지 않는다(순환 방지).
import {
    clearSession,
    getSessionToken,
    isSessionExpired,
} from "../auth/session";
import { API_BASE_URL } from "./config";

const REQUEST_TIMEOUT_MS = 15_000;

// status 가 null 이면 서버 응답을 받지 못했다(주소 없음 · 연결 실패 · 시간 초과).
export class ApiError extends Error {
    constructor(
        readonly status: number | null,
        readonly code: string,
        message: string,
    ) {
        super(message);
        this.name = "ApiError";
    }
}

// refreshed: 새 세션을 받았다. failed: 통신 오류 등(로그아웃하지 않는다).
// rejected: 거절돼 세션을 지웠다(00a). notApplicable: 다시 받을 수 없는 세션이다(게스트 아님).
export type SessionRefreshResult =
    | "refreshed"
    | "failed"
    | "rejected"
    | "notApplicable";

let refreshSession: (() => Promise<SessionRefreshResult>) | undefined;

export function setSessionRefresher(
    refresher: () => Promise<SessionRefreshResult>,
): void {
    refreshSession = refresher;
}

type RequestOptions = {
    method?: "GET" | "POST" | "PUT" | "DELETE";
    body?: unknown;
    // false 면 세션을 싣지 않는다(로그인 요청).
    auth?: boolean;
};

export function request<T>(
    path: string,
    options: RequestOptions = {},
): Promise<T> {
    return send<T>(path, options, false);
}

async function send<T>(
    path: string,
    options: RequestOptions,
    retried: boolean,
): Promise<T> {
    const { method = "GET", body, auth = true } = options;
    if (API_BASE_URL == null) {
        throw new ApiError(
            null,
            "API_NOT_CONFIGURED",
            "EXPO_PUBLIC_API_BASE_URL 이 없습니다.",
        );
    }

    // 만료된 게스트 세션은 보내기 전에 다시 받는다. 실패해도 그대로 보내고 401 처리에 맡긴다.
    if (auth && !retried && isSessionExpired()) await refreshSession?.();

    const headers: Record<string, string> = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const token = auth ? getSessionToken() : undefined;
    if (token != null) headers.Authorization = `Bearer ${token}`;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    let response: Response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            signal: controller.signal,
        });
    } catch {
        const timedOut = controller.signal.aborted;
        throw new ApiError(
            null,
            timedOut ? "TIMEOUT" : "NETWORK_ERROR",
            timedOut
                ? "요청 시간이 지났습니다."
                : "서버에 연결하지 못했습니다.",
        );
    } finally {
        clearTimeout(timer);
    }

    if (response.status === 401 && token != null && !retried) {
        const result = (await refreshSession?.()) ?? "notApplicable";
        if (result === "refreshed") return send<T>(path, options, true);
        if (result === "notApplicable") await clearSession("login");
        // rejected 는 이미 세션을 지웠다. failed 는 로그아웃하지 않고 오류를 넘긴다.
    }
    if (!response.ok) {
        const { code, message } = await readError(response);
        throw new ApiError(response.status, code, message);
    }
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
}

async function readError(
    response: Response,
): Promise<{ code: string; message: string }> {
    try {
        const body: unknown = await response.json();
        if (
            typeof body === "object" &&
            body != null &&
            "code" in body &&
            typeof body.code === "string"
        ) {
            const message =
                "message" in body && typeof body.message === "string"
                    ? body.message
                    : body.code;
            return { code: body.code, message };
        }
    } catch {
        // 바디가 없거나 JSON 이 아니다.
    }
    return { code: `HTTP_${response.status}`, message: response.statusText };
}
