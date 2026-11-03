// 지금 위치를 읽는 hook. 지도 탭(10) · 장소 상세(08) · 근처 탭이 쓴다.
// 화면에 포커스될 때, 포커스된 동안 앱이 다시 앞으로 올 때, 위치 설정 신호(권한 요청 뒤 등)가 올 때 한 번씩 읽는다
// (움직임을 계속 따라가지 않는다).
// 실제 모드: 위치 권한이 always · whileInUse 면 getGeofencePosition(마지막 위치 → 현재 위치 5초 제한)으로 읽고,
// 받지 못하면 unknown. 권한이 없거나 위치 모듈이 없으면 off.
// 목 모드(API 주소 없음): MOCK_SCENARIO.locationPermission 이 denied 면 off, 아니면 목 좌표
// (mapViewMock.current, MOCK_SCENARIO.nearbyEmpty 면 nearbyMock.empty.current).
// 다시 읽는 동안은 이전 값을 둔다. 마지막 읽기만 반영하고, 화면을 떠난 뒤에 온 결과는 버린다.
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import { isApiConfigured } from "../api/config";
import { MOCK_SCENARIO, mapViewMock, nearbyMock } from "../api/mock";
import type { LatLng } from "../geo/distanceMeters";
import { getGeofencePosition, getLocationPermission } from "./location";
import { onLocationSettingsChanged } from "./locationSettings";

export type DeviceLocation =
    | { status: "loading" }
    | { status: "off" }
    | { status: "unknown" }
    | { status: "located"; coordinate: LatLng };

export function useDeviceLocation(): DeviceLocation {
    const [location, setLocation] = useState<DeviceLocation>(() =>
        isApiConfigured ? { status: "loading" } : mockLocation(),
    );
    const readId = useRef(0);

    const read = useCallback(() => {
        if (!isApiConfigured) return;
        const id = ++readId.current;
        readLocation().then((next) => {
            if (id === readId.current) setLocation(next);
        });
    }, []);

    useFocusEffect(
        useCallback(() => {
            read();
            const subscription = AppState.addEventListener(
                "change",
                (state) => {
                    if (state === "active") read();
                },
            );
            const stopSettings = onLocationSettingsChanged(read);
            return () => {
                subscription.remove();
                stopSettings();
                readId.current += 1;
            };
        }, [read]),
    );

    return location;
}

async function readLocation(): Promise<DeviceLocation> {
    const permission = await getLocationPermission();
    if (permission !== "always" && permission !== "whileInUse") {
        return { status: "off" };
    }
    const coordinate = await getGeofencePosition();
    return coordinate == null
        ? { status: "unknown" }
        : { status: "located", coordinate };
}

function mockLocation(): DeviceLocation {
    if (MOCK_SCENARIO.locationPermission === "denied") return { status: "off" };
    return {
        status: "located",
        coordinate: MOCK_SCENARIO.nearbyEmpty
            ? nearbyMock.empty.current
            : mapViewMock.current,
    };
}
