// 저장물 카드. Figma C/Card/Memory. meta 는 도보 시간이다(카테고리는 응답에 없다).
// 태그 줄은 응답에 태그가 없어 그리지 않는다. 요약이 없으면 그 줄을 숨긴다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import {
    colors,
    metrics,
    radius,
    spacing,
    typography,
} from "../../../shared/ui/theme";

export type MemoryCardProps = {
    time: string;
    place: string;
    meta: string;
    summary?: string;
    thumbUri?: string;
    fade: ThumbFade;
    onPress?: () => void;
};

export default function MemoryCard({
    time,
    place,
    meta,
    summary,
    thumbUri,
    fade,
    onPress,
}: MemoryCardProps) {
    return (
        <Pressable
            accessibilityRole={onPress ? "button" : undefined}
            disabled={!onPress}
            onPress={onPress}
            style={styles.card}
        >
            <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
            <View style={styles.content}>
                <View style={styles.head}>
                    <Text numberOfLines={1} style={styles.time}>
                        {time}
                    </Text>
                    <Text numberOfLines={1} style={styles.place}>
                        {place}
                    </Text>
                </View>
                <Text numberOfLines={1} style={styles.meta}>
                    {meta}
                </Text>
                {summary != null && (
                    <Text
                        numberOfLines={metrics.memoryCard.summaryMaxLines}
                        style={styles.summary}
                    >
                        {summary}
                    </Text>
                )}
            </View>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    card: {
        flexDirection: "row",
        alignItems: "flex-start",
        gap: spacing.md,
        padding: spacing.md,
        borderRadius: radius.lg,
        backgroundColor: colors.bg.surface,
    },
    thumb: {
        width: metrics.memoryCard.thumbWidth,
    },
    content: {
        flex: 1,
        gap: spacing.sm,
    },
    head: {
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
    summary: {
        ...typography.bodyDefault,
        color: colors.text.onSubtle,
    },
});
