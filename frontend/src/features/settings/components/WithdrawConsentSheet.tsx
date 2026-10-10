// 11d 위치 정보 이용 동의 거두기 확인 2202:1102. 틀 · 여백은 동의 시트와 같다(ModalSheet).
// 거두기 저장은 설정 화면이 하고 상태(status)만 넘긴다. saving · error 는 Figma 에 없어 동의 시트와 같은 규칙으로 둔다.
import { StyleSheet, Text, View } from "react-native";

import ModalSheet, {
    ModalSheetActions,
    type ModalSheetStatus,
} from "../../../shared/ui/ModalSheet";
import { colors, spacing, typography } from "../../../shared/ui/theme";

type Props = {
    visible: boolean;
    status: ModalSheetStatus;
    onConfirm: () => void;
    onCancel: () => void;
    // 시트가 다 닫히면 부른다. 바깥 닫힘뿐 아니라 visible 을 false 로 바꿔 닫은 때도 온다.
    onDismiss: () => void;
};

export default function WithdrawConsentSheet({
    visible,
    status,
    onConfirm,
    onCancel,
    onDismiss,
}: Props) {
    const saving = status === "saving";
    return (
        <ModalSheet visible={visible} locked={saving} onDismiss={onDismiss}>
            <View style={styles.body}>
                <Text accessibilityRole="header" style={styles.heading}>
                    위치 정보 이용 동의를 거둘까요?
                </Text>
                <Text style={styles.message}>
                    {
                        "거두면 저장한 곳 근처에 와도 알려드리지 않아요.\n저장한 기억은 그대로 남아요."
                    }
                </Text>
                <Text style={styles.caption}>
                    위치 권한은 기기 설정에서 따로 끌 수 있어요.
                </Text>
            </View>

            <ModalSheetActions
                primary={{
                    label: saving ? "거두고 있어요" : "동의 거두기",
                    onPress: onConfirm,
                }}
                secondary={{ label: "그대로 둘게요", onPress: onCancel }}
                error={
                    status === "error"
                        ? "동의를 거두지 못했어요. 연결을 확인하고 다시 눌러주세요."
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
    message: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
});
