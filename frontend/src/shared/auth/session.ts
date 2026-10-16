// 인증 상태와 세션. RootNavigator 가 이 상태로 Onboarding / Main 화면을 고른다.
// loading: 저장된 세션을 읽는 중(스플래시 유지). signedOut: 세션 없음, startAt 화면부터 보인다.
// onboarding: 로그인은 끝났고 권한 단계(00c~00f) 중이다. signedIn: 근처 탭부터.
// 세션은 안전한 저장소(shared/storage/secureStore)에만 둔다. 리프레시 토큰은 없다.
// MVP 는 게스트 세션이다. 게스트 세션은 만료돼도 같은 설치 id 로 조용히 다시 받으므로(login.ts) 로그인 상태로 복원한다.
// 목 모드(API 주소 없음)에서는 저장소를 쓰지 않고 MOCK_SCENARIO.signedIn 으로 시작한다.
// 순환 방지: 이 파일은 API 클라이언트를 import 하지 않는다.
import { useSyncExternalStore } from "react";

import { isApiConfigured } from "../api/config";
import { MOCK_SCENARIO } from "../api/mock";
import {
    deleteSecureItem,
    readSecureItem,
    writeSecureItem,
} from "../storage/secureStore";

// 백엔드 POST /api/auth/{제공자} 응답 그대로. sessionExpiresAt 은 ISO-8601 UTC.
export type Session = {
    sessionToken: string;
    sessionExpiresAt: string;
};

// 세션을 받은 제공자. 기기에만 저장한다. 401 때 다시 받을 수 있는지(게스트)를 가른다.
export type SessionProvider = "guest" | "kakao";

type StoredSession = Session & { provider: SessionProvider };

export type AuthState =
    | { status: "loading" }
    | { status: "signedOut"; startAt: "intro" | "login" }
    | { status: "onboarding" }
    | { status: "signedIn" };

const SESSION_KEY = "remembrall.session";

let session: StoredSession | null = null;
let state: AuthState = { status: "loading" };
const listeners = new Set<() => void>();

function setState(next: AuthState) {
    state = next;
    listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

export function useAuthState(): AuthState {
    return useSyncExternalStore(subscribe, () => state);
}

// API 클라이언트가 Bearer 헤더에 쓴다.
export function getSessionToken(): string | undefined {
    return session?.sessionToken;
}

export function getSessionProvider(): SessionProvider | undefined {
    return session?.provider;
}

export function isSession(value: unknown): value is Session {
    return (
        typeof value === "object" &&
        value != null &&
        "sessionToken" in value &&
        typeof value.sessionToken === "string" &&
        "sessionExpiresAt" in value &&
        typeof value.sessionExpiresAt === "string"
    );
}

function isStoredSession(value: unknown): value is StoredSession {
    return (
        isSession(value) &&
        "provider" in value &&
        (value.provider === "guest" || value.provider === "kakao")
    );
}

function isExpired({ sessionExpiresAt }: Session) {
    const expiresAt = Date.parse(sessionExpiresAt);
    return Number.isNaN(expiresAt) || expiresAt <= Date.now();
}

// 지금 세션이 만료됐는지. 클라이언트가 요청 전에 미리 다시 받을지 정한다.
export function isSessionExpired(): boolean {
    return session != null && isExpired(session);
}

async function readStoredSession(): Promise<StoredSession | null> {
    const raw = await readSecureItem(SESSION_KEY);
    if (raw == null) return null;
    try {
        const parsed: unknown = JSON.parse(raw);
        return isStoredSession(parsed) ? parsed : null;
    } catch {
        return null;
    }
}

// 앱 시작 때 한 번. 저장된 세션이 있으면 근처 탭, 없으면 00a 부터.
// 게스트 세션은 만료돼 있어도 근처 탭으로 간다(첫 요청 전에 다시 받는다). 다른 제공자 세션이 만료됐으면 00a 부터.
export async function restoreSession(): Promise<void> {
    if (!isApiConfigured) {
        setState(
            MOCK_SCENARIO.signedIn
                ? { status: "signedIn" }
                : { status: "signedOut", startAt: "intro" },
        );
        return;
    }
    const stored = await readStoredSession();
    if (stored != null && (stored.provider === "guest" || !isExpired(stored))) {
        session = stored;
        setState({ status: "signedIn" });
        return;
    }
    if (stored != null) await deleteSecureItem(SESSION_KEY);
    setState({ status: "signedOut", startAt: "intro" });
}

// 로그인 응답을 저장한다. 목 모드에서는 부르지 않는다. 저장소가 없는 빌드에서는 메모리에만 둔다.
export async function saveSession(
    next: Session,
    provider: SessionProvider,
): Promise<void> {
    session = { ...next, provider };
    await writeSecureItem(SESSION_KEY, JSON.stringify(session));
}

// 로그인 성공. 권한 단계(00c)로 넘어간다.
export function completeLogin(): void {
    setState({ status: "onboarding" });
}

// 00f "시작하기". 근처 탭으로 간다.
export function finishOnboarding(): void {
    setState({ status: "signedIn" });
}

// 세션을 지우고 startAt 화면부터 다시 보인다. 게스트 재로그인이 거절되면 00a(intro), 카카오 세션 401 이면 00b(login).
export async function clearSession(
    startAt: "intro" | "login" = "intro",
): Promise<void> {
    session = null;
    await deleteSecureItem(SESSION_KEY);
    setState({ status: "signedOut", startAt });
}
