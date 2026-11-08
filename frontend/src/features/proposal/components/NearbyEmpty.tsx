// 06 근처 빈 상태. 가장 가까운 기억(장소 이름 · 직선거리)을 안내하고, 바랜 썸네일 스택을 보여준다.
// 저장한 장소가 하나도 없으면(nearest 없음) 안내 문장과 링크를 숨기고 썸네일은 자리표시다.
// 바랜 썸네일 스택(FadedStack)은 06b 도 쓴다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import Thumb from "../../../shared/ui/Thumb";
import { colors, metrics, spacing, typography } from "../../../shared/ui/theme";

// 현재 위치(동네 이름)는 화면 헤더가 그린다.
export type NearbyEmptyProps = {
    nearest?: { name: string; distance: string };
    stackThumbUris: readonly string[];
};

type Props = NearbyEmptyProps & {
    onOpenNearest: () => void;
};

export default function NearbyEmpty({
    nearest,
    stackThumbUris,
    onOpenNearest,
}: Props) {
    return (
        <View>
            <View style={styles.intro}>
                <Text style={styles.heading}>여기선 저장해둔 곳이 없어요</Text>
                {nearest != null && (
                    <>
                        <Text style={styles.body}>
                            가장 가까운 기억은 {nearest.distance} 떨어진{" "}
                            {nearest.name}에 있어요.
                        </Text>
                        <Pressable
                            accessibilityRole="link"
                            hitSlop={metrics.nearbyEmpty.linkHitSlop}
                            onPress={onOpenNearest}
                            style={styles.link}
                        >
                            <Text style={styles.linkLabel}>
                                {nearest.name} 기억 보기
                            </Text>
                            <Icon
                                name="chevronRight"
                                size={metrics.nearbyEmpty.linkIconSize}
                            />
                        </Pressable>
                    </>
                )}
            </View>

            <FadedStack thumbUris={stackThumbUris} />

            <View style={styles.footer}>
                <Text style={[styles.caption, styles.centered]}>
                    저장한 곳 근처에 가면 그때 조용히 꺼내드릴게요
                </Text>
            </View>
        </View>
    );
}

// 바랜 썸네일 3장 스택(06 2061:881 · 06b 2203:939). 주소가 없는 칸은 자리표시다.
export function FadedStack({ thumbUris }: { thumbUris: readonly string[] }) {
    return (
        <View style={styles.stack}>
            {metrics.nearbyEmpty.stack.map((item, index) => (
                <Thumb
                    key={item.rotate}
                    fade="months"
                    uri={thumbUris[index]}
                    style={[
                        styles.stackThumb,
                        {
                            top: item.top,
                            marginLeft:
                                item.dx -
                                metrics.nearbyEmpty.stackThumbWidth / 2,
                            transform: [{ rotate: item.rotate }],
                        },
                    ]}
                />
            ))}
        </View>
    );
}

const styles = StyleSheet.create({
    intro: {
        paddingTop: metrics.proposal.introPaddingTop,
        paddingHorizontal: spacing.lg,
        gap: metrics.proposal.introGap,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    centered: {
        textAlign: "center",
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    body: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
    link: {
        flexDirection: "row",
        alignItems: "center",
        alignSelf: "flex-start",
        gap: spacing.xs,
        paddingTop: spacing.sm,
    },
    linkLabel: {
        ...typography.labelButton,
        color: colors.text.onSubtle,
    },
    stack: {
        height: metrics.nearbyEmpty.stackHeight,
        overflow: "hidden",
    },
    stackThumb: {
        position: "absolute",
        left: "50%",
        width: metrics.nearbyEmpty.stackThumbWidth,
    },
    footer: {
        alignItems: "center",
        paddingHorizontal: spacing.lg,
    },
});
