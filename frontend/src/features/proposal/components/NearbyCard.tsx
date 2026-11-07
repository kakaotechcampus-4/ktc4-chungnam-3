// 지금 가까운 저장물 카드. Figma C/Card/Nearby. 자두색 now pill 과 그림자로 다시 꺼낸 순간을 표시한다.
// 근처 목록의 첫 카드는 실행 모션의 앞장이 내려앉을 자리다(launchTarget). 카드 면이 앞장과 같은 색이라 교체가 보이지 않는다.
// 태그 줄은 응답에 태그가 없어 그리지 않는다. 요약이 없으면 그 줄을 숨긴다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useLaunchTargetMeasure } from "../../../shared/launch/launchTarget";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import {
    colors,
    effects,
    metrics,
    radius,
    spacing,
    typography,
} from "../../../shared/ui/theme";

export type NearbyCardProps = {
    now: string;
    time: string;
    place: string;
    summary?: string;
    thumbUri?: string;
    fade: ThumbFade;
    onPress?: () => void;
    launchTarget?: boolean;
};

export default function NearbyCard({
    now,
    time,
    place,
    summary,
    thumbUri,
    fade,
    onPress,
    launchTarget,
}: NearbyCardProps) {
    const measure = useLaunchTargetMeasure(({ x, y, width, height }) => ({
        left: x,
        top: y,
        width,
        height,
        radius: radius.lg,
        rotate: 0,
    }));
    return (
        <Pressable
            ref={launchTarget ? measure.ref : undefined}
            onLayout={launchTarget ? measure.onLayout : undefined}
            accessibilityRole={onPress ? "button" : undefined}
            disabled={!onPress}
            onPress={onPress}
            style={styles.card}
        >
            <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
            <View style={styles.content}>
                <View style={styles.now}>
                    <Text numberOfLines={1} style={styles.nowLabel}>
                        {now}
                    </Text>
                </View>
                <View style={styles.head}>
                    <Text numberOfLines={1} style={styles.time}>
                        {time}
                    </Text>
                    <Text numberOfLines={1} style={styles.place}>
                        {place}
                    </Text>
                </View>
                {summary != null && (
                    <Text
                        numberOfLines={metrics.nearbyCard.summaryMaxLines}
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
        // 실행 모션의 도착 라운드(위 launchTarget)와 같다.
        borderRadius: radius.lg,
        backgroundColor: colors.bg.surface,
        boxShadow: effects.elevationLow,
    },
    thumb: {
        width: metrics.nearbyCard.thumbWidth,
    },
    content: {
        flex: 1,
        gap: spacing.sm,
    },
    now: {
        alignSelf: "flex-start",
        paddingHorizontal: metrics.nearbyCard.nowPaddingHorizontal,
        paddingVertical: metrics.nearbyCard.nowPaddingVertical,
        borderRadius: radius.full,
        backgroundColor: colors.brand.primary,
    },
    nowLabel: {
        ...typography.captionMeta,
        color: colors.text.onPrimary,
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
    summary: {
        ...typography.bodyDefault,
        color: colors.text.onSubtle,
    },
});
