// 끌 수 있는 시트 핸들. Figma 08 2061:993 · 10b 2103:573 (위 10, 36 × 4, bg/placeholder).
import { StyleSheet, View } from "react-native";

import { colors, metrics } from "./theme";

export default function SheetHandle() {
    return (
        <View style={styles.row}>
            <View style={styles.handle} />
        </View>
    );
}

const styles = StyleSheet.create({
    row: {
        alignItems: "center",
        paddingTop: metrics.sheetHandle.paddingTop,
    },
    handle: {
        width: metrics.sheetHandle.width,
        height: metrics.sheetHandle.height,
        borderRadius: metrics.sheetHandle.height / 2,
        backgroundColor: colors.bg.placeholder,
    },
});
