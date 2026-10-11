// 위치 권한. 00d(앱 사용 중) · 00e(항상 허용) · 설정(11)이 쓴다.
// 근처 감지(OS 지오펜스)는 앱이 닫혀 있어도 동작해야 해서 '항상 허용'이 필요하다. 앱 사용 중 권한 없이는 요청할 수 없다.
// expo-location 은 처음 쓸 때 불러온다. 이 모듈이 없는 개발 빌드에서 파일 맨 위에서 import 하면
// import 만으로 앱이 멈춘다. 모듈이 없으면 "unavailable" 을 돌려주고 경고한다.
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
    const { granted } = await module.requestForegroundPermissionsAsync();
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
    }
}
