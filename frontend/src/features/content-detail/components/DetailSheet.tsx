// 08 장소 상세 시트 본문. head · 메모 묶음 · 원본 영상 줄. 핸들과 하단 버튼은 시트가 따로 그린다.
// 빈 필드(08b): 메모가 없으면 묶음 전체, 태그가 없으면 태그 줄을 숨긴다. 도보 시간은 showWalk 일 때만 붙인다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import Icon from "../../../shared/ui/Icon";
import Thumb, { type ThumbFade } from "../../../shared/ui/Thumb";
import {
    colors,
    metrics,
    radius,
    size,
    spacing,
    stroke,
    typography,
} from "../../../shared/ui/theme";

export type DetailSheetProps = {
    time: string;
    place: string;
    address: string;
    walk?: string;
    thumbUri?: string;
    fade: ThumbFade;
    note?: string;
    tags?: readonly string[];
    sourceMeta: string;
};

type Props = DetailSheetProps & {
    showWalk: boolean;
    noteExpanded: boolean;
};

export default function DetailSheet({
    time,
    place,
    address,
    walk,
    thumbUri,
    fade,
    note,
    tags,
    sourceMeta,
    showWalk,
    noteExpanded,
}: Props) {
    const meta = showWalk && walk ? `${address} · ${walk}` : address;
    return (
        <View style={styles.body}>
            <View style={styles.head}>
                <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
                <View style={styles.info}>
                    <Text numberOfLines={1} style={styles.time}>
                        {time}
                    </Text>
                    <Text numberOfLines={1} style={styles.place}>
                        {place}
                    </Text>
                    <Text numberOfLines={1} style={styles.caption}>
                        {meta}
                    </Text>
                </View>
            </View>

            {note != null && (
                <View style={styles.note}>
                    <Text style={styles.caption}>영상에서 저장해둔 것</Text>
                    <Text
                        numberOfLines={
                            noteExpanded
                                ? undefined
                                : metrics.contentDetail.noteCollapsedLines
                        }
                        style={styles.noteBody}
                    >
                        {note}
                    </Text>
                    {tags != null && tags.length > 0 && (
                        <View style={styles.tags}>
                            {tags.map((tag) => (
                                <View key={tag} style={styles.tag}>
                                    <Text style={styles.tagLabel}>#{tag}</Text>
                                </View>
                            ))}
                        </View>
                    )}
                </View>
            )}

            {/* 동작이 연결되면 disabled 를 뺀다. */}
            <Pressable
                accessibilityRole="link"
                accessibilityState={{ disabled: true }}
                style={styles.source}
            >
                <View style={styles.sourceText}>
                    <Text numberOfLines={1} style={styles.sourceTitle}>
                        원본 영상 다시 보기
                    </Text>
                    <Text numberOfLines={1} style={styles.caption}>
                        {sourceMeta}
                    </Text>
                </View>
                <Icon name="chevronRight" size={size.iconSm} />
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    // 핸들 아래 16, 좌우 20, 묶음 사이 16 (Figma Sheet 2061:992).
    body: {
        gap: metrics.contentDetail.sheetGap,
        paddingTop: metrics.contentDetail.sheetGap,
        paddingHorizontal: spacing.lg,
    },
    head: {
        flexDirection: "row",
        alignItems: "flex-end",
        gap: metrics.contentDetail.headGap,
    },
    thumb: {
        width: metrics.contentDetail.thumbWidth,
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
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    note: {
        gap: metrics.contentDetail.noteGap,
    },
    noteBody: {
        ...typography.bodyDefault,
        color: colors.text.primary,
    },
    tags: {
        flexDirection: "row",
        flexWrap: "wrap",
        gap: metrics.contentDetail.tagGap,
    },
    tag: {
        paddingHorizontal: spacing.sm,
        paddingVertical: metrics.contentDetail.tagPaddingVertical,
        borderRadius: radius.sm,
        backgroundColor: colors.bg.subtle,
    },
    tagLabel: {
        ...typography.captionMeta,
        color: colors.text.onSubtle,
    },
    source: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        paddingLeft: metrics.contentDetail.sourcePaddingLeft,
        paddingRight: spacing.md,
        paddingVertical: spacing.md,
        borderWidth: stroke.thin,
        borderColor: colors.border.default,
        borderRadius: radius.md,
    },
    sourceText: {
        flex: 1,
        gap: spacing.xs,
    },
    sourceTitle: {
        ...typography.labelButton,
        color: colors.text.primary,
    },
});
