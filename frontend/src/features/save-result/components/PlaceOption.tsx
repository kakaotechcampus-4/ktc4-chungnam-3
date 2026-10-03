// 02 장소 후보 한 개. 선택되면 1.5 테두리 + Check.
// 테두리는 크기에 포함하지 않는다. padding 에서 테두리 두께를 빼 두 상태 모두 70 · 같은 글자 위치가 된다.
import { Pressable, StyleSheet, Text, View } from "react-native";

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
    name: string;
    address: string;
    selected: boolean;
    onPress: () => void;
};

export default function PlaceOption({
    name,
    address,
    selected,
    onPress,
}: Props) {
    return (
        <Pressable
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            onPress={onPress}
            style={[
                styles.option,
                selected ? styles.selected : styles.unselected,
            ]}
        >
            <View style={styles.text}>
                <Text numberOfLines={1} style={styles.name}>
                    {name}
                </Text>
                <Text numberOfLines={1} style={styles.address}>
                    {address}
                </Text>
            </View>
            {selected && (
                <Icon name="check" size={metrics.placeOption.checkSize} />
            )}
        </Pressable>
    );
}

const styles = StyleSheet.create({
    option: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        borderRadius: radius.md,
        backgroundColor: colors.bg.surface,
    },
    selected: {
        paddingHorizontal:
            metrics.placeOption.paddingHorizontal - stroke.medium,
        paddingVertical: metrics.placeOption.paddingVertical - stroke.medium,
        borderWidth: stroke.medium,
        borderColor: colors.text.primary,
    },
    unselected: {
        paddingHorizontal: metrics.placeOption.paddingHorizontal - stroke.thin,
        paddingVertical: metrics.placeOption.paddingVertical - stroke.thin,
        borderWidth: stroke.thin,
        borderColor: colors.border.default,
    },
    text: {
        flex: 1,
        gap: spacing.xs,
    },
    name: {
        ...typography.titleCard,
        color: colors.text.primary,
    },
    address: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
});
