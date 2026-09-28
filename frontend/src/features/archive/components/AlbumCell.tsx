// 앨범 셀. 썸네일 + 상태 배지 + 한 줄 이름. 폭은 부모가 정한다.
// 이름 규칙: 장소 확정이면 장소명, 미확정이면 영상 제목을 “ ”로 감싼다.
// partial(부분 성공)은 확정으로 보고 대표 장소명 하나를 보여준다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Badge, { type BadgeStatus } from "../../../shared/ui/Badge";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import { colors, metrics, typography } from "../../../shared/ui/theme";

const UNCONFIRMED: readonly BadgeStatus[] = [
    "analyzing",
    "needsConfirmation",
    "noPlace",
    "failed",
];

export type AlbumCellProps = {
    placeName?: string;
    videoTitle?: string;
    thumbUri?: string;
    fade: ThumbFade;
    status?: BadgeStatus;
    onPress?: () => void;
};

function cellName({
    placeName,
    videoTitle,
    status,
}: Pick<AlbumCellProps, "placeName" | "videoTitle" | "status">) {
    const unconfirmed = status != null && UNCONFIRMED.includes(status);
    return unconfirmed && videoTitle != null ? `“${videoTitle}”` : placeName;
}

export default function AlbumCell({
    placeName,
    videoTitle,
    thumbUri,
    fade,
    status,
    onPress,
}: AlbumCellProps) {
    return (
        <Pressable
            accessibilityRole={onPress ? "button" : undefined}
            disabled={!onPress}
            onPress={onPress}
            style={styles.cell}
        >
            <View>
                <Thumb fade={fade} uri={thumbUri} />
                {status && <Badge status={status} style={styles.badge} />}
            </View>
            <Text numberOfLines={1} style={styles.label}>
                {cellName({ placeName, videoTitle, status })}
            </Text>
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
    label: {
        ...typography.captionMeta,
        color: colors.text.onSubtle,
    },
});
