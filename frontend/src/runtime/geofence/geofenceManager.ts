// 지오펜스 이벤트 처리와 도착 보고 대기열. OS 등록 · 해제와 태스크 연결은 G3(이 파일을 부른다), 가상 도착(G4)도 handleGeofenceEvent 를 그대로 부른다.
// 장소 들어옴: 이미 inside 면 무시한다(Android 초기 트리거 · 다시 등록할 때의 중복 ENTER). 아니면 inside 로 바꾸고
//   도착 보고(triggerEventId 새로 만듦, eventOccurredAt = 처리 시각, location 없음)를 대기열에 넣은 뒤 바로 보낸다.
//   태스크 데이터에는 발생 시각이 없어 처리 시각을 쓴다.
// 장소 나감: outside 로 바꾼다(서버에는 보내지 않는다). 재조회 지오펜스 나감: 다시 받아 등록해야 한다고 돌려준다. 모르는 id 는 무시한다.
// 대기열 보내기: 앞에서부터 하나씩. sent 는 지우고, 401 이 아닌 4xx 는 버린다. timeout · networkError · serverError · 401 은
//   남기고 이번 보내기를 멈춘다(다음에 같은 triggerEventId 로 다시 보낸다. 401 은 백그라운드에서 세션 복원 전일 수 있다).
//   보내기 직전에 eventOccurredAt 이 30분을 넘었으면 버린다(서버가 버린다). 대기열은 최대 QUEUE_LIMIT 개, 넘치면 오래된 것부터 버린다.
// 시간 예산: 태스크 예산 15초에서 상태 읽기 · 쓰기 여유를 뺀 FLUSH_BUDGET_MS. 첫 보내기는 늘 하고,
//   다음 보내기는 경과 시간 + 보고 시간 제한(10초) ≤ 예산일 때만 시작한다. 못 보낸 것은 deferred 로 남는다.
// 시연 도구(설정)는 simulateArrival · getGeofenceSummary 를 쓴다. 가상 도착도 같은 대기열 · 같은 규칙으로 보낸다.
import {
    LOCATION_EVENT_TIMEOUT_MS,
    type LocationEventRequest,
    reportLocationEvent,
} from "../../shared/api/geofences";
import {
    type GeofenceState,
    loadGeofenceState,
    resetGeofenceState,
    saveGeofenceState,
} from "./geofenceState";
import type { PlaceFenceState, RegistrationPlan } from "./registrationPlan";

const QUEUE_LIMIT = 20;
const MAX_EVENT_AGE_MS = 30 * 60 * 1000;
const FLUSH_BUDGET_MS = 12_000;

export type GeofenceEventOutcome =
    | { kind: "reported"; flush: FlushResult }
    | { kind: "exited" }
    | { kind: "resyncNeeded" }
    | {
          kind: "ignored";
          reason: "alreadyInside" | "unknownIdentifier" | "resyncEnter";
      };

// sent: 보내서 지움. kept: 실패해 남김(이번 보내기를 멈춘 항목). dropped: 버림(4xx · 30분 지남). deferred: 예산 · 멈춤으로 못 보낸 나머지.
export type FlushResult = {
    sent: number;
    kept: number;
    dropped: number;
    deferred: number;
};

// 시연 도구용 요약. registeredAt 은 마지막 등록 시각(ISO-8601 UTC), 등록한 적이 없으면 null.
export type GeofenceSummary = {
    places: number;
    registeredAt: string | null;
    queued: number;
};

// via: 등록된 장소면 geofence(나감 → 들어옴), 아니면 direct(장소 상태는 그대로). result: 이 도착 보고 하나의 결과.
export type SimulatedArrival = {
    via: "geofence" | "direct";
    result: "sent" | "kept" | "dropped";
};

type CryptoModule = typeof import("expo-crypto");
let cryptoModule: CryptoModule | null | undefined;
let flushing: Promise<FlushResult> | undefined;
// 보내기에서 지운 항목의 결과(triggerEventId → sent · dropped). 가상 도착이 자기 결과를 찾고 지운다.
const settled = new Map<string, "sent" | "dropped">();

