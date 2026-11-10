// 앨범 셀. 썸네일 + 상태 배지 + 한 줄 이름. 폭은 부모가 정한다.
// 이름은 부모가 정해 넘긴다(albumSections 의 albumLabel). 꼬리말("외 N곳")은 줄이지 않고 이름만 말줄임한다.
// 길게 누르기는 지우기 확인(07c)이다. onLongPress 가 있을 때만 접근성 동작 "지우기"를 붙인다(TalkBack 동작 메뉴 · 두 번 탭하고 길게 누르기).
import { Pressable, StyleSheet, Text, View } from "react-native";

import Badge, { type BadgeStatus } from "../../../shared/ui/Badge";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import { colors, metrics, typography } from "../../../shared/ui/theme";

export type AlbumCellProps = {
    label: string;
    labelSuffix?: string;
    thumbUri?: string;
    fade: ThumbFade;
    status?: BadgeStatus;
    onPress?: () => void;
    onLongPress?: () => void;
};

const LONG_PRESS_ACTIONS = [{ name: "longpress", label: "지우기" }];

export default function AlbumCell({
    label,
    labelSuffix,
    thumbUri,
    fade,
    status,
    onPress,
    onLongPress,
}: AlbumCellProps) {
    return (
        <Pressable
            accessibilityRole={onPress ? "button" : undefined}
            disabled={!onPress}
            onPress={onPress}
            onLongPress={onLongPress}
            accessibilityActions={onLongPress ? LONG_PRESS_ACTIONS : undefined}
            onAccessibilityAction={
                onLongPress
                    ? (event) => {
                          if (event.nativeEvent.actionName === "longpress") {
                              onLongPress();
                          }
                      }
                    : undefined
            }
            style={styles.cell}
        >
            <View>
                <Thumb fade={fade} uri={thumbUri} />
                {status && <Badge status={status} style={styles.badge} />}
            </View>
            <View style={styles.labelRow}>
                <Text numberOfLines={1} style={[styles.label, styles.name]}>
                    {label}
                </Text>
                {labelSuffix != null && (
                    <Text numberOfLines={1} style={styles.label}>
                        {` ${labelSuffix}`}
                    </Text>
                )}
            </View>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    cell: {
        flex: 1,
        gap: metrics.albumCell.labelGap,
    },
    badge: {
        position: "absolute",
        top: metrics.albumCell.badgeInset,
        left: metrics.albumCell.badgeInset,
    },
    labelRow: {
        flexDirection: "row",
    },
    label: {
        ...typography.captionMeta,
        color: colors.text.onSubtle,
    },
    // 이름만 줄어든다. 꼬리말은 제 폭을 지킨다.
    name: {
        flexShrink: 1,
    },
});
