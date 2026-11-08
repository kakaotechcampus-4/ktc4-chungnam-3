// 07c 기억 지우기 확인 2266:1206. 틀 · 여백은 11d 거두기 시트(WithdrawConsentSheet)와 같다(ModalSheet).
// 지우기 호출은 기억 탭이 하고 상태(status)만 넘긴다. saving 이면 바깥 닫힘과 두 버튼을 막고, error 면 버튼 위에 오류 문구를 보인다.
// 지울 대상(target)은 열 때 잡아 둔 값이다. 지운 뒤 목록에서 빠져도 닫히는 동안 내용이 그대로다.
// 이름 줄은 셀(AlbumCell)과 같은 규칙이다. 꼬리말("외 N곳")은 줄이지 않고 이름만 말줄임한다.
import { StyleSheet, Text, View } from "react-native";

import ModalSheet, {
    ModalSheetActions,
    type ModalSheetStatus,
} from "../../../shared/ui/ModalSheet";
import Thumb from "../../../shared/ui/Thumb";
import {
    colors,
    metrics,
    radius,
    spacing,
    typography,
} from "../../../shared/ui/theme";
import type { AlbumItem } from "../albumSections";

export type DeleteSaveTarget = Pick<
    AlbumItem,
    "label" | "labelSuffix" | "thumbUri" | "fade" | "savedAgo"
>;

type Props = {
    visible: boolean;
    status: ModalSheetStatus;
    target: DeleteSaveTarget | null;
    onConfirm: () => void;
    onCancel: () => void;
    // 시트가 다 닫히면 부른다. 바깥 닫힘뿐 아니라 visible 을 false 로 바꿔 닫은 때도 온다.
    onDismiss: () => void;
};

export default function DeleteSaveSheet({
    visible,
    status,
    target,
    onConfirm,
    onCancel,
    onDismiss,
}: Props) {
    const saving = status === "saving";
    return (
        <ModalSheet visible={visible} locked={saving} onDismiss={onDismiss}>
            <View style={styles.body}>
                <Text accessibilityRole="header" style={styles.heading}>
                    이 기억을 지울까요?
                </Text>
                {target != null && (
                    <View style={styles.target}>
                        <Thumb
                            fade={target.fade}
                            uri={target.thumbUri}
                            showSource={false}
                            style={styles.thumb}
                        />
                        <View style={styles.targetText}>
                            <View style={styles.labelRow}>
                                <Text
                                    numberOfLines={1}
                                    style={[styles.label, styles.name]}
                                >
                                    {target.label}
                                </Text>
                                {target.labelSuffix != null && (
                                    <Text
                                        numberOfLines={1}
                                        style={styles.label}
                                    >
                                        {` ${target.labelSuffix}`}
                                    </Text>
                                )}
                            </View>
                            <Text style={styles.savedAt}>
                                {`${target.savedAgo} 저장`}
                            </Text>
                        </View>
                    </View>
                )}
                <Text style={styles.message}>
                    {
                        "지운 기억은 되돌릴 수 없어요.\n같은 영상을 공유하면 다시 저장할 수 있어요."
                    }
                </Text>
            </View>

            <ModalSheetActions
                primary={{
                    label: saving ? "지우고 있어요" : "지우기",
                    onPress: onConfirm,
                }}
                secondary={{ label: "그대로 둘게요", onPress: onCancel }}
                error={
                    status === "error"
                        ? "지우지 못했어요. 연결을 확인하고 다시 눌러주세요."
                        : undefined
                }
                disabled={saving}
            />
        </ModalSheet>
    );
}

const styles = StyleSheet.create({
    body: {
        gap: spacing.sm,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    target: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        paddingVertical: spacing.xs,
    },
    thumb: {
        width: metrics.deleteSaveSheet.thumbWidth,
        borderRadius: radius.sm,
    },
    // 남은 폭을 채워 이름이 그 안에서 줄어들게 한다.
    targetText: {
        flex: 1,
        gap: spacing.xs,
    },
    labelRow: {
        flexDirection: "row",
    },
    label: {
        ...typography.bodyDefault,
        color: colors.text.primary,
    },
    // 이름만 줄어든다. 꼬리말은 제 폭을 지킨다.
    name: {
        flexShrink: 1,
    },
    savedAt: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    message: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
});
