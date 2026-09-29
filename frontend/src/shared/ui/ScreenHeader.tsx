// 근처 · 기억 탭 헤더. 왼쪽은 현재 위치(01 · 06) 또는 제목(07 · 07b), 오른쪽은 설정 톱니.
// 스크롤 영역 밖에 고정한다. 상태바 inset 을 포함한다.
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Icon from "./Icon";
import { colors, metrics, spacing, typography } from "./theme";

type Props = (
    | { location: string; title?: never }
    | { title: string; location?: never }
) & {
    onSettingsPress: () => void;
};

export default function ScreenHeader({
    location,
    title,
    onSettingsPress,
}: Props) {
    const insets = useSafeAreaInsets();
    return (
        <View style={[styles.header, { marginTop: insets.top }]}>
            {location != null ? (
                <View style={styles.location}>
                    <Icon
                        name="pin"
                        size={metrics.screenHeader.locationIconSize}
                    />
                    <Text numberOfLines={1} style={styles.locationLabel}>
                        지금 {location}
                    </Text>
                </View>
            ) : (
                <Text numberOfLines={1} style={styles.title}>
                    {title}
                </Text>
            )}
            <Pressable
                accessibilityRole="button"
                accessibilityLabel="설정"
                hitSlop={metrics.screenHeader.settingsHitSlop}
                onPress={onSettingsPress}
                style={styles.settings}
            >
                <Icon
                    name="settings"
                    size={metrics.screenHeader.settingsIconSize}
                />
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    header: {
        height: metrics.screenHeader.height,
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        paddingTop: spacing.sm,
        paddingLeft: spacing.lg,
        paddingRight: spacing.md,
    },
    location: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.xs,
    },
    locationLabel: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    title: {
        ...typography.titleCard,
        color: colors.text.primary,
    },
    settings: {
        width: metrics.screenHeader.settingsButtonSize,
        height: metrics.screenHeader.settingsButtonSize,
        alignItems: "center",
        justifyContent: "center",
    },
});
