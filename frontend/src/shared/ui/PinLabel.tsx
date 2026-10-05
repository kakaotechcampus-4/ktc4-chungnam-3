// 지도 핀 아래 장소 이름표(10b 2103:570 · 08 2061:971). 핀과 같은 좌표에 위쪽 가운데를 맞추고, 위 여백으로 핀과 간격을 둔다.
// 지도마다 하나를 계속 두고 좌표 · 글자만 바꾼다. 숨길 때는 opacity 로 숨기고 빼지 않는다.
// 지도가 숨겨진 동안 마커가 빠지면 Android 지도가 그 마커를 기본 핀으로 되살린다.
// 눌러도 동작은 없지만 누름은 받는다(Android 는 tappable 이 없다). 지도에는 marker-press 로 와서 선택이 풀리지 않는다.
import { useRef } from "react";
import { StyleSheet, Text, View } from "react-native";
import { type LatLng, Marker, type MapMarker } from "react-native-maps";

import { useMarkerRedraw } from "./PhotoMarker";
import { colors, metrics, radius, typography } from "./theme";

type Props = {
    coordinate: LatLng;
    label: string;
    visible?: boolean;
};

// 이름표는 어느 핀에도 가려지지 않게 핀(0)보다 위에 둔다. 값은 바꾸지 않는다.
const LABEL_Z_INDEX = 1;

// 핀 아래로 이름표가 차지하는 높이(위 간격 + 이름표). 08 화면 맞추기가 쓴다.
export const PIN_LABEL_EXTENT =
    metrics.photoMarker.labelGap +
    typography.captionMeta.lineHeight +
    metrics.photoMarker.labelPaddingVertical * 2;

export default function PinLabel({ coordinate, label, visible = true }: Props) {
    const markerRef = useRef<MapMarker>(null);
    useMarkerRedraw(markerRef, true, label);
    return (
        <Marker
            ref={markerRef}
            coordinate={coordinate}
            anchor={{ x: 0.5, y: 0 }}
            tracksViewChanges={false}
            zIndex={LABEL_Z_INDEX}
            opacity={visible ? 1 : 0}
        >
            {/* 위 여백까지 비트맵 크기에 들어가도록 평탄화하지 않는다. */}
            <View collapsable={false} style={styles.frame}>
                <View style={styles.label}>
                    <Text numberOfLines={1} style={styles.text}>
                        {label}
                    </Text>
                </View>
            </View>
        </Marker>
    );
}

const styles = StyleSheet.create({
    frame: {
        paddingTop: metrics.photoMarker.labelGap,
    },
    label: {
        paddingHorizontal: metrics.photoMarker.labelPaddingHorizontal,
        paddingVertical: metrics.photoMarker.labelPaddingVertical,
        borderRadius: radius.full,
        backgroundColor: colors.text.primary,
    },
    text: {
        ...typography.captionMeta,
        color: colors.text.onPrimary,
    },
});
