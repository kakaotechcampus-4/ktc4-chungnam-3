// 버튼. Figma C/Button (Primary / Secondary / Text). 앞 아이콘(icon)은 선택이다.
import {
    Pressable,
    type StyleProp,
    StyleSheet,
    Text,
    type ViewStyle,
} from "react-native";

import Icon, { type IconName } from "./Icon";
import { colors, radius, size, spacing, stroke, typography } from "./theme";

export type ButtonKind = "primary" | "secondary" | "text";

type Props = {
    kind: ButtonKind;
    label: string;
    icon?: IconName;
    onPress?: () => void;
    style?: StyleProp<ViewStyle>;
};

const ICON_COLOR: Record<ButtonKind, string> = {
    primary: colors.icon.onPrimary,
    secondary: colors.icon.default,
    text: colors.icon.default,
};

export default function Button({ kind, label, icon, onPress, style }: Props) {
    return (
        <Pressable
            accessibilityRole="button"
            onPress={onPress}
            style={({ pressed }) => [
                styles.base,
                containerStyles[kind],
                pressed && kind === "primary" && styles.primaryPressed,
                style,
            ]}
        >
            {icon && (
                <Icon name={icon} size={size.iconSm} color={ICON_COLOR[kind]} />
            )}
            <Text style={[typography.labelButton, labelStyles[kind]]}>
                {label}
            </Text>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    base: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: spacing.sm,
        height: size.button,
        paddingHorizontal: spacing.lg,
        borderRadius: radius.md,
    },
    primaryPressed: {
        backgroundColor: colors.brand.primaryPressed,
    },
});

const containerStyles = StyleSheet.create({
    primary: {
        backgroundColor: colors.brand.primary,
    },
    secondary: {
        backgroundColor: colors.bg.surface,
        borderWidth: stroke.thin,
        borderColor: colors.border.default,
    },
    text: {},
});

const labelStyles = StyleSheet.create({
    primary: {
        color: colors.text.onPrimary,
    },
    secondary: {
        color: colors.text.primary,
    },
    text: {
        color: colors.text.onSubtle,
    },
});
