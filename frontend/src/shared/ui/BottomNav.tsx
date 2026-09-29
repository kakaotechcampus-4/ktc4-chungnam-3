// 하단 바 "근처 / 지도 / 기억". Figma C/BottomNav. 내비게이션과 무관하게 active · onChange 만 받는다.
// 선택 표시는 bg/subtle pill 이다. 브랜드 색을 쓰지 않는다.
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Icon, { type IconName } from "./Icon";
import { colors, metrics, radius, spacing, stroke, typography } from "./theme";

export type BottomNavKey = "nearby" | "map" | "archive";

const TABS: { key: BottomNavKey; label: string; icon: IconName }[] = [
    { key: "nearby", label: "근처", icon: "footprints" },
    { key: "map", label: "지도", icon: "map" },
    { key: "archive", label: "기억", icon: "images" },
];

type Props = {
    active: BottomNavKey;
    onChange: (key: BottomNavKey) => void;
};

export default function BottomNav({ active, onChange }: Props) {
    const insets = useSafeAreaInsets();
    return (
        <View
            style={[styles.bar, { paddingBottom: spacing.sm + insets.bottom }]}
            accessibilityRole="tablist"
        >
            {TABS.map(({ key, label, icon }) => {
                const selected = key === active;
                return (
                    <Pressable
                        key={key}
                        style={styles.tab}
                        accessibilityRole="tab"
                        accessibilityState={{ selected }}
                        accessibilityLabel={label}
                        onPress={() => onChange(key)}
                    >
                        <View
                            style={[
                                styles.indicator,
                                selected && styles.indicatorActive,
                            ]}
                        >
                            <Icon
                                name={icon}
                                size={metrics.bottomNav.iconSize}
                                color={
                                    selected
                                        ? colors.icon.default
                                        : colors.icon.secondary
                                }
                            />
                        </View>
                        <Text
                            style={[
                                styles.label,
                                selected
                                    ? styles.labelActive
                                    : styles.labelInactive,
                            ]}
                        >
                            {label}
                        </Text>
                    </Pressable>
                );
            })}
        </View>
    );
}

const styles = StyleSheet.create({
    bar: {
        flexDirection: "row",
        alignItems: "center",
        paddingTop: spacing.sm,
        borderTopWidth: stroke.thin,
        borderTopColor: colors.border.default,
        backgroundColor: colors.bg.surface,
    },
    tab: {
        flex: 1,
        alignItems: "center",
        gap: metrics.bottomNav.labelGap,
    },
    indicator: {
        paddingHorizontal: spacing.lg,
        paddingVertical: spacing.xs,
        borderRadius: radius.full,
    },
    indicatorActive: {
        backgroundColor: colors.bg.subtle,
    },
    label: typography.captionMeta,
    labelActive: {
        color: colors.text.primary,
    },
    labelInactive: {
        color: colors.text.secondary,
    },
});
