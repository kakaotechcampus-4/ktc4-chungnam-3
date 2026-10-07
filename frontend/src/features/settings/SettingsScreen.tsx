// 설정(11). 알림 · 위치 · 앱 정보. 하단 바가 없는 스택 화면이라 상태바 · 제스처 inset 을 직접 처리한다.
// 조용한 시간 · 오픈소스 라이선스는 다음 화면이 없어 아직 동작하지 않는다.
// 기기 권한(알림 · 위치)은 실제 상태를 읽고, 앱이 앞으로 돌아올 때 다시 읽는다.
// 권한 모듈이 없는 개발 빌드에서는 위치를 목 값으로 보이고 알림 상태는 확인하지 않는다.
import { useNavigation } from "@react-navigation/native";
import Constants from "expo-constants";
import { useState } from "react";
import { Linking, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    MOCK_SCENARIO,
    type MockLocationPermission,
    settingsMock,
} from "../../shared/api/mock";
import { openNotificationSettings } from "../../shared/permissions/notifications";
import { usePermissionStatus } from "../../shared/permissions/usePermissionStatus";
import { spacing } from "../../shared/ui/theme";
import TopBar from "../../shared/ui/TopBar";
import SettingsGroup from "./components/SettingsGroup";
import SettingsRow from "./components/SettingsRow";

// 안드로이드 OS 권한 화면 표현.
const PERMISSION_LABEL: Record<MockLocationPermission, string> = {
    always: "항상 허용",
    whileInUse: "앱 사용 중에만 허용",
    denied: "허용 안 함",
};

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

    return (
        <View style={styles.screen}>
            <TopBar title="설정" onIconPress={() => navigation.goBack()} />

            <ScrollView
                contentContainerStyle={{
                    paddingBottom: insets.bottom + spacing.lg,
                }}
            >
                <SettingsGroup title="알림">
                    {notificationsOff ? (
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
                    note="근처 알림은 위치 권한이 ‘항상 허용’일 때만 동작해요."
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
                </SettingsGroup>

                <SettingsGroup title="앱 정보">
                    <SettingsRow title="버전" value={version} />
                    <SettingsRow title="오픈소스 라이선스" chevron disabled />
                </SettingsGroup>
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
});
