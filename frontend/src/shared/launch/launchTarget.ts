// 실행 모션(app/launch/LaunchSplash)의 앞장이 내려앉을 자리. docs/design/c-launch-motion.md §3.5 의 S2.
// 도착 화면(근처 첫 카드 · 00a 맨 앞 사진)이 창 좌표로 재서 보고하고, App 이 읽어 LaunchSplash 에 넘긴다.
// undefined: 아직 보고 전, null: 도착 대상 없음(빈 근처 등). 이번 실행의 첫 보고만 받고, 모션이 끝나면 더 받지 않는다.
import { useRef, useSyncExternalStore } from "react";
import type { View } from "react-native";

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

function accepting() {
    return !ended && target === undefined;
}

export function reportLaunchTarget(rect: LaunchRect | null): void {
    if (!accepting()) return;
    target = rect;
    listeners.forEach((listener) => listener());
}

export type WindowFrame = {
    x: number;
    y: number;
    width: number;
    height: number;
};

// 도착 화면용. 돌려준 ref · onLayout 을 붙인 뷰를 처음 그렸을 때 창 좌표로 재고, toRect 로 바꿔 보고한다.
// 기울어진 뷰는 재면 회전된 꼭짓점이 나오므로, 기울지 않은 부모를 재고 toRect 에서 자식 자리를 계산한다(PhotoStack).
export function useLaunchTargetMeasure(
    toRect: (frame: WindowFrame) => LaunchRect,
) {
    const ref = useRef<View>(null);
    const onLayout = () => {
        if (!accepting()) return;
        ref.current?.measureInWindow((x, y, width, height) =>
            reportLaunchTarget(toRect({ x, y, width, height })),
        );
    };
    return { ref, onLayout };
}

export function useLaunchTarget(): LaunchRect | null | undefined {
    return useSyncExternalStore(subscribe, () => target);
}

// 모션이 끝나면 부른다. 뒤에 다시 그려진 도착 화면의 보고는 무시한다.
export function endLaunch(): void {
    ended = true;
}
