// 켜짐·꺼짐 표시. Figma C/Switch. RN 기본 Switch 는 Figma 모양과 달라 직접 그린다.
// 누르기와 접근성은 줄(SettingsRow)이 맡는다. 이 그림은 접근성 트리에서 숨긴다.
import { useEffect, useRef } from "react";
import { Animated, StyleSheet, View } from "react-native";

import { colors, metrics, radius } from "../../../shared/ui/theme";

const { width, knobSize, knobInset, durationMs } = metrics.settingsSwitch;
const TRAVEL = width - knobSize - knobInset * 2;

type Props = {
    value: boolean;
};

export default function Switch({ value }: Props) {
    const progress = useRef(new Animated.Value(value ? 1 : 0)).current;

    useEffect(() => {
        Animated.timing(progress, {
            toValue: value ? 1 : 0,
            duration: durationMs,
            useNativeDriver: true,
        }).start();
    }, [progress, value]);

    const translateX = progress.interpolate({
        inputRange: [0, 1],
        outputRange: [0, TRAVEL],
    });

    return (
        <View
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
            style={[styles.track, value ? styles.trackOn : styles.trackOff]}
        >
            <Animated.View
                style={[
                    styles.knob,
                    value ? styles.knobOn : styles.knobOff,
                    { transform: [{ translateX }] },
                ]}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    track: {
        width,
        height: metrics.settingsSwitch.height,
        justifyContent: "center",
        paddingHorizontal: knobInset,
        borderRadius: radius.full,
    },
    trackOn: {
        backgroundColor: colors.text.primary,
    },
    trackOff: {
        backgroundColor: colors.bg.placeholder,
    },
    knob: {
        width: knobSize,
        height: knobSize,
        borderRadius: radius.full,
    },
    knobOn: {
        backgroundColor: colors.bg.surface,
    },
    knobOff: {
        backgroundColor: colors.icon.secondary,
    },
});
