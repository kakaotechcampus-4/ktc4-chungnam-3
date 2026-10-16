// 안전한 저장소(expo-secure-store, Android Keystore). 세션 · 설치 id 가 쓴다.
// 처음 쓸 때 불러온다. 이 모듈이 없는 개발 빌드에서 파일 맨 위에서 import 하면 import 만으로 앱이 멈춘다.
// 모듈이 없으면 읽기는 null, 쓰기 · 지우기는 아무것도 하지 않고 경고를 한 번 띄운다(호출한 쪽이 메모리에만 둔다).
// 값은 로그 · 경고에 남기지 않는다.
type SecureStoreModule = typeof import("expo-secure-store");
let secureStore: SecureStoreModule | null | undefined;

function loadSecureStore(): SecureStoreModule | null {
    if (secureStore === undefined) {
        try {
            secureStore = require("expo-secure-store") as SecureStoreModule;
        } catch {
            secureStore = null;
            console.warn(
                "expo-secure-store 네이티브 모듈이 없는 빌드입니다. 세션과 설치 id 를 이번 실행 동안만 메모리에 둡니다. 개발 빌드를 다시 만드세요.",
            );
        }
    }
    return secureStore;
}

export function isSecureStoreAvailable(): boolean {
    return loadSecureStore() != null;
}

// 읽지 못하면 null 이다(모듈 없음 · 저장소 오류).
export async function readSecureItem(key: string): Promise<string | null> {
    try {
        return (await loadSecureStore()?.getItemAsync(key)) ?? null;
    } catch {
        return null;
    }
}

export async function writeSecureItem(
    key: string,
    value: string,
): Promise<void> {
    await loadSecureStore()?.setItemAsync(key, value);
}

export async function deleteSecureItem(key: string): Promise<void> {
    try {
        await loadSecureStore()?.deleteItemAsync(key);
    } catch {
        // 지우지 못해도 호출한 쪽의 메모리 값은 비웠다.
    }
}
