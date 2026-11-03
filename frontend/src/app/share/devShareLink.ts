// 개발용 공유 진입. remembrall://dev/share?text=<인코딩된 텍스트>(Expo Go 는 exp://<IP>:8081/--/dev/share?text=…).
// 실제 공유 인텐트 없이 같은 처리 흐름(receiveSharedText)을 부른다. 받는 쪽(ShareReceiverHost)은 __DEV__ 에서만 처리한다.
// linking 설정은 이 주소를 화면 경로로 해석하지 않는다(filter).
// expo-linking 의 parse 는 쿼리 값을 두 번 풀어 '%' 가 든 텍스트가 깨질 수 있어 정규식으로 나눈다.

// scheme://[Expo Go 의 host/--/]경로[?쿼리]
const URL_PARTS =
    /^[a-z][a-z0-9+.-]*:\/\/(?:[^/?#]*\/--\/)?([^?#]*)(?:\?([^#]*))?/i;
const DEV_SHARE_PATH = "dev/share";

// 개발용 공유 주소면 text 값, 아니면 null. text 가 없으면 빈 문자열(링크 없음)이다.
export function parseDevShareUrl(url: string): string | null {
    const parts = URL_PARTS.exec(url);
    if (parts == null || parts[1].replace(/\/$/, "") !== DEV_SHARE_PATH) {
        return null;
    }
    for (const pair of (parts[2] ?? "").split("&")) {
        const [key, value = ""] = pair.split("=");
        if (key === "text") return decodeQueryValue(value);
    }
    return "";
}

function decodeQueryValue(value: string): string {
    const spaced = value.replace(/\+/g, " ");
    try {
        return decodeURIComponent(spaced);
    } catch {
        return spaced;
    }
}
