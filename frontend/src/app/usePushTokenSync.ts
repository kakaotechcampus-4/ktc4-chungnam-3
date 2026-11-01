// FCM 토큰 등록 시점(runtime/notifications/pushTokenSync). RootNavigator 가 인증 상태로 부른다(usePendingSaveSender 와 같은 자리).
// signedIn 이 된 직후 한 번, 그 뒤 앱이 앞으로 돌아올 때마다 부르고, signedIn 인 동안 토큰 변경을 구독한다.
// signedIn 이 아니게 되면 한 번 불러 기억한 (세션, 토큰) 쌍을 지운다. OS 설정에서 켠 알림 권한은 앱이 앞으로 돌아올 때 반영된다.
// 결과는 화면에 보이지 않는다.
import { useEffect } from "react";
import { AppState } from "react-native";

import { syncPushToken } from "../runtime/notifications/pushTokenSync";
import { addFcmTokenListener } from "../shared/permissions/notifications";

export function usePushTokenSync(signedIn: boolean): void {
    useEffect(() => {
        syncPushToken();
        if (!signedIn) return;
        const subscription = AppState.addEventListener("change", (state) => {
            if (state === "active") syncPushToken();
        });
        const stopListening = addFcmTokenListener((token) => {
            syncPushToken(token);
        });
        return () => {
            subscription.remove();
            stopListening();
        };
    }, [signedIn]);
}
