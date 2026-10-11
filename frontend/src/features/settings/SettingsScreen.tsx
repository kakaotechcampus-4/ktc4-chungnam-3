// 설정(11). 알림 · 위치 · 앱 정보. 하단 바가 없는 스택 화면이라 상태바 · 제스처 inset 을 직접 처리한다.
// 조용한 시간 · 오픈소스 라이선스는 다음 화면이 없어 아직 동작하지 않는다. 약관 및 정책은 페이지 URL 이 생기기 전까지 같다.
// 기기 권한(알림 · 위치)은 실제 상태를 읽고, 앱이 앞으로 돌아올 때 다시 읽는다.
// 권한 모듈이 없는 개발 빌드에서는 위치를 목 값으로 보이고 알림 상태는 확인하지 않는다.
// 위치 정보 이용 동의(11c 2202:915 · 11c-off 2202:986)는 화면에 포커스될 때 서버에서 읽는다.
// 동의 안 함이면 동의 시트(LocationConsentSheet), 동의함이면 거두기 시트(11d)를 띄운다. 저장이 성공하면 PUT 응답으로 바로 갱신한다.
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import Constants from "expo-constants";
import { useCallback, useRef, useState } from "react";
import { Linking, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    type ConsentResponse,
    type ConsentType,
    fetchConsents,
    setLocationConsent,
} from "../../shared/api/consents";
import { TERMS_URLS } from "../../shared/api/consentTerms";
import {
    toConsentAgreedAt,
    toConsentState,
} from "../../shared/api/mappers/consentMapper";
import {
    MOCK_SCENARIO,
    type MockLocationPermission,
    settingsMock,
} from "../../shared/api/mock";
import { openExternalUrl } from "../../shared/external-links/openExternalUrl";
import { openNotificationSettings } from "../../shared/permissions/notifications";
import { usePermissionStatus } from "../../shared/permissions/usePermissionStatus";
import LocationConsentSheet from "../../shared/ui/LocationConsentSheet";
import type { ModalSheetStatus } from "../../shared/ui/ModalSheet";
import { spacing } from "../../shared/ui/theme";
import TopBar from "../../shared/ui/TopBar";
import SettingsGroup from "./components/SettingsGroup";
import SettingsRow from "./components/SettingsRow";
import WithdrawConsentSheet from "./components/WithdrawConsentSheet";

// 안드로이드 OS 권한 화면 표현.
const PERMISSION_LABEL: Record<MockLocationPermission, string> = {
    always: "항상 허용",
    whileInUse: "앱 사용 중에만 허용",
    denied: "허용 안 함",
};

const LOCATION: ConsentType = "LOCATION_BASED_SERVICE";

// notAgreed 는 한 번도 동의하지 않음 · 거둠을 함께 쓴다.
type LocationConsent =
    | { status: "loading" }
    | { status: "agreed"; agreedAt: Date }
    | { status: "notAgreed" }
    | { status: "failed" };

function toLocationConsent(
    consents: readonly ConsentResponse[],
): LocationConsent {
    const agreedAt = toConsentAgreedAt(consents, LOCATION);
    return toConsentState(consents, LOCATION) === "agreed" && agreedAt != null
        ? { status: "agreed", agreedAt }
        : { status: "notAgreed" };
}

// 동의 행 설명. 날짜는 기기 시간대.
function consentDescription(consent: LocationConsent): string {
    switch (consent.status) {
        case "loading":
            return "확인하고 있어요";
        case "agreed":
            return `${consent.agreedAt.getMonth() + 1}월 ${consent.agreedAt.getDate()}일에 동의했어요`;
        case "notAgreed":
            return "동의하지 않았어요";
        case "failed":
            return "확인하지 못했어요. 눌러서 다시 불러와요";
    }
}

// 동의를 저장하는 시트 하나의 열림 · 상태. 성공하면 응답을 넘기고 닫는다. 어떤 예외든 error 로 시트를 유지한다.
function useConsentSheet(
    save: () => Promise<ConsentResponse>,
    onSaved: (response: ConsentResponse) => void,
) {
    const [visible, setVisible] = useState(false);
    const [status, setStatus] = useState<ModalSheetStatus>("default");
    const saving = useRef(false);

    const open = () => {
        setStatus("default");
        setVisible(true);
    };
    const close = () => setVisible(false);
    const onDismiss = () => {
        setVisible(false);
        setStatus("default");
    };
    const submit = async () => {
        if (saving.current) return;
        saving.current = true;
        setStatus("saving");
        const response = await save().catch(() => null);
        saving.current = false;
        if (response == null) {
            setStatus("error");
            return;
        }
        onSaved(response);
        setVisible(false);
    };
    return { visible, status, open, close, onDismiss, submit };
}

