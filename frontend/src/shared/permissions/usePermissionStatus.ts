// 설정(11)이 보여 주는 기기 권한 상태. 화면에 들어올 때와 앱이 앞으로 돌아올 때 다시 읽는다.
// 시스템 설정에서 권한을 바꾸고 돌아오면 바로 갱신된다. 읽기 전에는 undefined 다.
import { useEffect, useState } from "react";
import { AppState } from "react-native";

import { getLocationPermission, type LocationPermission } from "./location";
import {
    getNotificationPermission,
    type NotificationPermission,
} from "./notifications";

type PermissionStatus = {
    location?: LocationPermission;
    notifications?: NotificationPermission;
};

export function usePermissionStatus(): PermissionStatus {
    const [status, setStatus] = useState<PermissionStatus>({});

    useEffect(() => {
        let active = true;
        const read = async () => {
            const [location, notifications] = await Promise.all([
                getLocationPermission(),
                getNotificationPermission(),
            ]);
            if (active) setStatus({ location, notifications });
        };
        read();
        const subscription = AppState.addEventListener("change", (state) => {
            if (state === "active") read();
        });
        return () => {
            active = false;
            subscription.remove();
        };
    }, []);

    return status;
}
