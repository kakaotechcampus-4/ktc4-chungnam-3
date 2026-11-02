// 실행 모션(app/launch/LaunchSplash)의 앞장이 내려앉을 자리. docs/design/c-launch-motion.md §3.3 의 S2.
// 도착 화면(근처 첫 카드 · 00a 맨 앞 사진)이 화면 좌표로 재서 보고하고, App 이 읽어 LaunchSplash 에 넘긴다.
// undefined: 아직 보고 전, null: 도착 대상 없음(빈 근처 등). 이번 실행의 첫 보고만 받고, 모션이 끝나면 더 받지 않는다.
import { useSyncExternalStore } from "react";

export type LaunchRect = {
    left: number;
    top: number;
    width: number;
    height: number;
    radius: number;
    // 도 단위. 도착 대상이 기울어 있으면 앞장도 그만큼 돌며 내려앉는다.
    rotate: number;
};

let target: LaunchRect | null | undefined;
let ended = false;
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

export function reportLaunchTarget(rect: LaunchRect | null): void {
    if (ended || target !== undefined) return;
    target = rect;
    listeners.forEach((listener) => listener());
}

export function useLaunchTarget(): LaunchRect | null | undefined {
    return useSyncExternalStore(subscribe, () => target);
}

// 모션이 끝나면 부른다. 뒤에 다시 그려진 도착 화면의 보고는 무시한다.
export function endLaunch(): void {
    ended = true;
}
