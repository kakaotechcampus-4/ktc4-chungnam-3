// 필터 칩. Figma C/Chip. 높이 size.chip 고정, 선택 상태는 투명 테두리로 두 상태 크기를 맞춘다.
import { Pressable, StyleSheet, Text } from "react-native";

import {
    colors,
    metrics,
    radius,
    size,
    stroke,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    label: string;
    selected: boolean;
    onPress: () => void;
};

export default function Chip({ label, selected, onPress }: Props) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected }}
            hitSlop={metrics.chip.hitSlop}
            onPress={onPress}
            style={[styles.chip, selected ? styles.selected : styles.default]}
        >
            <Text
                numberOfLines={1}
                style={[
                    styles.label,
                    selected ? styles.labelSelected : styles.labelDefault,
                ]}
            >
                {label}
            </Text>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    chip: {
        height: size.chip,
        justifyContent: "center",
        paddingHorizontal: metrics.chip.paddingHorizontal - stroke.thin,
        borderRadius: radius.full,
        borderWidth: stroke.thin,
    },
    default: {
        borderColor: colors.border.default,
        backgroundColor: colors.bg.surface,
    },
    selected: {
        borderColor: "transparent",
        backgroundColor: colors.bg.subtle,
    },
    label: typography.labelButton,
    labelDefault: {
        color: colors.text.secondary,
    },
    labelSelected: {
        color: colors.text.primary,
    },
});
