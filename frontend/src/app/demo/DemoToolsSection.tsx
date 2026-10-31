// 설정의 "시연 도구" 묶음. 개발 모드 · preview 빌드에서만 App 이 등록한다(shared/demo/demoTools). 기존 설정 줄만 쓴다.
// 지오펜스 등록 상태 · 보낼 도착 수 · "지금 다시 등록" · 저장한 장소별 가상 도착 · (목 모드) 목 서버가 받은 도착 기록.
// 가상 도착은 누르면 바로 보낸다(RN Android 는 앱이 백그라운드면 JS 타이머가 멈춰 예약하지 않는다). 보낸 뒤 홈으로 내리고 알림을 기다린다.
// 장소 목록은 묶음이 처음 그려질 때 한 번 읽는다. 목록(최대 100개) 중 장소가 있는 저장물만 상세를 하나씩 읽는다(시연용 N+1).
import { useCallback, useEffect, useState } from "react";

import SettingsGroup from "../../features/settings/components/SettingsGroup";
import SettingsRow from "../../features/settings/components/SettingsRow";
import {
    type GeofenceSummary,
    getGeofenceSummary,
    simulateArrival,
    type SimulatedArrival,
} from "../../runtime/geofence/geofenceManager";
import {
    type GeofenceSyncResult,
    getLastGeofenceSyncResult,
    syncGeofences,
} from "../../runtime/geofence/geofenceSync";
import { isApiConfigured } from "../../shared/api/config";
import { getMockLocationEvents } from "../../shared/api/geofencesMock";
import { toSaveStateFromItem } from "../../shared/api/mappers/personalSaveMapper";
import {
    fetchPersonalSave,
    fetchPersonalSaves,
} from "../../shared/api/personalSaves";

const LIST_SIZE = 100;

type DemoPlace = { placeId: string; name: string; saveTitle: string | null };

type Places =
    | { status: "loading" }
    | { status: "failed" }
    | { status: "ready"; places: DemoPlace[] };

const FAILED_STEP: Record<
    Extract<GeofenceSyncResult, { kind: "failed" }>["step"],
    string
> = {
    permission: "권한 확인",
    consent: "동의 확인",
    position: "위치",
    fetch: "목록 받기",
    start: "등록",
};

// 등록이 없거나 다시 등록한 결과를 짧게 쓴다.
export function syncResultLabel(result: GeofenceSyncResult | null): string {
    if (result == null) return "아직 등록하지 않았어요";
    switch (result.kind) {
        case "registered":
            return result.places === 0
                ? "저장한 장소가 없어요"
                : `${result.places}곳 등록했어요`;
        case "cleared":
            return result.because === "signedOut"
                ? "로그인하지 않았어요"
                : result.because === "permission"
                  ? "위치 권한이 ‘항상 허용’이 아니에요"
                  : "위치 정보 이용 동의가 없어요";
        case "skipped":
            return "지오펜스 모듈이 없는 빌드예요";
        case "failed":
            return `${FAILED_STEP[result.step]}에 실패했어요`;
    }
}

export function arrivalLabel(arrival: SimulatedArrival): string {
    const where = arrival.via === "direct" ? " · 등록 안 된 곳" : "";
    switch (arrival.result) {
        case "sent":
            return `보냈어요 · 알림을 기다려요${where}`;
        case "kept":
            return `보내지 못해 남겼어요. 다음에 다시 보내요${where}`;
        case "dropped":
            return `서버가 받지 않아 버렸어요${where}`;
    }
}

function clock(iso: string, withSeconds: boolean): string {
    const date = new Date(iso);
    const parts = [date.getHours(), date.getMinutes()];
    if (withSeconds) parts.push(date.getSeconds());
    return parts.map((part) => String(part).padStart(2, "0")).join(":");
}

async function readPlaces(): Promise<DemoPlace[]> {
    const { items } = await fetchPersonalSaves({ size: LIST_SIZE });
    const places: DemoPlace[] = [];
    for (const item of items) {
        if (toSaveStateFromItem(item).kind !== "ready") continue;
        const detail = await fetchPersonalSave(item.personalSaveId);
        for (const place of detail.places) {
            places.push({
                placeId: place.placeId,
                name: place.name,
                saveTitle: detail.title,
            });
        }
    }
    return places;
}

