// 00d 위치 1단계 2175:808. "위치 허용하기" → 00e, "나중에 할게요" → 00f.
// '앱 사용 중' 권한 없이는 '항상 허용'을 받을 수 없어 미루거나 거절하면 00e 를 건너뛴다.
// 허용하기는 앱 사용 중 위치를 요청한다. 허용(이번만 포함) → 00e, 거절 → 00f.
// 위치 정보 이용 동의(00d-c 2202:843): 동의하지 않았으면 허용하기가 먼저 동의 시트를 띄운다.
// "동의하고 계속"으로 동의를 기록한 뒤에 권한을 요청한다. 동의 기록은 권한 결과와 무관하다.
import { StackActions, useNavigation } from "@react-navigation/native";
import { useEffect, useRef, useState } from "react";

import type { ConsentState } from "../../domain/consent";
import { fetchConsents, setLocationConsent } from "../../shared/api/consents";
import { toConsentState } from "../../shared/api/mappers/consentMapper";
import { requestForegroundLocation } from "../../shared/permissions/location";
import Button from "../../shared/ui/Button";
import LocationConsentSheet, {
    type LocationConsentSheetStatus,
} from "../../shared/ui/LocationConsentSheet";
import { metrics } from "../../shared/ui/theme";
import OnboardingLayout, {
    OnboardingActions,
    OnboardingList,
} from "./components/OnboardingLayout";
import PermissionStep from "./components/PermissionStep";

export default function LocationPermissionScreen() {
    const navigation = useNavigation();
    // 요청 중에는 다시 누르지 못하게 한다.
    const pending = useRef(false);
    const saving = useRef(false);
    // 위치 동의 상태. 화면이 열릴 때 미리 읽는다. 아직 못 읽었으면 null(시트를 띄운다).
    const consent = useRef<ConsentState | null>(null);
    const [sheetVisible, setSheetVisible] = useState(false);
    const [sheetStatus, setSheetStatus] =
        useState<LocationConsentSheetStatus>("default");

    useEffect(() => {
        // 읽지 못하면 동의하지 않은 것으로 본다.
        fetchConsents()
            .then((consents) => {
                consent.current = toConsentState(
                    consents,
                    "LOCATION_BASED_SERVICE",
                );
            })
            .catch(() => {
                consent.current = "never";
            });
    }, []);

    const requestPermission = async () => {
        if (pending.current) return;
        pending.current = true;
        // 요청이 실패하면 거절로 보고 00f 로 간다.
        const result = await requestForegroundLocation().catch(
            () => "denied" as const,
        );
        navigation.dispatch(
            StackActions.replace(
                result === "denied" ? "FirstSave" : "BackgroundLocation",
            ),
        );
    };

    const allow = () => {
        if (consent.current === "agreed") {
            requestPermission();
            return;
        }
        setSheetStatus("default");
        setSheetVisible(true);
    };

    // 실패하면(목 Error · ApiError 모두) 시트를 그대로 두고 오류를 보인다. 다시 누르면 다시 저장한다.
    const agree = async () => {
        if (saving.current) return;
        saving.current = true;
        setSheetStatus("saving");
        try {
            await setLocationConsent(true);
        } catch {
            setSheetStatus("error");
            return;
        } finally {
            saving.current = false;
        }
        consent.current = "agreed";
        setSheetVisible(false);
        requestPermission();
    };

    const later = () => navigation.dispatch(StackActions.replace("FirstSave"));

    // 시트가 닫히면(바깥 닫힘 포함) 00d 에 머문다.
    const closeSheet = () => {
        setSheetVisible(false);
        setSheetStatus("default");
    };

    return (
        <>
            <OnboardingLayout
                messageTop={metrics.onboarding.messageTop}
                heading="근처인지 알려면 위치가 필요해요"
                body="위치는 저장한 곳 근처에 왔는지 확인할 때만 써요. 두 단계로 허용해요."
                footer={
                    <OnboardingActions>
                        <Button
                            kind="primary"
                            label="위치 허용하기"
                            onPress={allow}
                        />
                        <Button
                            kind="text"
                            label="나중에 할게요"
                            onPress={later}
                        />
                    </OnboardingActions>
                }
            >
                <OnboardingList>
                    <PermissionStep
                        step={1}
                        title="앱 사용 중에만 허용"
                        caption="곧 뜨는 창에서 이걸 골라주세요"
                    />
                    <PermissionStep
                        step={2}
                        title="설정에서 항상 허용으로 바꾸기"
                        caption="앱을 닫아도 근처에 왔는지 알 수 있어요"
                    />
                </OnboardingList>
            </OnboardingLayout>
            <LocationConsentSheet
                visible={sheetVisible}
                status={sheetStatus}
                onAgree={agree}
                onLater={later}
                onDismiss={closeSheet}
            />
        </>
    );
}
