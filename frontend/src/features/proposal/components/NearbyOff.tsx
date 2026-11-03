// 06b 근처 · 위치 꺼짐(2203:939). 위치 권한이 없거나 위치를 받지 못할 때. 헤더("위치를 쓰지 않는 중")는 화면이 그린다.
// 바랜 썸네일은 저장한 장소가 있으면 최근 3곳, 없으면 자리표시. "위치 켜기"는 화면 하나의 주 버튼이다.
import { StyleSheet, Text, View } from "react-native";

import Button from "../../../shared/ui/Button";
import { colors, metrics, spacing, typography } from "../../../shared/ui/theme";
import { FadedStack } from "./NearbyEmpty";

type Props = {
    stackThumbUris: readonly string[];
    onTurnOn: () => void;
    // 권한 창 · 설정 화면을 여는 중에는 다시 누르지 못하게 한다.
    busy: boolean;
};

export default function NearbyOff({ stackThumbUris, onTurnOn, busy }: Props) {
    return (
        <View>
            <View style={styles.intro}>
                <Text style={styles.heading}>지금은 근처를 알 수 없어요</Text>
                <Text style={styles.body}>
                    위치 정보 이용에 동의하고 위치를 허용하면, 저장한 곳
                    근처에서 다시 꺼내드려요.
                </Text>
            </View>

            <FadedStack thumbUris={stackThumbUris} />

            <View style={styles.footer}>
                <Text style={[styles.caption, styles.centered]}>
                    저장한 곳은 기억 탭에서 그대로 볼 수 있어요
                </Text>
            </View>

            <View style={styles.action}>
                <Button
                    kind="primary"
                    label="위치 켜기"
                    onPress={onTurnOn}
                    disabled={busy}
                />
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    intro: {
        paddingTop: metrics.proposal.introPaddingTop,
        paddingHorizontal: spacing.lg,
        gap: metrics.proposal.introGap,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    body: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    centered: {
        textAlign: "center",
    },
    footer: {
        alignItems: "center",
        paddingHorizontal: spacing.lg,
    },
    // Figma 의 아래 여백 수치를 받지 못해 화면 좌우 여백(lg)과 큰 간격(xl)으로 둔다.
    action: {
        paddingTop: spacing.xl,
        paddingHorizontal: spacing.lg,
    },
});
