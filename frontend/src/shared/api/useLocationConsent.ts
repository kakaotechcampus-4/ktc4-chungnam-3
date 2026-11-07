// 위치 정보 이용 동의를 읽는 hook. 근처 탭(06b 게이트 · 동의 시트)이 쓴다.
// 화면에 포커스될 때, 포커스된 동안 앱이 다시 앞으로 올 때, 위치 설정 신호(동의 저장 · 권한 요청 뒤)가 올 때 읽는다.
// 상태는 consentMapper 의 toConsentState(never · agreed · outdated · withdrawn), 동의할 때 보낼 서버 현재 약관 버전을 함께 둔다.
// 처음 읽기가 실패하면 failed, 이미 읽은 뒤의 다시 읽기 실패는 읽어 둔 값을 그대로 둔다.
// 마지막 읽기만 반영하고, 화면을 떠난 뒤에 온 결과는 버린다.
import { useFocusEffect } from "@react-navigation/native";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import type { ConsentState } from "../../domain/consent";
import { onLocationSettingsChanged } from "../permissions/locationSettings";
import { fetchConsents } from "./consents";
import { toConsentState, toCurrentTermsVersion } from "./mappers/consentMapper";

export type LocationConsentView =
    | { status: "loading" }
    | { status: "failed" }
    | { status: "loaded"; state: ConsentState; termsVersion: string | null };

export function useLocationConsent(): LocationConsentView {
    const [view, setView] = useState<LocationConsentView>({
        status: "loading",
    });
    const current = useRef(view);
    current.current = view;
    const readId = useRef(0);

    const read = useCallback(() => {
        const id = ++readId.current;
        fetchConsents()
            .then((consents) => {
                if (id !== readId.current) return;
                setView({
                    status: "loaded",
                    state: toConsentState(consents, "LOCATION_BASED_SERVICE"),
                    termsVersion: toCurrentTermsVersion(
                        consents,
                        "LOCATION_BASED_SERVICE",
                    ),
                });
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
            read();
            const subscription = AppState.addEventListener(
                "change",
                (state) => {
                    if (state === "active") read();
                },
            );
            const stopSettings = onLocationSettingsChanged(read);
            return () => {
                subscription.remove();
                stopSettings();
                readId.current += 1;
            };
        }, [read]),
    );

    return view;
}
