// 알림을 눌러 08 을 연다. runtime 의 notificationListener 가 넘긴 id 와 시연 도구가 부른다.
// 대기 칸은 한 칸이고 마지막 것만 둔다. signedIn 이고 네비게이터가 준비돼 ContentDetail 이 등록돼 있으면
// 지금 화면 위에 ContentDetail { notificationId } 를 push 하고 비운다(navigate 는 쓰지 않는다. 같은 이름이 스택에 있으면 그리로 돌아간다).
// 비우는 때: 넘겨받은 즉시 · NavigationContainer onReady(App) · signedIn 이 된 직후 · signedIn 동안 내비게이션 상태가 바뀔 때(화면 등록이 늦게 반영되는 경우).
// signedIn → signedOut 으로 바뀔 때만 대기 칸을 버린다(로그아웃 · 세션을 잃음). 앱 실행 직후의 첫 signedOut(이번 실행에서 아직 signedIn 이 된 적 없음)은
// 버리지 않고, 로그인 · 온보딩을 마쳐 signedIn 이 되면 연다(종료 상태에서 누른 알림으로 로그인 전 화면이 뜬 경우). 위치 동의는 조건이 아니다.
import { StackActions } from "@react-navigation/native";
import { useEffect } from "react";

import { type AuthState, getAuthState } from "../../shared/auth/session";
import { navigationRef } from "../navigation/navigationRef";
import { ROUTES } from "../navigation/routes";

let pending: string | null = null;
// 이번 실행에서 signedIn 이 된 적 있는지. 그 뒤의 signedOut 만 로그아웃으로 보고 대기 칸을 버린다.
let signedInThisRun = false;

export function openFromNotification(notificationId: string): void {
    pending = notificationId;
    flushPendingNotification();
}

export function flushPendingNotification(): void {
    if (pending == null) return;
    if (getAuthState().status !== "signedIn" || !navigationRef.isReady()) {
        return;
    }
    const routeNames = navigationRef.getRootState()?.routeNames;
    if (routeNames?.includes(ROUTES.ContentDetail) !== true) return;
    const notificationId = pending;
    pending = null;
    navigationRef.dispatch(
        StackActions.push(ROUTES.ContentDetail, { notificationId }),
    );
}

// RootNavigator 가 인증 상태로 부른다.
export function useNotificationOpener(status: AuthState["status"]): void {
    useEffect(() => {
        if (status === "signedOut") {
            if (signedInThisRun) pending = null;
            return;
        }
        if (status !== "signedIn") return;
        signedInThisRun = true;
        flushPendingNotification();
        return navigationRef.addListener("state", flushPendingNotification);
    }, [status]);
}
