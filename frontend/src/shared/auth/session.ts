// 인증 상태. RootNavigator 가 이 상태로 Onboarding / Main 화면을 고른다.
// loading: 저장된 세션을 읽는 중(스플래시 유지). signedOut: 세션 없음, startAt 화면부터 보인다.
// onboarding: 로그인은 끝났고 권한 단계(00c~00f) 중이다. signedIn: 근처 탭부터.
// 지금은 메모리에만 둔다. 2번 커밋에서 expo-secure-store 저장 · 복원과 만료 확인을 붙인다.
import { useSyncExternalStore } from "react";

import { MOCK_SCENARIO } from "../api/mock";

export type AuthState =
    | { status: "loading" }
    | { status: "signedOut"; startAt: "intro" | "login" }
    | { status: "onboarding" }
    | { status: "signedIn" };

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

// 앱 시작 때 한 번. 저장된 세션이 있으면 근처 탭, 없으면 00a 부터.
export async function restoreSession(): Promise<void> {
    setState(
        MOCK_SCENARIO.signedIn
            ? { status: "signedIn" }
            : { status: "signedOut", startAt: "intro" },
    );
}

// 로그인 성공. 권한 단계(00c)로 넘어간다.
export function completeLogin(): void {
    setState({ status: "onboarding" });
}

// 00f "시작하기". 근처 탭으로 간다.
export function finishOnboarding(): void {
    setState({ status: "signedIn" });
}

// 로그아웃 · 401. 00b 부터 다시 보인다.
export function signOut(): void {
    setState({ status: "signedOut", startAt: "login" });
}
