// 지도 사진 핀. Figma 10 pin(2103:471) · C/Thumb. 9:16, 폭과 테두리는 fade 로 정한다.
// variant 가 있으면 크기 · 테두리 · 그림자는 variant 로 정하고 바램만 fade 를 따른다(detail: 08 장소 핀).
// selected 는 테두리를 text/primary 로 바꾼다(10b 2103:545). 이름표는 PinLabel 이 따로 그린다.
// Android 는 Marker 안의 뷰를 그 크기의 비트맵으로 찍는다. 핀 비트맵 크기는 상태와 무관하게 고정한다.
// 다시 찍기는 redraw() 로 직접 요청한다. tracksViewChanges 는 자식 스타일 변화(테두리 색 등)를 다시 찍는다는 보장이 없다.
// zIndex 는 바꾸지 않는다. Fabric 이 마커를 빼고 다시 넣어 지도의 마커 목록이 어긋난다.
import { useIsFocused } from "@react-navigation/native";
import { Image } from "expo-image";
import { type RefObject, useEffect, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import { type LatLng, Marker, type MapMarker } from "react-native-maps";

import type { ThumbFade } from "./Thumb";
import { colors, effects, fade, metrics, radius } from "./theme";

type Props = {
    coordinate: LatLng;
    fade: ThumbFade;
    uri?: string;
    variant?: "detail";
    selected?: boolean;
    // 08 핀은 누를 곳이 없다.
    onPress?: () => void;
};

// 준비되거나 key 가 바뀌면, 또 화면에 다시 포커스가 오면 마커를 다시 찍는다.
// 다음 프레임과 redrawDelayMs 뒤 두 번 찍어 뷰가 덜 그려진 채 굳지 않게 한다. PinLabel 도 쓴다.
export function useMarkerRedraw(
    ref: RefObject<MapMarker | null>,
    ready: boolean,
    key: unknown,
) {
    const focused = useIsFocused();
    useEffect(() => {
        if (!ready || !focused) return;
        const redraw = () => ref.current?.redraw();
        const frame = requestAnimationFrame(redraw);
        const timer = setTimeout(redraw, metrics.photoMarker.redrawDelayMs);
        return () => {
            cancelAnimationFrame(frame);
            clearTimeout(timer);
        };
    }, [ref, ready, key, focused]);
}

// 핀 크기 · 테두리. variant 가 있으면 fade 와 관계없이 variant 로 정한다. 08 화면 맞추기도 쓴다.
export function photoMarkerSize(level: ThumbFade, variant?: Props["variant"]) {
    return variant
        ? metrics.photoMarker.variant[variant]
        : metrics.photoMarker[level];
}

export default function PhotoMarker({
    coordinate,
    fade: level,
    uri,
    variant,
    selected = false,
    onPress,
}: Props) {
    const [failed, setFailed] = useState(false);
    const [settled, setSettled] = useState(uri == null);
    const markerRef = useRef<MapMarker>(null);
    useMarkerRedraw(markerRef, settled, selected);

    const pin = photoMarkerSize(level, variant);
    const pinHeight = pin.width / metrics.thumb.aspectRatio;
    // 그림자가 있는 핀은 비트맵 가장자리에서 그림자가 잘리지 않게 사방에 투명 여백을 둔다.
    // Figma 는 recent 핀과 08 장소 핀에만 그림자가 있다.
    const raised =
        variant === "detail" || (variant == null && level === "recent");
    const margin = raised ? metrics.photoMarker.shadowMargin : 0;
    // 좌표는 핀 아래 가운데(여백 제외)에 둔다.
    const anchorY = (margin + pinHeight) / (pinHeight + margin * 2);

    return (
        <Marker
            ref={markerRef}
            coordinate={coordinate}
            anchor={{ x: 0.5, y: anchorY }}
            tracksViewChanges={false}
            onPress={onPress}
        >
            <View style={{ padding: margin }}>
                <View
                    style={[
                        styles.pin,
                        raised && styles.raised,
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
            </View>
        </Marker>
    );
}

const styles = StyleSheet.create({
    // 이미지가 없거나 로딩 중이면 bg/placeholder 가 보인다.
    pin: {
        borderRadius: radius.md,
        overflow: "hidden",
        backgroundColor: colors.bg.placeholder,
    },
    raised: {
        boxShadow: effects.elevationLow,
    },
    fade: {
        backgroundColor: colors.bg.fade,
    },
});
