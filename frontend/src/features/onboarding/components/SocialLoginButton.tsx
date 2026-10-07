// 소셜 로그인 버튼. Figma C/SocialLoginButton 2174:693. 높이 size/button, radius/md, 심볼과 라벨 사이 8.
// 색 · 심볼 · 서체는 제공자 디자인 가이드를 따른다(colors.provider). 제공자를 추가하면 VARIANT 에 한 항목을 더한다.
// 카카오: 레이블은 OS 기본 시스템 서체이고(fontFamily 를 지정하지 않는다), 높이는 버튼의 1/3 이하(48 → 16).
import { Image, type ImageSource } from "expo-image";
import { Pressable, StyleSheet, Text } from "react-native";

import type { LoginProvider } from "../../../shared/auth/providers";
import {
    colors,
    metrics,
    radius,
    size,
    spacing,
    typography,
} from "../../../shared/ui/theme";

const VARIANT: Record<
    NonNullable<LoginProvider["button"]>["variant"],
    {
        container: string;
        symbol: string;
        label: string;
        symbolSource: ImageSource;
    }
> = {
    kakao: {
        container: colors.provider.kakaoContainer,
        symbol: colors.provider.kakaoSymbol,
        label: colors.provider.kakaoLabel,
        // 카카오 공식 에셋의 말풍선 심볼. 직접 그리지 않는다.
        symbolSource: require("../../../../assets/images/providers/kakao-symbol.svg"),
    },
};

type Props = {
    variant: NonNullable<LoginProvider["button"]>["variant"];
    label: string;
    disabled?: boolean;
    onPress: () => void;
};

export default function SocialLoginButton({
    variant,
    label,
    disabled = false,
    onPress,
}: Props) {
    const tone = VARIANT[variant];
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel={label}
            accessibilityState={{ disabled }}
            disabled={disabled}
            onPress={onPress}
            style={[styles.button, { backgroundColor: tone.container }]}
        >
            <Image
                source={tone.symbolSource}
                tintColor={tone.symbol}
                contentFit="contain"
                style={styles.symbol}
                accessible={false}
            />
            <Text style={[styles.label, { color: tone.label }]}>{label}</Text>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    button: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: spacing.sm,
        height: size.button,
        borderRadius: radius.md,
    },
    symbol: {
        height: metrics.onboarding.providerSymbolSize,
        aspectRatio: metrics.onboarding.providerSymbolAspectRatio,
    },
    // 시스템 서체. 글자 크기는 Label/Button 과 같고, 줄 높이는 버튼 높이의 1/3 로 둔다.
    label: {
        fontSize: typography.labelButton.fontSize,
        lineHeight: size.button / 3,
        fontWeight: metrics.onboarding.providerLabelWeight,
        includeFontPadding: false,
    },
});
