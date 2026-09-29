// 08 시트 핸들. Figma 2061:993 (위 10, 36 × 4, bg/placeholder).
import { StyleSheet, View } from "react-native";

import { colors, metrics } from "../../../shared/ui/theme";

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
        paddingTop: metrics.contentDetail.sheetPaddingTop,
    },
    handle: {
        width: metrics.contentDetail.handleWidth,
        height: metrics.contentDetail.handleHeight,
        borderRadius: metrics.contentDetail.handleHeight / 2,
        backgroundColor: colors.bg.placeholder,
    },
});
