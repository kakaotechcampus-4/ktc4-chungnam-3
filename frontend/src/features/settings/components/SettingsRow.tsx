// 설정 줄 하나. 제목(+ 설명) · 값 · > 또는 Switch.
// Switch 줄은 줄 전체를 누르면 켜짐·꺼짐이 바뀌고, 접근성 역할 switch 도 줄에 붙는다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import {
    colors,
    metrics,
    size,
    spacing,
    typography,
} from "../../../shared/ui/theme";
import Switch from "./Switch";

type Props = {
    title: string;
    description?: string;
    value?: string;
    chevron?: boolean;
    switchValue?: boolean;
    onPress?: () => void;
    disabled?: boolean;
};

export default function SettingsRow({
    title,
    description,
    value,
    chevron = false,
    switchValue,
    onPress,
    disabled = false,
}: Props) {
    const isSwitch = switchValue != null;
    const pressable = onPress != null && !disabled;
    const role = isSwitch
        ? "switch"
        : pressable || disabled
          ? "button"
          : undefined;
    return (
        <Pressable
            accessibilityRole={role}
            accessibilityState={
                isSwitch ? { checked: switchValue, disabled } : { disabled }
            }
            disabled={!pressable}
            onPress={onPress}
            style={styles.row}
        >
            <View style={styles.text}>
                <Text numberOfLines={1} style={styles.title}>
                    {title}
                </Text>
                {description != null && (
                    <Text numberOfLines={1} style={styles.caption}>
                        {description}
                    </Text>
                )}
            </View>
            {value != null && (
                <Text numberOfLines={1} style={styles.caption}>
                    {value}
                </Text>
            )}
            {chevron && <Icon name="chevronRight" size={size.iconSm} />}
            {isSwitch && <Switch value={switchValue} />}
        </Pressable>
    );
}

const styles = StyleSheet.create({
    row: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        paddingHorizontal: metrics.settings.rowPaddingHorizontal,
        paddingVertical: metrics.settings.rowPaddingVertical,
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
