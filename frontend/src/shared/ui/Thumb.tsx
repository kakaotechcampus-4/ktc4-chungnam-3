// 저장물 썸네일. Figma C/Thumb. 오래 잊은 것일수록 bg/fade 를 덮어 바래게 한다.
// 폭은 style 로 받고(고정 폭 또는 flex) 높이는 9:16 비율로 정해진다.
// 이미지를 불러오지 못하면 이미지를 빼고 bg/placeholder 를 보여준다 (흐름 메모 08b).
import { Image } from "expo-image";
import { useEffect, useState } from "react";
import { type StyleProp, StyleSheet, View, type ViewStyle } from "react-native";

import Icon from "./Icon";
import { colors, fade, metrics, radius } from "./theme";

export type ThumbFade = keyof typeof fade;

type Props = {
    fade: ThumbFade;
    uri?: string;
    showSource?: boolean;
    style?: StyleProp<ViewStyle>;
};

export default function Thumb({
    fade: level,
    uri,
    showSource = true,
    style,
}: Props) {
    const [failed, setFailed] = useState(false);
    useEffect(() => setFailed(false), [uri]);

    return (
        <View style={[styles.frame, style]}>
            {uri != null && !failed && (
                <Image
                    source={{ uri }}
                    style={StyleSheet.absoluteFill}
                    contentFit="cover"
                    onError={() => setFailed(true)}
                />
            )}
            <View
                style={[
                    StyleSheet.absoluteFill,
                    styles.fade,
                    { opacity: fade[level] },
                ]}
            />
            {showSource && (
                <View style={styles.source}>
                    <View style={styles.play}>
                        <Icon
                            name="play"
                            size={metrics.thumb.playSize}
                            color={colors.icon.onPrimary}
                        />
                    </View>
                </View>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    // 이미지가 없거나 로딩 중이면 bg/placeholder 가 보인다.
    frame: {
        aspectRatio: metrics.thumb.aspectRatio,
        borderRadius: radius.md,
        overflow: "hidden",
        backgroundColor: colors.bg.placeholder,
    },
    fade: {
        backgroundColor: colors.bg.fade,
    },
    source: {
        position: "absolute",
        left: metrics.thumb.sourceInset,
        bottom: metrics.thumb.sourceInset,
        width: metrics.thumb.sourceSize,
        height: metrics.thumb.sourceSize,
        borderRadius: radius.full,
        backgroundColor: colors.bg.overlay,
    },
    play: {
        position: "absolute",
        left: metrics.thumb.playOffsetLeft,
        top: metrics.thumb.playOffsetTop,
    },
});
