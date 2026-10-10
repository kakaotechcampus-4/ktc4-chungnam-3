// 버튼. Figma C/Button (Primary / Secondary / Text). 앞 아이콘(icon)은 선택이다.
// disabled: 눌리지 않는다. Primary 는 brand/primary-disabled 배경 · text/on-subtle 글자, Text 는 text/disabled 글자(C/LocationConsentSheet Saving).
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
    disabled?: boolean;
    style?: StyleProp<ViewStyle>;
};

const ICON_COLOR: Record<ButtonKind, string> = {
    primary: colors.icon.onPrimary,
    secondary: colors.icon.default,
    text: colors.icon.default,
};

export default function Button({
    kind,
    label,
    icon,
    onPress,
    disabled = false,
    style,
}: Props) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled }}
            disabled={disabled}
            onPress={onPress}
            style={({ pressed }) => [
                styles.base,
                containerStyles[kind],
                pressed && kind === "primary" && styles.primaryPressed,
                disabled && kind === "primary" && styles.primaryDisabled,
                style,
            ]}
        >
            {icon && (
                <Icon name={icon} size={size.iconSm} color={ICON_COLOR[kind]} />
            )}
            <Text
                style={[
                    typography.labelButton,
                    labelStyles[kind],
                    disabled && disabledLabelStyles[kind],
                ]}
            >
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
    primaryDisabled: {
        backgroundColor: colors.brand.primaryDisabled,
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

// Secondary 의 비활성 모습은 Figma 에 없어 글자만 흐리게 둔다.
const disabledLabelStyles = StyleSheet.create({
    primary: {
        color: colors.text.onSubtle,
    },
    secondary: {
        color: colors.text.disabled,
    },
    text: {
        color: colors.text.disabled,
    },
});
