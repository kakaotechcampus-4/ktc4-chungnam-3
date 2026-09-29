// 설정(11). 알림 · 위치 · 앱 정보. 하단 바가 없는 스택 화면이라 상태바 · 제스처 inset 을 직접 처리한다.
// 조용한 시간 · 오픈소스 라이선스는 다음 화면이 없어 아직 동작하지 않는다.
import { useNavigation } from "@react-navigation/native";
import Constants from "expo-constants";
import { useState } from "react";
import {
    Linking,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    MOCK_SCENARIO,
    type MockLocationPermission,
    settingsMock,
} from "../../shared/api/mock";
import Icon from "../../shared/ui/Icon";
import {
    colors,
    metrics,
    size,
    spacing,
    typography,
} from "../../shared/ui/theme";
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

    return (
        <View style={styles.screen}>
            <View style={[styles.topBar, { marginTop: insets.top }]}>
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="뒤로"
                    hitSlop={metrics.settings.backHitSlop}
                    onPress={() => navigation.goBack()}
                >
                    <Icon name="arrowLeft" size={size.iconMd} />
                </Pressable>
                <Text style={styles.title}>설정</Text>
            </View>

            <ScrollView
                contentContainerStyle={{
                    paddingBottom: insets.bottom + spacing.lg,
                }}
            >
                <SettingsGroup title="알림">
                    <SettingsRow
                        title="근처에 오면 알려주기"
                        description="저장한 곳 가까이 가면 알려요"
                        switchValue={nearbyAlerts}
                        onPress={() => setNearbyAlerts((on) => !on)}
                    />
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
                            PERMISSION_LABEL[MOCK_SCENARIO.locationPermission]
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
    topBar: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.sm,
        padding: spacing.md,
    },
    title: {
        ...typography.titleCard,
        color: colors.text.primary,
    },
});
