// 실제 공유 진입점. expo-share-intent(Android ACTION_SEND text/plain, MainActivity)가 받은 텍스트를 넘긴다.
// 처음 쓸 때 불러온다. 네이티브 모듈이 없으면(Expo Go · 이 모듈 전에 만든 개발 빌드) 경고를 한 번 남기고 아무것도 하지 않는다.
// 리스너를 먼저 붙이고 getShareIntent 를 한 번 부른다. 콜드 스타트 공유는 그때 onChange 로 오고,
// 실행 중 공유는 네이티브 OnNewIntent 가 바로 onChange 로 보낸다. 넘긴 뒤 네이티브에 남은 공유를 지운다.
// 받은 텍스트는 text, 없으면 webUrl 이다.
type ShareIntentPackage = typeof import("expo-share-intent");

let shareIntentPackage: ShareIntentPackage | null | undefined;

function loadShareIntent(): ShareIntentPackage | null {
    if (shareIntentPackage === undefined) {
        try {
            const loaded = require("expo-share-intent") as ShareIntentPackage;
            shareIntentPackage =
                loaded.ShareIntentModule == null ? null : loaded;
        } catch {
            shareIntentPackage = null;
        }
        if (shareIntentPackage == null) {
            console.warn(
                "expo-share-intent 네이티브 모듈이 없는 빌드입니다. 공유 수신은 개발 빌드를 다시 만든 뒤에 동작합니다.",
            );
        }
    }
    return shareIntentPackage;
}

// 받기를 시작한다. 돌려준 함수로 멈춘다.
export function startNativeShareIntent(
    onText: (text: string) => void,
): () => void {
    const shareIntent = loadShareIntent();
    const native = shareIntent?.ShareIntentModule;
    if (shareIntent == null || native == null) return () => {};

    const subscription = native.addListener("onChange", (event) => {
        const { text, webUrl } = shareIntent.parseShareIntent(event.value, {});
        const shared = text ?? webUrl;
        if (shared != null && shared !== "") onText(shared);
        native.clearShareIntent(shareIntent.getShareExtensionKey());
    });
    native.getShareIntent("");
    return () => subscription.remove();
}
