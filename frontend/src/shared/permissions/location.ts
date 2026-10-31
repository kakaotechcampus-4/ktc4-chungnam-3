// 위치 권한. 00d(앱 사용 중) · 00e(항상 허용) · 설정(11)이 쓴다. 지오펜스 등록 · 해제와 그때 쓰는 현재 위치도 여기서 한다
// (expo-location 은 shared/permissions 밖에서 import 하지 않는다). 권한을 요청한 뒤에는 위치 설정 신호를 낸다.
// 근처 감지(OS 지오펜스)는 앱이 닫혀 있어도 동작해야 해서 '항상 허용'이 필요하다. 앱 사용 중 권한 없이는 요청할 수 없다.
// expo-location 은 처음 쓸 때 불러온다. 이 모듈이 없는 개발 빌드에서 파일 맨 위에서 import 하면
// import 만으로 앱이 멈춘다. 모듈이 없으면 "unavailable" 을 돌려주고 경고한다.
import type { LatLng } from "../geo/distanceMeters";
import { notifyLocationSettingsChanged } from "./locationSettings";

type LocationModule = typeof import("expo-location");
let location: LocationModule | null | undefined;

function loadLocation(): LocationModule | null {
    if (location === undefined) {
        try {
            location = require("expo-location") as LocationModule;
        } catch {
            location = null;
            console.warn(
                "expo-location 네이티브 모듈이 없는 빌드입니다. 위치 권한을 확인하지 않습니다. 개발 빌드를 다시 만드세요.",
            );
        }
    }
    return location;
}

// 안드로이드 위치 권한 3단계와 같다.
export type LocationPermission =
    | "always"
    | "whileInUse"
    | "denied"
    | "unavailable";

export type LocationRequestResult = "granted" | "denied" | "unavailable";

export async function getLocationPermission(): Promise<LocationPermission> {
    const module = loadLocation();
    if (module == null) return "unavailable";
    const foreground = await module.getForegroundPermissionsAsync();
    if (!foreground.granted) return "denied";
    try {
        const background = await module.getBackgroundPermissionsAsync();
        return background.granted ? "always" : "whileInUse";
    } catch {
        // 백그라운드 위치를 선언하지 않은 앱(Expo Go)은 조회가 실패한다.
        return "whileInUse";
    }
}

// 00d. 시스템 창에서 '앱 사용 중'(또는 '이번만')을 고르면 granted.
export async function requestForegroundLocation(): Promise<LocationRequestResult> {
    const module = loadLocation();
    if (module == null) return "unavailable";
    const { granted } = await module
        .requestForegroundPermissionsAsync()
        .finally(notifyLocationSettingsChanged);
    return granted ? "granted" : "denied";
}

// 00e. 안드로이드 11+ 는 시스템이 이 앱의 위치 권한 화면을 연다. 사용자가 돌아오면 끝난다.
export async function requestBackgroundLocation(): Promise<LocationRequestResult> {
    const module = loadLocation();
    if (module == null) return "unavailable";
    try {
        const { granted } = await module.requestBackgroundPermissionsAsync();
        return granted ? "granted" : "denied";
    } catch {
        // 백그라운드 위치를 선언하지 않은 앱(Expo Go)은 요청이 실패한다.
        return "denied";
    } finally {
        notifyLocationSettingsChanged();
    }
}

// 지오펜스 등록에 쓰는 현재 위치. 백그라운드 태스크 예산(15초) 안에 끝나게 한다.
// 마지막 위치(LAST_KNOWN_MAX_AGE_MS 이내 · 정확도 LAST_KNOWN_ACCURACY_METERS 이하) → 없으면 현재 위치(Balanced,
// 설정 대화상자 없음, CURRENT_POSITION_TIMEOUT_MS 제한) → 그래도 없으면 null.
const LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 1000;
const LAST_KNOWN_ACCURACY_METERS = 200;
const CURRENT_POSITION_TIMEOUT_MS = 5_000;

export async function getGeofencePosition(): Promise<LatLng | null> {
    const module = loadLocation();
    if (module == null) return null;
    try {
        const last = await module.getLastKnownPositionAsync({
            maxAge: LAST_KNOWN_MAX_AGE_MS,
            requiredAccuracy: LAST_KNOWN_ACCURACY_METERS,
        });
        if (last != null) return toLatLng(last.coords);
    } catch {
        // 아래에서 현재 위치를 받는다.
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<null>((resolve) => {
        timer = setTimeout(() => resolve(null), CURRENT_POSITION_TIMEOUT_MS);
    });
    try {
        const current = await Promise.race([
            module.getCurrentPositionAsync({
                accuracy: module.Accuracy.Balanced,
                mayShowUserSettingsDialog: false,
            }),
            timeout,
        ]);
        return current == null ? null : toLatLng(current.coords);
    } catch {
        return null;
    } finally {
        clearTimeout(timer);
    }
}

// expo-location LocationRegion 과 같은 모양.
export type GeofencingRegion = {
    identifier: string;
    latitude: number;
    longitude: number;
    radius: number;
    notifyOnEnter: boolean;
    notifyOnExit: boolean;
};

// 같은 태스크 이름으로 다시 부르면 등록 목록 전체가 바뀐다. 실패(태스크 매니저 없음 · 백그라운드 권한 없음)는 예외로 넘긴다.
export async function startGeofencing(
    taskName: string,
    regions: readonly GeofencingRegion[],
): Promise<"started" | "unavailable"> {
    const module = loadLocation();
    if (module == null) return "unavailable";
    await module.startGeofencingAsync(taskName, [...regions]);
    return "started";
}

// 백그라운드 위치 권한이 없으면 stopGeofencingAsync 가 예외를 던진다. 그때는 failed 이고, 부르는 쪽이 태스크를 직접 해제한다.
export async function stopGeofencing(
    taskName: string,
): Promise<"stopped" | "failed" | "unavailable"> {
    const module = loadLocation();
    if (module == null) return "unavailable";
    try {
        await module.stopGeofencingAsync(taskName);
        return "stopped";
    } catch {
        return "failed";
    }
}

function toLatLng(coords: { latitude: number; longitude: number }): LatLng {
    return { latitude: coords.latitude, longitude: coords.longitude };
}
