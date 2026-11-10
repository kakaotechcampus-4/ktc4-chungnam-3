// 저장물 썸네일. Figma C/Thumb. 오래 잊은 것일수록 bg/fade 를 덮어 바래게 한다.
// 폭은 style 로 받고(고정 폭 또는 flex) 높이는 9:16 비율로 정해진다.
// 이미지를 불러오지 못하면 이미지를 빼고 bg/placeholder 를 보여준다 (흐름 메모 08b).
// restoreFrom 이 있으면 바램 덮개를 그 값에서 fade 로 한 번 풀어 준다(근처에서 연 08, "근처에 가면 원래 색으로 돌아온다").
// 이미지를 불러온 뒤 시작하고, 이미지가 없거나 불러오지 못하면 · 시스템 동작 줄이기면 풀림 없이 바로 fade 로 그린다.
// 마운트된 동안 한 번만 풀린다. restoreFrom 이 없으면 덮개는 지금처럼 고정 View 다(풀림 컴포넌트를 그리지 않는다).
import { Image } from "expo-image";
import { useEffect, useRef, useState } from "react";
import { type StyleProp, StyleSheet, View, type ViewStyle } from "react-native";
import Animated, {
    useAnimatedStyle,
    useReducedMotion,
    useSharedValue,
    withTiming,
} from "react-native-reanimated";

import Icon from "./Icon";
import { colors, fade, metrics, radius } from "./theme";

export type ThumbFade = keyof typeof fade;

type Props = {
    fade: ThumbFade;
    uri?: string;
    // 앱에 번들한 이미지(require). 있으면 uri 대신 쓴다. 네트워크 없이 바로 그려진다(00a 일러스트).
    image?: number;
    showSource?: boolean;
    style?: StyleProp<ViewStyle>;
    restoreFrom?: ThumbFade;
};

export default function Thumb({
    fade: level,
    uri,
    image,
    showSource = true,
    style,
    restoreFrom,
}: Props) {
    const [failed, setFailed] = useState(false);
    const [loaded, setLoaded] = useState(false);
    useEffect(() => {
        setFailed(false);
        setLoaded(false);
    }, [uri, image]);
    const hasImage = (image != null || uri != null) && !failed;

    return (
        <View style={[styles.frame, style]}>
            {hasImage && (
                <Image
                    source={image ?? { uri }}
                    style={StyleSheet.absoluteFill}
                    contentFit="cover"
                    onLoad={
                        restoreFrom != null ? () => setLoaded(true) : undefined
                    }
                    onError={() => setFailed(true)}
                />
            )}
            {restoreFrom != null ? (
                <RestoringFade
                    from={restoreFrom}
                    to={level}
                    image={!hasImage ? "none" : loaded ? "loaded" : "loading"}
                />
            ) : (
                <View
                    style={[
                        StyleSheet.absoluteFill,
                        styles.fade,
                        { opacity: fade[level] },
                    ]}
                />
            )}
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

// 풀리는 바램 덮개. 이미지를 불러오는 동안은 from 으로 덮고, 불러오면(loaded) from → to 로 한 번 풀린다.
// 이미지가 없거나 불러오지 못하면(none) · 동작 줄이기면 풀림 없이 바로 to 다. 한 번 정해진 뒤에는 다시 바래지 않는다.
function RestoringFade({
    from,
    to,
    image,
}: {
    from: ThumbFade;
    to: ThumbFade;
    image: "loading" | "loaded" | "none";
}) {
    const reducedMotion = useReducedMotion();
    const instant = reducedMotion || image === "none";
    const opacity = useSharedValue(instant ? fade[to] : fade[from]);
    const settled = useRef(instant);
    useEffect(() => {
        if (settled.current || image === "loading") return;
        settled.current = true;
        opacity.value =
            image === "none"
                ? fade[to]
                : withTiming(fade[to], {
                      duration: metrics.thumb.restoreDurationMs,
                  });
    }, [image, to, opacity]);
    const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));
    return (
        <Animated.View
            style={[StyleSheet.absoluteFill, styles.fade, animatedStyle]}
        />
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
