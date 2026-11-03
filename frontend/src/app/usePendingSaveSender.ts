// 저장 대기열(runtime/background/pendingQueue)을 다시 보내는 시점. RootNavigator 가 인증 상태로 부른다.
// signedIn 이 된 직후(온보딩을 마치고 Main 에 들어왔을 때 포함) 한 번, 그 뒤 앱이 앞으로 돌아올 때마다 보낸다.
// 세션이 없거나 온보딩 중에는 보내지 않는다. 결과는 아직 화면에 보이지 않는다.
import { useEffect } from "react";
import { AppState } from "react-native";

import { drain } from "../runtime/background/pendingQueue";

export function usePendingSaveSender(signedIn: boolean): void {
    useEffect(() => {
        if (!signedIn) return;
        drain();
        const subscription = AppState.addEventListener("change", (state) => {
            if (state === "active") drain();
        });
        return () => subscription.remove();
    }, [signedIn]);
}
