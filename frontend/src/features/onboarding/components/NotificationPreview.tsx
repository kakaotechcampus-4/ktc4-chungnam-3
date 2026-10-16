// 알림 미리보기 카드(00c 2175:772). 실제 알림이 어떻게 보일지 보여준다.
import { Image } from "expo-image";
import { StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import {
    colors,
    effects,
    metrics,
    radius,
    spacing,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    title: string;
    imageUri: string;
};

export default function NotificationPreview({ title, imageUri }: Props) {
    return (
        <View style={styles.card}>
            <View style={styles.app}>
                <View style={styles.appIcon}>
                    <Icon
                        name="pin"
                        size={metrics.onboarding.previewAppGlyphSize}
                        color={colors.icon.onPrimary}
                    />
                </View>
                <Text style={styles.appName}>REMEMBRALL · 지금</Text>
            </View>
            <View style={styles.content}>
                <Text style={styles.title}>{title}</Text>
                <Image
                    source={{ uri: imageUri }}
                    style={styles.image}
                    contentFit="cover"
                />
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    card: {
        gap: spacing.sm,
        paddingHorizontal: metrics.onboarding.previewPaddingHorizontal,
        paddingVertical: metrics.onboarding.previewPaddingVertical,
        borderRadius: radius.lg,
        backgroundColor: colors.bg.surface,
        boxShadow: effects.elevationLow,
    },
    app: {
        flexDirection: "row",
        alignItems: "center",
        gap: metrics.onboarding.previewAppGap,
    },
    appIcon: {
        padding: metrics.onboarding.previewAppIconPadding,
        borderRadius: radius.full,
        backgroundColor: colors.text.primary,
    },
    appName: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    content: {
        flexDirection: "row",
        alignItems: "flex-start",
        gap: spacing.md,
    },
    title: {
        ...typography.titleCard,
        flex: 1,
        color: colors.text.primary,
    },
    // 이미지가 없거나 로딩 중이면 bg/placeholder 가 보인다.
    image: {
        width: metrics.onboarding.previewImageSize,
        height: metrics.onboarding.previewImageSize,
        borderRadius: radius.md,
        backgroundColor: colors.bg.placeholder,
    },
});
