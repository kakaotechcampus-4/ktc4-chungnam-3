// push 화면 상단 바. ← + 제목. 설정(11) · 장소 직접 찾기(02c) 가 쓴다. 상태바 inset 을 포함한다.
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Icon from "./Icon";
import { colors, metrics, size, spacing, typography } from "./theme";

type Props = {
    title: string;
    onBack: () => void;
};

export default function TopBar({ title, onBack }: Props) {
    const insets = useSafeAreaInsets();
    return (
        <View style={[styles.bar, { marginTop: insets.top }]}>
            <Pressable
                accessibilityRole="button"
                accessibilityLabel="뒤로"
                hitSlop={metrics.topBar.backHitSlop}
                onPress={onBack}
            >
                <Icon name="arrowLeft" size={size.iconMd} />
            </Pressable>
            <Text numberOfLines={1} style={styles.title}>
                {title}
            </Text>
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
