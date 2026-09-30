// 10b 요약 시트 본문. Figma 2103:575. 썸네일 · 시간 · 장소 · 도보 시간과 카테고리 · 화살표.
// 누르면 08 로 간다. 도보 시간은 showWalk 일 때만 붙이고, 없으면 카테고리만 보인다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import {
    colors,
    metrics,
    spacing,
    typography,
} from "../../../shared/ui/theme";

export type PinSummaryProps = {
    time: string;
    place: string;
    walk?: string;
    category: string;
    thumbUri?: string;
    fade: ThumbFade;
};

type Props = PinSummaryProps & {
    showWalk: boolean;
    onPress: () => void;
};

export default function PinSummary({
    time,
    place,
    walk,
    category,
    thumbUri,
    fade,
    showWalk,
    onPress,
}: Props) {
    const meta = showWalk && walk ? `${walk} · ${category}` : category;

    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${place}, ${time}, ${meta}`}
            onPress={onPress}
            style={styles.row}
        >
            <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
            <View style={styles.info}>
                <Text style={styles.time}>{time}</Text>
                <Text numberOfLines={1} style={styles.place}>
                    {place}
                </Text>
                <Text numberOfLines={1} style={styles.meta}>
                    {meta}
                </Text>
            </View>
            <Icon
                name="chevronRight"
                size={metrics.mapView.summaryChevronSize}
            />
        </Pressable>
    );
}

const styles = StyleSheet.create({
    row: {
        flexDirection: "row",
        alignItems: "center",
        gap: metrics.mapView.summaryGap,
    },
    thumb: {
        width: metrics.mapView.summaryThumbWidth,
    },
    info: {
        flex: 1,
        gap: spacing.xs,
    },
    time: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    place: {
        ...typography.titleCard,
        color: colors.text.primary,
    },
    meta: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
});