// 등록을 마친 뒤 부른다. 장소 상태 · 재조회 id · 마지막 등록 정보를 계획대로 바꾼다(목록에서 빠진 장소의 상태는 지워진다).
export async function applyRegistration(
    plan: RegistrationPlan,
    at: Date,
): Promise<void> {
    const current = await loadGeofenceState();
    current.places = plan.places;
    current.resyncId = plan.resyncId;
    current.registration = {
        at: at.toISOString(),
        center: plan.center,
        ids: plan.regions.map((region) => region.identifier),
    };
    saveGeofenceState(current);
}

// planRegistration 의 previous 로 넘긴다.
export async function getPlaceFenceStates(): Promise<
    Record<string, PlaceFenceState>
> {
    return { ...(await loadGeofenceState()).places };
}

export async function handleGeofenceEvent(
    event: { identifier: string; kind: "enter" | "exit" },
    now: Date,
): Promise<GeofenceEventOutcome> {
    const current = await loadGeofenceState();
    if (current.resyncId != null && event.identifier === current.resyncId) {
        return event.kind === "exit"
            ? { kind: "resyncNeeded" }
            : { kind: "ignored", reason: "resyncEnter" };
    }
    if (
        !Object.prototype.hasOwnProperty.call(current.places, event.identifier)
    ) {
        return { kind: "ignored", reason: "unknownIdentifier" };
    }
    const place = current.places[event.identifier];
    if (event.kind === "exit") {
        place.inside = false;
        saveGeofenceState(current);
        return { kind: "exited" };
    }
    if (place.inside) return { kind: "ignored", reason: "alreadyInside" };
    place.inside = true;
    const { flush } = await queueArrival(current, place.placeId, now);
    return { kind: "reported", flush };
}

export async function getGeofenceSummary(): Promise<GeofenceSummary> {
    const current = await loadGeofenceState();
    return {
        places: Object.keys(current.places).length,
        registeredAt: current.registration?.at ?? null,
        queued: current.queue.length,
    };
}

// 시연용 가상 도착. 등록된 장소면 handleGeofenceEvent 에 나감 → 들어옴을 넣어 실제 이벤트와 같은 길로 보고한다(반복해도 매번 보고).
// 등록 안 된 장소(권한 · 동의 · 모듈 문제)면 장소 상태는 건드리지 않고 도착 보고만 대기열에 넣고 보낸다.
export async function simulateArrival(
    placeId: string,
    now: Date,
): Promise<SimulatedArrival> {
    const current = await loadGeofenceState();
    const geofenceId = Object.keys(current.places).find(
        (id) => current.places[id].placeId === placeId,
    );
    if (geofenceId != null) {
        await handleGeofenceEvent(
            { identifier: geofenceId, kind: "exit" },
            now,
        );
        current.places[geofenceId].inside = true;
    }
    const { triggerEventId } = await queueArrival(current, placeId, now);
    const queued = (await loadGeofenceState()).queue.some(
        (item) => item.triggerEventId === triggerEventId,
    );
    const result = queued ? "kept" : (settled.get(triggerEventId) ?? "sent");
    settled.delete(triggerEventId);
    return { via: geofenceId != null ? "geofence" : "direct", result };
}

// 이미 보내는 중이면 새로 시작하지 않고 그 결과를 같이 받는다(그 사이 넣은 항목도 이어서 보낸다).
export function flushLocationEvents(
    clock: () => number = Date.now,
): Promise<FlushResult> {
    flushing ??= sendQueued(clock).finally(() => {
        flushing = undefined;
    });
    return flushing;
}

