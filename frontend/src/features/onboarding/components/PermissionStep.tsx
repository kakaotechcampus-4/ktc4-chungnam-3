// 위치 권한 단계 행(00d 2175:819 · 00e 2175:860). 번호 원 28, 끝난 단계는 text/primary 원 + 체크.
// 테두리는 크기에 포함하지 않는다. padding 에서 테두리 두께를 뺀다.
import { StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import {
    colors,
    metrics,
    radius,
    spacing,
    stroke,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    step: number;
    done?: boolean;
    title: string;
    caption: string;
};

export default function PermissionStep({
    step,
    done = false,
    title,
    caption,
}: Props) {
    return (
        <View style={styles.row}>
            <View
                style={[
                    styles.badge,
                    done ? styles.badgeDone : styles.badgeTodo,
                ]}
            >
                {done ? (
                    <Icon
                        name="check"
                        size={metrics.onboarding.stepCheckSize}
                        color={colors.icon.onPrimary}
                    />
                ) : (
                    <Text style={styles.number}>{step}</Text>
                )}
            </View>
            <View style={styles.text}>
                <Text style={styles.title}>{title}</Text>
                <Text style={styles.caption}>{caption}</Text>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    row: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        paddingHorizontal:
            metrics.onboarding.rowPaddingHorizontal - stroke.thin,
        paddingVertical: metrics.onboarding.rowPaddingVertical - stroke.thin,
        borderWidth: stroke.thin,
        borderColor: colors.border.default,
        borderRadius: radius.md,
        backgroundColor: colors.bg.surface,
    },
    badge: {
        width: metrics.onboarding.stepBadgeSize,
        height: metrics.onboarding.stepBadgeSize,
        alignItems: "center",
        justifyContent: "center",
        borderRadius: radius.full,
    },
    badgeTodo: {
        backgroundColor: colors.bg.subtle,
    },
    badgeDone: {
        backgroundColor: colors.text.primary,
    },
    number: {
        ...typography.labelButton,
        color: colors.text.onSubtle,
    },
    text: {
        flex: 1,
        gap: spacing.xs,
    },
    title: {
        ...typography.labelButton,
        color: colors.text.primary,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
});
