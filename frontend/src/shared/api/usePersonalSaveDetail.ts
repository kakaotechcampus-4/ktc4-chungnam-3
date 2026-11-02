// 저장물 상세를 읽는 hook. 저장 결과(02~05)와 저장물로 연 장소 상세(08)가 쓴다.
// 화면에 포커스될 때와, 포커스된 동안 앱이 다시 앞으로 올 때 읽는다.
// pollWhileAnalyzing 이면 분석 중인 동안 POLL_MS 마다 다시 읽고, 분석이 끝나면 멈춘다(완료 통지가 없다).
// 404 는 notFound. 처음 읽기가 실패하면 failed, 이미 읽은 뒤의 다시 읽기 실패는 보이던 상세를 그대로 둔다.
// 늦게 온 응답이 새 결과를 덮지 않게 마지막 읽기만 반영한다.
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import type { SaveState } from "../../domain/saveState";
import { ApiError } from "./client";
import { toSaveState } from "./mappers/personalSaveMapper";
import {
    fetchPersonalSave,
    type PersonalSaveDetailResponse,
} from "./personalSaves";

const POLL_MS = 5_000;

export type PersonalSaveDetailView =
    | { status: "loading" }
    | { status: "notFound" }
    | { status: "failed" }
    | {
          status: "loaded";
          detail: PersonalSaveDetailResponse;
          state: SaveState;
      };

export function usePersonalSaveDetail(
    personalSaveId: string,
    { pollWhileAnalyzing }: { pollWhileAnalyzing: boolean },
): { view: PersonalSaveDetailView; reload(): void } {
    const [view, setView] = useState<PersonalSaveDetailView>({
        status: "loading",
    });
    const current = useRef(view);
    current.current = view;
    const readId = useRef(0);

    const reload = useCallback(() => {
        const id = ++readId.current;
        if (current.current.status === "failed") {
            setView({ status: "loading" });
        }
        fetchPersonalSave(personalSaveId)
            .then((detail) => {
                if (id !== readId.current) return;
                setView({
                    status: "loaded",
                    detail,
                    state: toSaveState(detail),
                });
            })
            .catch((error) => {
                if (id !== readId.current) return;
                if (error instanceof ApiError && error.status === 404) {
                    setView({ status: "notFound" });
                } else if (current.current.status !== "loaded") {
                    setView({ status: "failed" });
                }
            });
    }, [personalSaveId]);

    useFocusEffect(
        useCallback(() => {
            reload();
            const subscription = AppState.addEventListener(
                "change",
                (state) => {
                    if (state === "active") reload();
                },
            );
            return () => subscription.remove();
        }, [reload]),
    );

    const polling =
        pollWhileAnalyzing &&
        view.status === "loaded" &&
        view.state.kind === "analyzing";
    useFocusEffect(
        useCallback(() => {
            if (!polling) return;
            const timer = setInterval(reload, POLL_MS);
            return () => clearInterval(timer);
        }, [polling, reload]),
    );

    return { view, reload };
}
