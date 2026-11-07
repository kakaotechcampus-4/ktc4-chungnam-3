// 위치 설정(위치 정보 이용 동의 · 위치 권한 요청)이 바뀌었다는 신호. 순수 JS.
// 바꾸는 쪽(shared/api/consents · shared/permissions/location)이 알리고, app 의 hook 이 받아 지오펜스를 다시 등록한다.
// features 는 runtime 을 import 하지 않으므로 이 신호를 거친다. OS 설정에서 바꾼 권한은 앱이 앞으로 올 때의 재등록이 맡는다.

const listeners = new Set<() => void>();

export function notifyLocationSettingsChanged(): void {
    listeners.forEach((listener) => listener());
}

// 돌려준 함수로 그만 받는다.
export function onLocationSettingsChanged(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}