export default function DemoToolsSection() {
    const [summary, setSummary] = useState<GeofenceSummary | null>(null);
    const [syncLabel, setSyncLabel] = useState<string>();
    const [syncing, setSyncing] = useState(false);
    const [places, setPlaces] = useState<Places>({ status: "loading" });
    const [arrivals, setArrivals] = useState<Record<string, string>>({});
    const [mockEvents, setMockEvents] = useState(getMockLocationEvents());

    const refresh = useCallback(() => {
        getGeofenceSummary().then(setSummary);
        if (!isApiConfigured) setMockEvents([...getMockLocationEvents()]);
    }, []);

    const loadPlaces = useCallback(() => {
        setPlaces({ status: "loading" });
        readPlaces().then(
            (list) => setPlaces({ status: "ready", places: list }),
            () => setPlaces({ status: "failed" }),
        );
    }, []);

    useEffect(() => {
        refresh();
        loadPlaces();
    }, [refresh, loadPlaces]);

    const resync = () => {
        if (syncing) return;
        setSyncing(true);
        setSyncLabel("등록하고 있어요");
        syncGeofences("settingsChanged")
            .then((result) => setSyncLabel(syncResultLabel(result)))
            .finally(() => {
                setSyncing(false);
                refresh();
            });
    };

    const arrive = (place: DemoPlace) => {
        setArrivals((prev) => ({ ...prev, [place.placeId]: "보내고 있어요" }));
        simulateArrival(place.placeId, new Date())
            .then((arrival) =>
                setArrivals((prev) => ({
                    ...prev,
                    [place.placeId]: arrivalLabel(arrival),
                })),
            )
            .finally(refresh);
    };

    const registered =
        summary == null
            ? "확인하고 있어요"
            : summary.places > 0 && summary.registeredAt != null
              ? `등록된 곳 ${summary.places}곳 · ${clock(summary.registeredAt, false)}`
              : `등록된 곳 없음 · ${syncResultLabel(getLastGeofenceSyncResult())}`;
    const placeNames = new Map(
        places.status === "ready"
            ? places.places.map((place) => [place.placeId, place.name])
            : [],
    );

    return (
        <SettingsGroup title="시연 도구" note="발표용 빌드에서만 보여요.">
            <SettingsRow title="지오펜스" description={registered} />
            <SettingsRow
                title="보낼 도착"
                value={summary == null ? undefined : `${summary.queued}건`}
            />
            <SettingsRow
                title="지금 다시 등록"
                description={syncLabel}
                chevron
                disabled={syncing}
                onPress={resync}
            />
            {places.status === "loading" && (
                <SettingsRow title="저장한 장소를 불러오고 있어요" />
            )}
            {places.status === "failed" && (
                <SettingsRow
                    title="저장한 장소를 불러오지 못했어요"
                    description="눌러서 다시 불러와요"
                    onPress={loadPlaces}
                />
            )}
            {places.status === "ready" && places.places.length === 0 && (
                <SettingsRow title="장소가 있는 저장물이 없어요" />
            )}
            {places.status === "ready" &&
                places.places.map((place) => (
                    <SettingsRow
                        key={place.placeId}
                        title={`${place.name}에 도착`}
                        description={
                            arrivals[place.placeId] ??
                            (place.saveTitle == null
                                ? undefined
                                : `“${place.saveTitle}”`)
                        }
                        chevron
                        onPress={() => arrive(place)}
                    />
                ))}
            {!isApiConfigured &&
                (mockEvents.length === 0 ? (
                    <SettingsRow title="목 서버가 받은 도착 없음" />
                ) : (
                    [...mockEvents]
                        .reverse()
                        .map((event) => (
                            <SettingsRow
                                key={event.triggerEventId}
                                title={`${clock(event.eventOccurredAt, true)} · ${placeNames.get(event.placeId) ?? event.placeId}`}
                                description="목 서버가 받은 도착"
                            />
                        ))
                ))}
        </SettingsGroup>
    );
}
