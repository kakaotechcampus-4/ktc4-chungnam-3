// 지오펜스 (재)등록 · 해제. 앱(app/useGeofenceSync)과 백그라운드 태스크(재조회 지오펜스 나감)가 부른다.
// 등록 조건: signedIn, 위치 권한 '항상 허용', 위치 동의 agreed(outdated · withdrawn · never 는 안 됨).
// 맞으면: 현재 위치 → 목록 받기 → 등록 계획(이전 장소 상태 유지) → OS 등록(목록이 비면 해제) → 상태 저장 → 도착 보고 보내기.
// 조건이 확실히 안 맞으면(signedIn 아님 · 권한 always 아님 · 동의 agreed 아님) 해제하고 상태 · 대기열을 지운다.
// 권한 · 동의 읽기 · 위치 · 목록 받기 · 등록이 실패하면 지금 등록을 그대로 두고 failed 다(다음 호출이 다시 한다).
// 태스크 매니저 · expo-location 이 없는 빌드에서는 아무것도 하지 않고 skipped 다(상태도 지우지 않는다).
// 해제: 백그라운드 위치 권한이 없으면 stopGeofencingAsync 가 예외를 던지므로 그때는 태스크를 직접 해제한다(unregisterTaskAsync).
// 실행 중에 또 불리면 하나로 합치고, 끝난 뒤 한 번 더 돈다(그 사이 바뀐 동의 · 권한을 놓치지 않게).
import { getAuthState } from "../../shared/auth/session";
import { fetchConsents } from "../../shared/api/consents";
import { fetchGeofences } from "../../shared/api/geofences";
import { toConsentState } from "../../shared/api/mappers/consentMapper";
import {
    getGeofencePosition,
    getLocationPermission,
    startGeofencing,
    stopGeofencing,
} from "../../shared/permissions/location";
import {
    applyRegistration,
    clearGeofenceState,
    flushLocationEvents,
    getPlaceFenceStates,
} from "./geofenceManager";
import { planRegistration } from "./registrationPlan";
import { GEOFENCE_TASK, loadTaskManager } from "./taskManager";

export type GeofenceSyncReason =
    | "signedIn"
    | "signedOut"
    | "foreground"
    | "settingsChanged"
    | "resync";

// registered.places 가 0 이면 해제했다(목록이 비었음).
export type GeofenceSyncResult =
    | { kind: "registered"; places: number }
    | { kind: "cleared"; because: "signedOut" | "permission" | "consent" }
    | { kind: "skipped"; because: "unavailable" }
    | {
          kind: "failed";
          step: "permission" | "consent" | "position" | "fetch" | "start";
      };

type TaskManagerModule = NonNullable<ReturnType<typeof loadTaskManager>>;

let running: Promise<GeofenceSyncResult> | undefined;
let queued: Promise<GeofenceSyncResult> | undefined;

// reason 은 부른 이유다(조건은 매번 새로 읽는다).
export function syncGeofences(
    reason: GeofenceSyncReason,
): Promise<GeofenceSyncResult> {
    if (running == null) {
        running = sync().finally(() => {
            running = undefined;
        });
        return running;
    }
    queued ??= running.then(() => {
        // 앞 실행은 예외를 던지지 않는다(실패는 failed 결과다).
        queued = undefined;
        return syncGeofences(reason);
    });
    return queued;
}

async function sync(): Promise<GeofenceSyncResult> {
    const taskManager = loadTaskManager();
    if (taskManager == null) return { kind: "skipped", because: "unavailable" };
    if (getAuthState().status !== "signedIn") {
        return clear(taskManager, "signedOut");
    }
    let permission;
    try {
        permission = await getLocationPermission();
    } catch {
        return { kind: "failed", step: "permission" };
    }
    if (permission === "unavailable") {
        return { kind: "skipped", because: "unavailable" };
    }
    if (permission !== "always") return clear(taskManager, "permission");

    let consented: boolean;
    try {
        consented =
            toConsentState(await fetchConsents(), "LOCATION_BASED_SERVICE") ===
            "agreed";
    } catch {
        return { kind: "failed", step: "consent" };
    }
    if (!consented) return clear(taskManager, "consent");

    const position = await getGeofencePosition();
    if (position == null) return { kind: "failed", step: "position" };
    let response;
    try {
        response = await fetchGeofences(position);
    } catch {
        return { kind: "failed", step: "fetch" };
    }
    const plan = planRegistration(
        response,
        position,
        await getPlaceFenceStates(),
    );
    try {
        if (plan.regions.length === 0) {
            await stop(taskManager);
        } else if (
            (await startGeofencing(GEOFENCE_TASK, plan.regions)) ===
            "unavailable"
        ) {
            return { kind: "skipped", because: "unavailable" };
        }
    } catch {
        return { kind: "failed", step: "start" };
    }
    await applyRegistration(plan, new Date());
    await flushLocationEvents();
    return { kind: "registered", places: response.places.length };
}

async function clear(
    taskManager: TaskManagerModule,
    because: "signedOut" | "permission" | "consent",
): Promise<GeofenceSyncResult> {
    await stop(taskManager);
    await clearGeofenceState();
    return { kind: "cleared", because };
}

// 등록돼 있을 때만 해제한다. 위치 권한 없이 stop 이 실패하면 태스크를 직접 해제한다(지오펜스도 함께 지워진다).
async function stop(taskManager: TaskManagerModule): Promise<void> {
    const registered = await taskManager
        .isTaskRegisteredAsync(GEOFENCE_TASK)
        .catch(() => false);
    if (!registered) return;
    if ((await stopGeofencing(GEOFENCE_TASK)) === "stopped") return;
    await taskManager.unregisterTaskAsync(GEOFENCE_TASK).catch(() => {});
}