// 도착 보고 하나를 대기열에 넣고 저장한 뒤 보낸다.
async function queueArrival(
    current: GeofenceState,
    placeId: string,
    now: Date,
): Promise<{ triggerEventId: string; flush: FlushResult }> {
    const triggerEventId = newTriggerEventId();
    enqueue(current, {
        placeId,
        triggerEventId,
        eventType: "ENTER",
        eventOccurredAt: now.toISOString(),
    });
    saveGeofenceState(current);
    return { triggerEventId, flush: await flushLocationEvents() };
}

// 동의 철회 · 로그아웃 때 부른다. 장소 상태 · 등록 정보 · 대기열을 모두 지운다. 보내는 중이던 결과는 반영하지 않는다.
export async function clearGeofenceState(): Promise<void> {
    resetGeofenceState();
}

async function sendQueued(clock: () => number): Promise<FlushResult> {
    const current = await loadGeofenceState();
    const started = clock();
    const result: FlushResult = { sent: 0, kept: 0, dropped: 0, deferred: 0 };
    let attempts = 0;
    let keptId: string | null = null;
    while (current.queue.length > 0) {
        const item = current.queue[0];
        if (clock() - Date.parse(item.eventOccurredAt) > MAX_EVENT_AGE_MS) {
            removeFromQueue(current, item);
            settle(item, "dropped");
            result.dropped += 1;
            saveGeofenceState(current);
            continue;
        }
        if (
            attempts > 0 &&
            clock() - started + LOCATION_EVENT_TIMEOUT_MS > FLUSH_BUDGET_MS
        ) {
            break;
        }
        attempts += 1;
        const outcome = await reportLocationEvent(item);
        // 보내는 사이 지워졌으면(clearGeofenceState) 아무것도 바꾸지 않는다.
        if ((await loadGeofenceState()) !== current) return result;
        if (outcome.kind === "sent") {
            settle(item, "sent");
            result.sent += 1;
        } else if (outcome.kind === "rejected" && outcome.status !== 401) {
            settle(item, "dropped");
            result.dropped += 1;
        } else {
            result.kept += 1;
            keptId = item.triggerEventId;
            break;
        }
        removeFromQueue(current, item);
        saveGeofenceState(current);
    }
    // 남긴 항목은 보내는 사이 넘쳐 버려졌을 수 있어 대기열에 있을 때만 뺀다.
    const keptQueued = current.queue.some(
        (queued) => queued.triggerEventId === keptId,
    );
    result.deferred = current.queue.length - (keptQueued ? 1 : 0);
    return result;
}

// 가상 도착만 결과를 찾아 간다. 실제 이벤트의 결과는 쌓이지 않게 최근 QUEUE_LIMIT 개만 둔다.
function settle(item: LocationEventRequest, outcome: "sent" | "dropped"): void {
    settled.set(item.triggerEventId, outcome);
    if (settled.size > QUEUE_LIMIT) {
        const oldest = settled.keys().next().value;
        if (oldest != null) settled.delete(oldest);
    }
}

function enqueue(current: GeofenceState, event: LocationEventRequest): void {
    current.queue.push(event);
    if (current.queue.length > QUEUE_LIMIT) {
        current.queue.splice(0, current.queue.length - QUEUE_LIMIT);
    }
}

// 보내는 동안 넘쳐서 이미 버려졌을 수 있어 triggerEventId 로 찾는다.
function removeFromQueue(
    current: GeofenceState,
    item: LocationEventRequest,
): void {
    const index = current.queue.findIndex(
        (queued) => queued.triggerEventId === item.triggerEventId,
    );
    if (index >= 0) current.queue.splice(index, 1);
}

// 서버가 같은 이벤트의 재전송을 거르는 데만 쓴다(비밀 아님). expo-crypto 가 없으면 Math.random 으로 만든다.
function newTriggerEventId(): string {
    if (cryptoModule === undefined) {
        try {
            cryptoModule = require("expo-crypto") as CryptoModule;
        } catch {
            cryptoModule = null;
        }
    }
    if (cryptoModule != null) return cryptoModule.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
        const random = Math.floor(Math.random() * 16);
        const value = char === "x" ? random : (random % 4) + 8;
        return value.toString(16);
    });
}
