// HTTP 클라이언트. 기본 주소 · Bearer 헤더 · ApiError · 401 처리.
// 응답은 래핑하지 않는다. 실패 바디 {code, message} 와 네트워크 오류 · 시간 초과는 ApiError 로 바꾼다.
// 세션을 실어 보낸 요청이 401 이면 세션을 지운다(만료 · 다른 폰 로그인). 화면은 세션 구독으로 00b 가 된다.
import { clearSession, getSessionToken } from "../auth/session";
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

type RequestOptions = {
    method?: "GET" | "POST" | "PUT" | "DELETE";
    body?: unknown;
    // false 면 세션을 싣지 않는다(로그인 요청).
    auth?: boolean;
};

export async function request<T>(
    path: string,
    { method = "GET", body, auth = true }: RequestOptions = {},
): Promise<T> {
    if (API_BASE_URL == null) {
        throw new ApiError(
            null,
            "API_NOT_CONFIGURED",
            "EXPO_PUBLIC_API_BASE_URL 이 없습니다.",
        );
    }

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

    if (response.status === 401 && token != null) await clearSession();
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
