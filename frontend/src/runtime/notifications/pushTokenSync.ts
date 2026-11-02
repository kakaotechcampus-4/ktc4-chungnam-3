// 기기 FCM 토큰을 서버에 등록한다. 앱(app/usePushTokenSync)이 부른다.
// 등록 조건: signedIn, 알림 권한 granted(위치 동의와 무관). 토큰은 넘겨받은 값(토큰 변경 리스너) 또는 지금 기기 토큰이다.
// (세션 토큰, FCM 토큰) 쌍을 이번 실행 동안 메모리에만 기억하고 쌍이 바뀔 때만 보낸다.
// 서버는 새 세션마다 토큰을 지우므로 세션도 비교한다. 세션 값을 파일에 두지 않으려고 저장하지 않는다(앱을 켜면 한 번은 보낸다).
// signedIn 이 아니면 기억한 쌍을 지운다. 실패는 조용히 failed 이고 다음 호출(앞으로 돌아올 때 · 다음 실행)이 다시 한다. 재시도 · 백오프는 없다.
// 실행 중에 또 불리면 하나로 합치고, 끝난 뒤 한 번 더 돈다(그 사이 넘겨받은 마지막 토큰으로).
// 마지막 결과(결과 · 시각 · 마지막 등록 시각)를 메모리에 두고 구독할 수 있게 한다. 시연 도구가 읽는다. 토큰 값은 두지 않는다.
import { registerFcmToken } from "../../shared/api/devices";
import { getAuthState, getSessionToken } from "../../shared/auth/session";
import {
    getFcmToken,
    getNotificationPermission,
} from "../../shared/permissions/notifications";

// permission: granted 가 아니다. unavailable: 알림 모듈이 없는 빌드(Expo Go 포함). noToken: 토큰을 받지 못했다(Firebase 설정 없는 빌드 등).
export type PushTokenSyncResult =
    | { kind: "registered" }
    | { kind: "unchanged" }
    | {
          kind: "skipped";
          reason: "notSignedIn" | "permission" | "unavailable" | "noToken";
      }
    | { kind: "failed" };

// at: 이 결과가 난 시각. registeredAt: 마지막으로 서버 등록(PUT)이 성공한 시각(signedIn 이 아니게 되면 null). ISO-8601.
export type LastPushTokenSync = {
    result: PushTokenSyncResult;
    at: string;
    registeredAt: string | null;
};

type SentPair = { sessionToken: string | undefined; fcmToken: string };

let lastSent: SentPair | null = null;
let running: Promise<PushTokenSyncResult> | undefined;
let queued: Promise<PushTokenSyncResult> | undefined;
let queuedToken: string | undefined;
let last: LastPushTokenSync | null = null;
const listeners = new Set<() => void>();

// 이번 실행에서 아직 돌지 않았으면 null.
export function getLastPushTokenSync(): LastPushTokenSync | null {
    return last;
}

export function subscribePushTokenSync(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

function record(result: PushTokenSyncResult): PushTokenSyncResult {
    const at = new Date().toISOString();
    const registeredAt =
        result.kind === "registered"
            ? at
            : result.kind === "skipped" && result.reason === "notSignedIn"
              ? null
              : (last?.registeredAt ?? null);
    last = { result, at, registeredAt };
    listeners.forEach((listener) => listener());
    return result;
}

export function syncPushToken(token?: string): Promise<PushTokenSyncResult> {
    if (running == null) {
        running = sync(token)
            .then(record)
            .finally(() => {
                running = undefined;
            });
        return running;
    }
    if (token != null) queuedToken = token;
    queued ??= running.then(() => {
        // 앞 실행은 예외를 던지지 않는다(실패는 failed 결과다).
        queued = undefined;
        const next = queuedToken;
        queuedToken = undefined;
        return syncPushToken(next);
    });
    return queued;
}

async function sync(token: string | undefined): Promise<PushTokenSyncResult> {
    if (getAuthState().status !== "signedIn") {
        lastSent = null;
        return { kind: "skipped", reason: "notSignedIn" };
    }
    try {
        const permission = await getNotificationPermission();
        if (permission === "unavailable") {
            return { kind: "skipped", reason: "unavailable" };
        }
        if (permission !== "granted") {
            return { kind: "skipped", reason: "permission" };
        }
        const fcmToken = token ?? (await getFcmToken());
        if (fcmToken == null) return { kind: "skipped", reason: "noToken" };
        if (
            lastSent != null &&
            lastSent.fcmToken === fcmToken &&
            lastSent.sessionToken === getSessionToken()
        ) {
            return { kind: "unchanged" };
        }
        await registerFcmToken(fcmToken);
        // 401 뒤 세션을 다시 받았으면 새 세션으로 등록됐다. 보낸 뒤의 세션을 기억한다.
        // 보내는 동안 로그아웃됐으면 기억하지 않는다.
        if (getAuthState().status === "signedIn") {
            lastSent = { sessionToken: getSessionToken(), fcmToken };
        }
        return { kind: "registered" };
    } catch {
        return { kind: "failed" };
    }
}
