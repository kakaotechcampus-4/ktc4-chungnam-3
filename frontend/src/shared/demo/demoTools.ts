// 설정의 "시연 도구" 묶음 자리. 개발 모드(__DEV__)이거나 EXPO_PUBLIC_DEMO_TOOLS 가 "1"(preview 빌드)일 때만 켜진다.
// 묶음은 runtime(지오펜스)을 불러야 해서 app 이 만들고 여기 등록한다. 설정(features)은 등록된 묶음을 그리기만 한다
// (features 는 runtime · app 을 import 하지 않는다). 꺼진 빌드에서는 등록되지 않아 설정에 아무것도 그리지 않는다.
import type { ComponentType } from "react";

export const isDemoToolsEnabled =
    __DEV__ || process.env.EXPO_PUBLIC_DEMO_TOOLS === "1";

let section: ComponentType | null = null;

export function registerDemoToolsSection(component: ComponentType): void {
    if (isDemoToolsEnabled) section = component;
}

export function getDemoToolsSection(): ComponentType | null {
    return section;
}
