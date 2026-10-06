// 인증 상태와 세션. RootNavigator 가 이 상태로 Onboarding / Main 화면을 고른다.
// loading: 저장된 세션을 읽는 중(스플래시 유지). signedOut: 세션 없음, startAt 화면부터 보인다.
// onboarding: 로그인은 끝났고 권한 단계(00c~00f) 중이다. signedIn: 근처 탭부터.
// 세션은 expo-secure-store(Android Keystore)에만 둔다. 리프레시 토큰은 없다. 만료되면 다시 로그인한다.
// 목 모드(API 주소 없음)에서는 저장소를 쓰지 않고 MOCK_SCENARIO.signedIn 으로 시작한다.
// 순환 방지: 이 파일은 API 클라이언트를 import 하지 않는다.
import { useSyncExternalStore } from "react";

import { isApiConfigured } from "../api/config";
import { MOCK_SCENARIO } from "../api/mock";

// 백엔드 POST /api/auth/{제공자} 응답 그대로. sessionExpiresAt 은 ISO-8601 UTC.
export type Session = {
    sessionToken: string;
    sessionExpiresAt: string;
};

export type AuthState =
    | { status: "loading" }
    | { status: "signedOut"; startAt: "intro" | "login" }
    | { status: "onboarding" }
    | { status: "signedIn" };

const SESSION_KEY = "remembrall.session";

// expo-secure-store 는 실제 모드에서 처음 쓸 때 불러온다. 네이티브 모듈이 없는 빌드(이 모듈을 넣기 전의 개발 빌드)에서
// 파일 맨 위에서 import 하면 import 만으로 앱이 멈춘다. 목 모드는 이 모듈을 전혀 불러오지 않는다.
// 모듈이 없으면 앱을 멈추지 않고 세션을 이번 실행 동안 메모리에만 둔다(다음 실행 때 다시 로그인).
type SecureStoreModule = typeof import("expo-secure-store");
let secureStore: SecureStoreModule | null | undefined;

function loadSecureStore(): SecureStoreModule | null {
    if (secureStore === undefined) {
        try {
            secureStore = require("expo-secure-store") as SecureStoreModule;
        } catch {
            secureStore = null;
            console.warn(
                "expo-secure-store 네이티브 모듈이 없는 빌드입니다. 세션을 이번 실행 동안만 메모리에 둡니다. 개발 빌드를 다시 만드세요.",
            );
        }
    }
    return secureStore;
}

let session: Session | null = null;
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

function isExpired({ sessionExpiresAt }: Session) {
    const expiresAt = Date.parse(sessionExpiresAt);
    return Number.isNaN(expiresAt) || expiresAt <= Date.now();
}

async function readStoredSession(): Promise<Session | null> {
    try {
        const store = loadSecureStore();
        if (store == null) return null;
        const raw = await store.getItemAsync(SESSION_KEY);
        if (raw == null) return null;
        const parsed: unknown = JSON.parse(raw);
        return isSession(parsed) ? parsed : null;
    } catch {
        // 읽지 못하면 세션이 없는 것으로 본다.
        return null;
    }
}

async function deleteStoredSession() {
    try {
        await loadSecureStore()?.deleteItemAsync(SESSION_KEY);
    } catch {
        // 지우지 못해도 메모리 세션은 비웠다.
    }
}

// 앱 시작 때 한 번. 저장된 세션이 있으면 근처 탭, 없거나 만료됐으면 00a 부터.
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
    if (stored != null && !isExpired(stored)) {
        session = stored;
        setState({ status: "signedIn" });
        return;
    }
    if (stored != null) await deleteStoredSession();
    setState({ status: "signedOut", startAt: "intro" });
}

// 로그인 응답을 저장한다. 목 모드에서는 부르지 않는다. 모듈이 없는 빌드에서는 메모리에만 둔다.
export async function saveSession(next: Session): Promise<void> {
    session = next;
    await loadSecureStore()?.setItemAsync(SESSION_KEY, JSON.stringify(next));
}

// 로그인 성공. 권한 단계(00c)로 넘어간다.
export function completeLogin(): void {
    setState({ status: "onboarding" });
}

// 00f "시작하기". 근처 탭으로 간다.
export function finishOnboarding(): void {
    setState({ status: "signedIn" });
}

// 로그아웃 · 401. 세션을 지우고 00b 부터 다시 보인다.
export async function clearSession(): Promise<void> {
    session = null;
    await deleteStoredSession();
    setState({ status: "signedOut", startAt: "login" });
}