export default function SettingsScreen() {
    const navigation = useNavigation();
    const insets = useSafeAreaInsets();
    const [nearbyAlerts, setNearbyAlerts] = useState<boolean>(
        settingsMock.nearbyAlerts,
    );
    const version = Constants.expoConfig?.version;
    const permissions = usePermissionStatus();
    // 11b: 기기 알림이 꺼져 있으면(허용 전 포함) 스위치 대신 > 를 보이고 누르면 시스템 알림 설정을 연다.
    const notificationsOff =
        permissions.notifications != null &&
        permissions.notifications !== "granted" &&
        permissions.notifications !== "unavailable";
    const location =
        permissions.location === "unavailable"
            ? MOCK_SCENARIO.locationPermission
            : permissions.location;

    const [consent, setConsent] = useState<LocationConsent>({
        status: "loading",
    });
    // 늦게 온 응답이 새 결과를 덮지 않게 마지막 요청만 반영한다.
    const readId = useRef(0);
    const readConsent = useCallback(() => {
        const id = ++readId.current;
        setConsent({ status: "loading" });
        fetchConsents()
            .then((consents) => {
                if (id === readId.current)
                    setConsent(toLocationConsent(consents));
            })
            .catch(() => {
                if (id === readId.current) setConsent({ status: "failed" });
            });
    }, []);
    useFocusEffect(readConsent);

    const applySaved = (response: ConsentResponse) => {
        readId.current++;
        setConsent(toLocationConsent([response]));
    };
    const agreeSheet = useConsentSheet(
        () => setLocationConsent(true),
        applySaved,
    );
    const withdrawSheet = useConsentSheet(
        () => setLocationConsent(false),
        applySaved,
    );

    const openConsent =
        consent.status === "agreed"
            ? withdrawSheet.open
            : consent.status === "notAgreed"
              ? agreeSheet.open
              : consent.status === "failed"
                ? readConsent
                : undefined;
    const policiesUrl = TERMS_URLS.policies;

    return (
        <View style={styles.screen}>
            <TopBar title="설정" onIconPress={() => navigation.goBack()} />

            <ScrollView
                contentContainerStyle={{
                    paddingBottom: insets.bottom + spacing.lg,
                }}
            >
                <SettingsGroup title="알림">
                    {consent.status === "notAgreed" ? (
                        <SettingsRow
                            title="근처에 오면 알려주기"
                            description="위치 정보 이용 동의가 필요해요. 눌러서 켜주세요"
                            chevron
                            onPress={agreeSheet.open}
                        />
                    ) : notificationsOff ? (
                        <SettingsRow
                            title="근처에 오면 알려주기"
                            description="기기 알림이 꺼져 있어요. 눌러서 켜주세요"
                            descriptionTone="danger"
                            chevron
                            onPress={openNotificationSettings}
                        />
                    ) : (
                        <SettingsRow
                            title="근처에 오면 알려주기"
                            description="저장한 곳 가까이 가면 알려요"
                            switchValue={nearbyAlerts}
                            onPress={() => setNearbyAlerts((on) => !on)}
                        />
                    )}
                    <SettingsRow
                        title="조용한 시간"
                        description="이 시간에는 알리지 않아요"
                        value={settingsMock.quietHours}
                        chevron
                        disabled
                    />
                </SettingsGroup>

                <SettingsGroup
                    title="위치"
                    note={
                        "근처 알림은 위치 권한이 ‘항상 허용’이고\n위치 정보 이용에 동의했을 때만 동작해요."
                    }
                >
                    <SettingsRow
                        title="위치 권한"
                        value={
                            location == null
                                ? undefined
                                : PERMISSION_LABEL[location]
                        }
                        chevron
                        onPress={() => Linking.openSettings()}
                    />
                    <SettingsRow
                        title="위치 정보 이용 동의"
                        description={consentDescription(consent)}
                        chevron
                        onPress={openConsent}
                    />
                </SettingsGroup>

                <SettingsGroup title="앱 정보">
                    <SettingsRow title="버전" value={version} />
                    <SettingsRow
                        title="약관 및 정책"
                        chevron
                        disabled={policiesUrl == null}
                        onPress={() => openExternalUrl(policiesUrl)}
                    />
                    <SettingsRow title="오픈소스 라이선스" chevron disabled />
                </SettingsGroup>
            </ScrollView>

            {/* 설정에서는 동의만 기록한다. 권한은 위치 권한 행이 따로 맡는다. */}
            <LocationConsentSheet
                visible={agreeSheet.visible}
                status={agreeSheet.status}
                onAgree={agreeSheet.submit}
                onLater={agreeSheet.close}
                onDismiss={agreeSheet.onDismiss}
            />
            <WithdrawConsentSheet
                visible={withdrawSheet.visible}
                status={withdrawSheet.status}
                onConfirm={withdrawSheet.submit}
                onCancel={withdrawSheet.close}
                onDismiss={withdrawSheet.onDismiss}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
});
