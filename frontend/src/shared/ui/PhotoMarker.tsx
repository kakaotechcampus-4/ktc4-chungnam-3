// 지도 사진 핀. Figma 10 pin(2103:471) · C/Thumb. 9:16, 폭과 테두리는 fade 로 정한다.
// 선택되면 테두리가 text/primary 로 바뀌고 아래에 장소 이름 라벨이 붙는다(10b 2103:545 · 2103:570).
// Android 는 Marker 안의 뷰를 비트맵으로 찍는다. 이미지가 정해질 때와 선택이 바뀔 때만 다시 찍는다.
import { Image } from "expo-image";
import { useEffect, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { type LatLng, Marker } from "react-native-maps";

import type { ThumbFade } from "./Thumb";
import { colors, effects, fade, metrics, radius, typography } from "./theme";

type Props = {
    coordinate: LatLng;
    fade: ThumbFade;
    uri?: string;
    label: string;
    selected: boolean;
    // 08 핀은 누를 곳이 없다.
    onPress?: () => void;
};

const LABEL_HEIGHT =
    typography.captionMeta.lineHeight +
    metrics.photoMarker.labelPaddingVertical * 2;

export default function PhotoMarker({
    coordinate,
    fade: level,
    uri,
    label,
    selected,
    onPress,
}: Props) {
    const [failed, setFailed] = useState(false);
    const [settled, setSettled] = useState(uri == null);
    const [tracking, setTracking] = useState(true);

    // 이미지가 뜨거나 실패하면, 또 선택이 바뀌면 한 프레임 다시 찍고 멈춘다.
    useEffect(() => {
        if (!settled) return;
        setTracking(true);
        const frame = requestAnimationFrame(() => setTracking(false));
        return () => cancelAnimationFrame(frame);
    }, [settled, selected]);

    const pin = metrics.photoMarker[level];
    const pinHeight = pin.width / metrics.thumb.aspectRatio;
    // 좌표는 핀 아래 가운데에 둔다. 라벨이 붙어도 핀 자리가 움직이지 않게 한다.
    const anchorY = selected
        ? pinHeight / (pinHeight + metrics.photoMarker.labelGap + LABEL_HEIGHT)
        : 1;

    return (
        <Marker
            coordinate={coordinate}
            anchor={{ x: 0.5, y: anchorY }}
            tracksViewChanges={tracking}
            zIndex={selected ? 1 : 0}
            onPress={onPress}
        >
            <View style={styles.column}>
                <View
                    style={[
                        styles.pin,
                        level === "recent" && styles.raised,
                        {
                            width: pin.width,
                            height: pinHeight,
                            borderWidth: pin.borderWidth,
                            borderColor: selected
                                ? colors.text.primary
                                : colors.bg.surface,
                        },
                    ]}
                >
                    {uri != null && !failed && (
                        <Image
                            source={{ uri }}
                            style={StyleSheet.absoluteFill}
                            contentFit="cover"
                            onLoad={() => setSettled(true)}
                            onError={() => {
                                setFailed(true);
                                setSettled(true);
                            }}
                        />
                    )}
                    <View
                        style={[
                            StyleSheet.absoluteFill,
                            styles.fade,
                            { opacity: fade[level] },
                        ]}
                    />
                </View>
                {selected && (
                    <View style={styles.label}>
                        <Text numberOfLines={1} style={styles.labelText}>
                            {label}
                        </Text>
                    </View>
                )}
            </View>
        </Marker>
    );
}

const styles = StyleSheet.create({
    column: {
        alignItems: "center",
        gap: metrics.photoMarker.labelGap,
    },
    // 이미지가 없거나 로딩 중이면 bg/placeholder 가 보인다.
    pin: {
        borderRadius: radius.md,
        overflow: "hidden",
        backgroundColor: colors.bg.placeholder,
    },
    // Figma 는 recent 핀에만 그림자가 있다.
    raised: {
        boxShadow: effects.elevationLow,
    },
    fade: {
        backgroundColor: colors.bg.fade,
    },
    label: {
        paddingHorizontal: metrics.photoMarker.labelPaddingHorizontal,
        paddingVertical: metrics.photoMarker.labelPaddingVertical,
        borderRadius: radius.full,
        backgroundColor: colors.text.primary,
    },
    labelText: {
        ...typography.captionMeta,
        color: colors.text.onPrimary,
    },
});
