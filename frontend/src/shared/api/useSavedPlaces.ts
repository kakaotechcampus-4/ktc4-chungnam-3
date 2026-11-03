// 내 장소를 읽는 hook. 지도 탭(10)과 근처 탭이 쓴다.
// 내 장소 목록과 저장물 목록(PAGE_SIZE 씩 끝까지)을 함께 읽어 장소마다 가장 최근 저장물 하나로 묶는다(toSavedPlaces).
// 화면에 포커스될 때와, 포커스된 동안 앱이 다시 앞으로 올 때 읽는다.
// 처음 읽기가 실패하면 failed, 이미 읽은 뒤의 다시 읽기 실패는 보이던 목록을 그대로 둔다.
// 늦게 온 응답이 새 결과를 덮지 않게 마지막 읽기만 반영하고, 화면을 떠난 뒤에 온 응답은 버린다
// (지도 마커는 화면이 숨겨진 동안 바뀌면 어긋난다. STRUCTURE "컴포넌트 위치").
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import type { SavedPlace } from "../../domain/savedPlace";
import { toSavedPlaces } from "./mappers/savedPlaceMapper";
import {
    fetchPersonalSaves,
    fetchSavedPlaces,
    type PersonalSaveItemResponse,
} from "./personalSaves";

// 서버 목록 한 페이지 최대값.
const PAGE_SIZE = 100;

export type SavedPlacesView =
    | { status: "loading" }
    | { status: "failed" }
    | { status: "loaded"; places: SavedPlace[] };

export function useSavedPlaces(): { view: SavedPlacesView; reload(): void } {
    const [view, setView] = useState<SavedPlacesView>({ status: "loading" });
    const current = useRef(view);
    current.current = view;
    const readId = useRef(0);

    const reload = useCallback(() => {
        const id = ++readId.current;
        if (current.current.status === "failed") {
            setView({ status: "loading" });
        }
        loadSavedPlaces()
            .then((places) => {
                if (id !== readId.current) return;
                setView({ status: "loaded", places });
            })
            .catch(() => {
                if (id !== readId.current) return;
                if (current.current.status !== "loaded") {
                    setView({ status: "failed" });
                }
            });
    }, []);

    useFocusEffect(
        useCallback(() => {
            reload();
            const subscription = AppState.addEventListener(
                "change",
                (state) => {
                    if (state === "active") reload();
                },
            );
            return () => {
                subscription.remove();
                readId.current += 1;
            };
        }, [reload]),
    );

    return { view, reload };
}

export async function loadSavedPlaces(): Promise<SavedPlace[]> {
    const [pins, items] = await Promise.all([
        fetchSavedPlaces(),
        fetchAllPersonalSaves(),
    ]);
    return toSavedPlaces(pins, items);
}

// nextCursor 가 null 일 때까지 읽는다. 같은 커서가 다시 오면(서버 이상) 거기서 멈춘다.
async function fetchAllPersonalSaves(): Promise<PersonalSaveItemResponse[]> {
    const items: PersonalSaveItemResponse[] = [];
    const seen = new Set<string>();
    let cursor: string | undefined;
    for (;;) {
        const page = await fetchPersonalSaves({ size: PAGE_SIZE, cursor });
        items.push(...page.items);
        if (page.nextCursor == null || seen.has(page.nextCursor)) {
            return items;
        }
        seen.add(page.nextCursor);
        cursor = page.nextCursor;
    }
}
