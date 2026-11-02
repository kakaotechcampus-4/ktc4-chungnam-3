// 지오펜스 백그라운드 태스크. index.js 가 번들 최상위에서 defineGeofenceTask 를 부른다
// (앱이 백그라운드로 깨어나면 컴포넌트가 마운트되지 않고, 정의가 없으면 태스크 매니저가 태스크를 해제한다).
// 태스크 매니저가 없는 빌드에서는 경고를 한 번 남기고 넘어간다(앱은 그대로 뜬다).
// 처리: 세션 다시 받기 등록 → 세션 복원(아직 읽기 전일 때만) → signedIn 이 아니면 해제(signedOut) 또는 무시(온보딩 중)
//   → 들어옴 · 나감을 handleGeofenceEvent 로 → 재조회 지오펜스 나감이면 다시 등록 → 남은 도착 보고 보내기.
// 태스크 데이터에는 eventType(1 들어옴 · 2 나감, expo-location GeofencingEventType)과 region 만 있다. 오류가 온 이벤트는 무시한다.
import { registerSessionRefresher } from "../../shared/auth/login";
import { ensureSessionRestored } from "../../shared/auth/session";
import { flushLocationEvents, handleGeofenceEvent } from "./geofenceManager";
import { syncGeofences } from "./geofenceSync";
import { GEOFENCE_TASK, loadTaskManager } from "./taskManager";

const EVENT_ENTER = 1;
const EVENT_EXIT = 2;

type GeofenceTaskData = {
    eventType: number;
    region: { identifier: string };
};

export function defineGeofenceTask(): void {
    const taskManager = loadTaskManager();
    if (taskManager == null) {
        console.warn(
            "expo-task-manager 네이티브 모듈이 없는 빌드입니다. 근처 도착 알림(지오펜스)은 개발 빌드를 다시 만든 뒤에 동작합니다.",
        );
        return;
    }
    if (taskManager.isTaskDefined(GEOFENCE_TASK)) return;
    taskManager.defineTask(GEOFENCE_TASK, handleGeofenceTask);
}

export async function handleGeofenceTask({
    data,
    error,
}: {
    data?: unknown;
    error?: unknown;
}): Promise<void> {
    if (error != null || !isGeofenceTaskData(data)) return;
    const kind =
        data.eventType === EVENT_ENTER
            ? "enter"
            : data.eventType === EVENT_EXIT
              ? "exit"
              : null;
    if (kind == null) return;

    registerSessionRefresher();
    const auth = await ensureSessionRestored();
    if (auth.status === "signedOut") {
        await syncGeofences("signedOut");
        return;
    }
    if (auth.status !== "signedIn") return;

    const outcome = await handleGeofenceEvent(
        { identifier: data.region.identifier, kind },
        new Date(),
    );
    if (outcome.kind === "resyncNeeded") await syncGeofences("resync");
    await flushLocationEvents();
}

function isGeofenceTaskData(value: unknown): value is GeofenceTaskData {
    if (typeof value !== "object" || value == null) return false;
    if (!("eventType" in value) || typeof value.eventType !== "number") {
        return false;
    }
    if (!("region" in value)) return false;
    const { region } = value;
    return (
        typeof region === "object" &&
        region != null &&
        "identifier" in region &&
        typeof region.identifier === "string"
    );
}
