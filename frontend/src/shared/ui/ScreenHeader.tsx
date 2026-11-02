// 근처 · 기억 탭 헤더. 왼쪽은 Pin + 위치 문구(01 · 06 "지금 {동네}" · 06b "위치를 쓰지 않는 중") 또는 제목(07 · 07b), 오른쪽은 설정 톱니.
// 위치 문구가 null 이면(동네 이름을 모름 · 읽는 중) 왼쪽을 비운다. 스크롤 영역 밖에 고정한다. 상태바 inset 을 포함한다.
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Icon from "./Icon";
import { colors, metrics, spacing, typography } from "./theme";

type Props = (
    | { location: string | null; title?: never }
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
            {title != null ? (
                <Text numberOfLines={1} style={styles.title}>
                    {title}
                </Text>
            ) : location != null ? (
                <View style={styles.location}>
                    <Icon
                        name="pin"
                        size={metrics.screenHeader.locationIconSize}
                    />
                    <Text numberOfLines={1} style={styles.locationLabel}>
                        {location}
                    </Text>
                </View>
            ) : (
                // 설정 톱니를 오른쪽에 두기 위한 빈 자리.
                <View />
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
