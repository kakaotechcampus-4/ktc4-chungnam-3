// 장소 검색창. Figma C/SearchField. 비어 있으면 안내 글자, 입력하면 진한 테두리 + 지우기.
import { Pressable, StyleSheet, TextInput, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import {
    colors,
    metrics,
    radius,
    size,
    spacing,
    stroke,
    typography,
} from "../../../shared/ui/theme";

type Props = {
    value: string;
    onChangeText: (text: string) => void;
    autoFocus?: boolean;
};

export default function SearchField({
    value,
    onChangeText,
    autoFocus = false,
}: Props) {
    const filled = value.length > 0;
    return (
        <View style={[styles.field, filled ? styles.filled : styles.empty]}>
            <Icon name="search" size={metrics.searchField.iconSize} />
            <TextInput
                value={value}
                onChangeText={onChangeText}
                autoFocus={autoFocus}
                placeholder="가게 이름이나 주소"
                placeholderTextColor={colors.text.secondary}
                returnKeyType="search"
                accessibilityLabel="장소 검색"
                style={styles.input}
            />
            {filled && (
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="지우기"
                    hitSlop={metrics.searchField.clearHitSlop}
                    onPress={() => onChangeText("")}
                >
                    <Icon name="close" size={size.iconSm} />
                </Pressable>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    field: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.sm,
        height: size.button,
        paddingLeft: metrics.searchField.paddingLeft,
        paddingRight: spacing.md,
        borderWidth: stroke.thin,
        borderRadius: radius.md,
        backgroundColor: colors.bg.surface,
    },
    empty: {
        borderColor: colors.border.default,
    },
    filled: {
        borderColor: colors.text.primary,
    },
    input: {
        flex: 1,
        padding: metrics.searchField.inputPadding,
        ...typography.bodyDefault,
        color: colors.text.primary,
    },
});
