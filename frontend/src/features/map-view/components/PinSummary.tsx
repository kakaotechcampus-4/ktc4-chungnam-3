// 10b 요약 시트 본문. Figma 2103:575. 썸네일 · 시간 · 장소 · 도보 시간 · 화살표.
// 누르면 08 로 간다. 카테고리는 장소 목록 응답에 없어 보이지 않는다. 도보 시간이 없으면(지금 위치를 모름) 그 줄을 숨긴다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import { colors, metrics, spacing, typography } from "../../../shared/ui/theme";

export type PinSummaryProps = {
    time: string;
    place: string;
    walk?: string;
    thumbUri?: string;
    fade: ThumbFade;
};

type Props = PinSummaryProps & {
    onPress: () => void;
};

export default function PinSummary({
    time,
    place,
    walk,
    thumbUri,
    fade,
    onPress,
}: Props) {
    return (
        <Pressable
            accessibilityRole="button"
            accessibilityLabel={[place, time, walk].filter(Boolean).join(", ")}
            onPress={onPress}
            style={styles.row}
        >
            <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
            <View style={styles.info}>
                <Text style={styles.time}>{time}</Text>
                <Text numberOfLines={1} style={styles.place}>
                    {place}
                </Text>
                {walk != null && (
                    <Text numberOfLines={1} style={styles.meta}>
                        {walk}
                    </Text>
                )}
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
