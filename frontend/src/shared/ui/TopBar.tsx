// 상단 바. ← 또는 X + 제목(선택). 설정(11) · 장소 직접 찾기(02c) · 저장물 없음(12) 이 쓴다. 상태바 inset 을 포함한다.
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Icon from "./Icon";
import { colors, metrics, size, spacing, typography } from "./theme";

type TopBarIcon = "arrowLeft" | "close";

type Props = {
    icon?: TopBarIcon;
    title?: string;
    onIconPress: () => void;
};

const ICON_LABEL: Record<TopBarIcon, string> = {
    arrowLeft: "뒤로",
    close: "닫기",
};

export default function TopBar({
    icon = "arrowLeft",
    title,
    onIconPress,
}: Props) {
    const insets = useSafeAreaInsets();
    return (
        <View style={[styles.bar, { marginTop: insets.top }]}>
            <Pressable
                accessibilityRole="button"
                accessibilityLabel={ICON_LABEL[icon]}
                hitSlop={metrics.topBar.iconHitSlop}
                onPress={onIconPress}
            >
                <Icon name={icon} size={size.iconMd} />
            </Pressable>
            {title != null && (
                <Text numberOfLines={1} style={styles.title}>
                    {title}
                </Text>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    bar: {
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
