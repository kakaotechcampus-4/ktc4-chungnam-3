// 지오펜스 (재)등록 시점. RootNavigator 가 인증 상태로 부른다(usePendingSaveSender 와 같은 자리).
// signedIn 이 된 직후 한 번, 그 뒤 앱이 앞으로 돌아올 때마다, 위치 설정(동의 · 권한 요청)이 바뀐 직후에 다시 등록한다.
// signedOut 이 되면 해제하고 상태를 지운다. 읽는 중(loading) · 온보딩 중에는 부르지 않는다.
// OS 설정에서 바꾼 권한은 앱이 앞으로 돌아올 때 반영된다. 결과는 아직 화면에 보이지 않는다.
import { useEffect } from "react";
import { AppState } from "react-native";

import { syncGeofences } from "../runtime/geofence/geofenceSync";
import type { AuthState } from "../shared/auth/session";
import { onLocationSettingsChanged } from "../shared/permissions/locationSettings";

export function useGeofenceSync(status: AuthState["status"]): void {
    useEffect(() => {
        if (status === "signedOut") {
            syncGeofences("signedOut");
            return;
        }
        if (status !== "signedIn") return;
        syncGeofences("signedIn");
        const subscription = AppState.addEventListener("change", (state) => {
            if (state === "active") syncGeofences("foreground");
        });
        const stopListening = onLocationSettingsChanged(() => {
            syncGeofences("settingsChanged");
        });
        return () => {
            subscription.remove();
            stopListening();
        };
    }, [status]);
}
