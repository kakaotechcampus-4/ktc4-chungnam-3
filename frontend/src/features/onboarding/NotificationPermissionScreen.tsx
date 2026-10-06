// 00c 알림 권한 2175:764. 알림 미리보기 · "알림 허용하기" / "나중에 할게요" → 00d.
// 허용하기는 알림 권한을 요청하고(안드로이드 13+ 시스템 창) 결과와 관계없이 00d 로 간다.
// 권한 단계는 화면을 교체한다. 시스템 뒤로 가기는 앱을 닫는다.
import { StackActions, useNavigation } from "@react-navigation/native";
import { useRef } from "react";
import { StyleSheet, View } from "react-native";

import { onboardingMock } from "../../shared/api/mock";
import { requestNotificationPermission } from "../../shared/permissions/notifications";
import Button from "../../shared/ui/Button";
import { metrics, spacing } from "../../shared/ui/theme";
import NotificationPreview from "./components/NotificationPreview";
import OnboardingLayout, {
    OnboardingActions,
} from "./components/OnboardingLayout";

export default function NotificationPermissionScreen() {
    const navigation = useNavigation();
    // 요청 중에는 다시 누르지 못하게 한다.
    const pending = useRef(false);
    const next = () =>
        navigation.dispatch(StackActions.replace("LocationPermission"));
    const allow = async () => {
        if (pending.current) return;
        pending.current = true;
        try {
            await requestNotificationPermission();
        } finally {
            next();
        }
    };

    return (
        <OnboardingLayout
            header={
                <View style={styles.preview}>
                    <NotificationPreview
                        {...onboardingMock.notificationPreview}
                    />
                </View>
            }
            messageTop={metrics.onboarding.previewMessageTop}
            heading="근처에 가면 알려드릴게요"
            body="알림을 허용해야 저장한 곳 가까이 갔을 때 알려드릴 수 있어요. 자주 울리지 않게 조용히 보낼게요."
            footer={
                <OnboardingActions>
                    <Button
                        kind="primary"
                        label="알림 허용하기"
                        onPress={allow}
                    />
                    <Button kind="text" label="나중에 할게요" onPress={next} />
                </OnboardingActions>
            }
        />
    );
}

const styles = StyleSheet.create({
    preview: {
        paddingTop: spacing.xxl,
        paddingHorizontal: spacing.lg,
    },
});
