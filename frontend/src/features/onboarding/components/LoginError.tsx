// 로그인 실패 문구(00b-err 2175:754). 통신 · 서버 오류일 때만 버튼 위에 보인다.
import { StyleSheet, Text, View } from "react-native";

import { colors, metrics, radius, typography } from "../../../shared/ui/theme";

export default function LoginError({ message }: { message: string }) {
    return (
        <View style={styles.box} accessibilityLiveRegion="polite">
            <Text style={styles.text}>{message}</Text>
        </View>
    );
}

const styles = StyleSheet.create({
    box: {
        paddingHorizontal: metrics.onboarding.errorPaddingHorizontal,
        paddingVertical: metrics.onboarding.errorPaddingVertical,
        borderRadius: radius.md,
        backgroundColor: colors.status.dangerBg,
    },
    text: {
        ...typography.captionMeta,
        color: colors.status.dangerFg,
    },
});
