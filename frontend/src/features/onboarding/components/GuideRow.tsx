// 첫 저장 안내 행(00f 2175:902). 아이콘 원 36 + 두 줄. 테두리 없음.
import { StyleSheet, Text, View } from "react-native";

import Icon, { type IconName } from "../../../shared/ui/Icon";
import {
    colors,
    metrics,
    radius,
    spacing,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    icon: IconName;
    title: string;
    caption: string;
};

export default function GuideRow({ icon, title, caption }: Props) {
    return (
        <View style={styles.row}>
            <View style={styles.icon}>
                <Icon name={icon} size={metrics.onboarding.guideGlyphSize} />
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
        paddingHorizontal: metrics.onboarding.rowPaddingHorizontal,
        paddingVertical: metrics.onboarding.rowPaddingVertical,
        borderRadius: radius.md,
        backgroundColor: colors.bg.surface,
    },
    icon: {
        width: metrics.onboarding.guideIconSize,
        height: metrics.onboarding.guideIconSize,
        alignItems: "center",
        justifyContent: "center",
        borderRadius: radius.full,
        backgroundColor: colors.bg.subtle,
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
