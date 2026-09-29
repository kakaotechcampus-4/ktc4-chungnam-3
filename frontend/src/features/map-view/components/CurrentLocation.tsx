// 현재 위치 표시. Figma 10 2103:469 · 2103:470. 바깥 원 위 가운데에 점을 겹친다.
import { StyleSheet, View } from "react-native";
import { type LatLng, Marker } from "react-native-maps";

import { colors, metrics, radius } from "../../../shared/ui/theme";

type Props = {
    coordinate: LatLng;
};

export default function CurrentLocation({ coordinate }: Props) {
    return (
        <Marker
            coordinate={coordinate}
            anchor={{ x: 0.5, y: 0.5 }}
            tracksViewChanges={false}
            tappable={false}
        >
            <View style={styles.frame}>
                <View style={[StyleSheet.absoluteFill, styles.halo]} />
                <View style={styles.dot} />
            </View>
        </Marker>
    );
}

const styles = StyleSheet.create({
    frame: {
        width: metrics.currentLocation.haloSize,
        height: metrics.currentLocation.haloSize,
        alignItems: "center",
        justifyContent: "center",
    },
    halo: {
        borderRadius: radius.full,
        backgroundColor: colors.status.infoFg,
        opacity: metrics.currentLocation.haloOpacity,
    },
    dot: {
        width: metrics.currentLocation.dotSize,
        height: metrics.currentLocation.dotSize,
        borderRadius: radius.full,
        borderWidth: metrics.currentLocation.dotBorderWidth,
        borderColor: colors.bg.surface,
        backgroundColor: colors.status.infoFg,
    },
});
