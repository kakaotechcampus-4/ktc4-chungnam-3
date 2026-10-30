// 공유 받음 시트. Figma C/ShareReceivedSheet 2212:1109
// (Saving 2212:986 · Saved 2212:1020 · AlreadySaved 2212:1047 · Queued 2212:1074 · NotShorts 2212:1094).
// 틀(손잡이 · 바깥 닫힘 · Saving 중 차단 · 버튼 묶음)은 ModalSheet 가 맡는다. 처리 흐름은 띄운 쪽(app/share)이 한다.
// 썸네일은 바램 없는 원래 색이다(방금 저장한 기억이 가장 선명하다). 불러오지 못하면 자리표시.
import { StyleSheet, Text, View } from "react-native";

import ModalSheet, { ModalSheetActions } from "../../shared/ui/ModalSheet";
import Thumb from "../../shared/ui/Thumb";
import { colors, metrics, spacing, typography } from "../../shared/ui/theme";

// app/share 의 ShareSheetState 와 같은 모양(features 는 app 을 import 하지 않는다).
export type ShareReceivedSheetContent =
    | {
          kind: "saving" | "saved" | "alreadySaved" | "queued";
          thumbnailUrl: string | null;
      }
    | { kind: "notShorts" };

type Props = {
    visible: boolean;
    content: ShareReceivedSheetContent;
    onBack: () => void;
    onOpenArchive: () => void;
    // 시트가 다 닫히면 부른다. 바깥 닫힘뿐 아니라 visible 을 false 로 바꿔 닫은 때도 온다.
    onDismiss: () => void;
};

const COPY: Record<
    ShareReceivedSheetContent["kind"],
    { title: string; body?: string }
> = {
    saving: { title: "저장하고 있어요" },
    saved: {
        title: "저장했어요",
        body: "어디인지 찾으면 기억 탭에 넣어둘게요.\n그 근처에 가면 다시 꺼내드려요.",
    },
    alreadySaved: {
        title: "이미 저장한 영상이에요",
        body: "기억 탭에서 다시 볼 수 있어요.",
    },
    queued: {
        title: "저장해둘게요",
        body: "다음에 앱을 열 때 다시 보낼게요.",
    },
    notShorts: {
        title: "Shorts 링크만 저장할 수 있어요",
        body: "지금은 YouTube Shorts만 받고 있어요.",
    },
};

export default function ShareReceivedSheet({
    visible,
    content,
    onBack,
    onOpenArchive,
    onDismiss,
}: Props) {
    const saving = content.kind === "saving";
    // Saving 에서는 비활성으로 보인다(버튼이 생겼다 사라지며 높이가 바뀌지 않게).
    const showArchive =
        content.kind === "saving" ||
        content.kind === "saved" ||
        content.kind === "alreadySaved";
    const copy = COPY[content.kind];

    return (
        <ModalSheet visible={visible} locked={saving} onDismiss={onDismiss}>
            <View style={styles.body}>
                {content.kind !== "notShorts" && (
                    <Thumb
                        fade="recent"
                        uri={content.thumbnailUrl ?? undefined}
                        style={styles.thumb}
                    />
                )}
                <View style={styles.text}>
                    <Text accessibilityRole="header" style={styles.heading}>
                        {copy.title}
                    </Text>
                    {copy.body != null && (
                        <Text style={styles.caption}>{copy.body}</Text>
                    )}
                </View>
            </View>

            <ModalSheetActions
                primary={{ label: "돌아가기", onPress: onBack }}
                secondary={
                    showArchive
                        ? { label: "기억 탭 보기", onPress: onOpenArchive }
                        : undefined
                }
                disabled={saving}
            />
        </ModalSheet>
    );
}

const styles = StyleSheet.create({
    body: {
        flexDirection: "row",
        alignItems: "flex-end",
        gap: spacing.lg,
    },
    thumb: {
        width: metrics.shareReceivedSheet.thumbWidth,
    },
    text: {
        flex: 1,
        gap: spacing.sm,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    caption: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
});
