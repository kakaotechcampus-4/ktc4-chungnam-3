// 위치 정보 이용 동의 시트. Figma C/LocationConsentSheet 2201:963 (Default 2201:803 · Saving 2201:861 · Error 2201:911).
// 00d · 06b · 설정에서 띄운다. 동의 저장은 띄운 화면이 하고 상태(status)만 넘긴다.
// 틀(바깥 닫힘 · saving 중 차단 · 버튼 묶음)은 ModalSheet 가 맡는다.
import { Pressable, StyleSheet, Text, View } from "react-native";

import { TERMS_URLS } from "../api/consentTerms";
import { openExternalUrl } from "../external-links/openExternalUrl";
import Icon, { type IconName } from "./Icon";
import ModalSheet, {
    ModalSheetActions,
    type ModalSheetStatus,
} from "./ModalSheet";
import {
    colors,
    metrics,
    radius,
    size,
    spacing,
    stroke,
    typography,
} from "./theme";

export type LocationConsentSheetStatus = ModalSheetStatus;

type Props = {
    visible: boolean;
    status: LocationConsentSheetStatus;
    onAgree: () => void;
    onLater: () => void;
    // 시트가 다 닫히면 부른다. 바깥 닫힘뿐 아니라 visible 을 false 로 바꿔 닫은 때도 온다.
    onDismiss: () => void;
};

const FACTS: readonly { icon: IconName; title: string; caption: string }[] = [
    {
        icon: "pin",
        title: "저장한 곳 근처에 왔는지만 확인해요",
        caption: "도착하면 그 장소와 시각만 서버로 보내요",
    },
    {
        icon: "footprints",
        title: "지나온 길은 남기지 않아요",
        caption: "이동 경로나 실시간 위치는 보내지 않아요",
    },
    {
        icon: "settings",
        title: "언제든 거둘 수 있어요",
        caption: "설정의 위치 정보 이용 동의에서 바꿀 수 있어요",
    },
];

export default function LocationConsentSheet({
    visible,
    status,
    onAgree,
    onLater,
    onDismiss,
}: Props) {
    const saving = status === "saving";
    return (
        <ModalSheet visible={visible} locked={saving} onDismiss={onDismiss}>
            <View style={styles.body}>
                <Text accessibilityRole="header" style={styles.heading}>
                    위치는 이렇게만 써요
                </Text>
                <View style={styles.facts}>
                    {FACTS.map((fact) => (
                        <View key={fact.icon} style={styles.fact}>
                            <Icon name={fact.icon} size={size.iconMd} />
                            <View style={styles.text}>
                                <Text style={styles.title}>{fact.title}</Text>
                                <Text style={styles.caption}>
                                    {fact.caption}
                                </Text>
                            </View>
                        </View>
                    ))}
                </View>
                <Pressable
                    accessibilityRole="button"
                    onPress={() =>
                        openExternalUrl(TERMS_URLS.locationBasedService)
                    }
                    style={styles.terms}
                >
                    <View style={styles.text}>
                        <Text style={styles.title}>
                            위치기반서비스 이용약관
                        </Text>
                        <Text style={styles.caption}>
                            근처 알림을 쓰려면 이 약관에 동의가 필요해요
                        </Text>
                    </View>
                    <Icon name="chevronRight" size={size.iconMd} />
                </Pressable>
            </View>

            <ModalSheetActions
                primary={{
                    label: saving ? "저장하고 있어요" : "동의하고 계속",
                    onPress: onAgree,
                }}
                secondary={{ label: "나중에 할게요", onPress: onLater }}
                error={
                    status === "error"
                        ? "동의를 저장하지 못했어요. 연결을 확인하고 다시 눌러주세요."
                        : undefined
                }
                disabled={saving}
            />
        </ModalSheet>
    );
}

const styles = StyleSheet.create({
    body: {
        gap: spacing.lg,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    facts: {
        gap: spacing.md,
    },
    fact: {
        flexDirection: "row",
        alignItems: "flex-start",
        gap: spacing.md,
    },
    text: {
        flex: 1,
        gap: spacing.xs,
    },
    title: {
        ...typography.labelButton,
        color: colors.text.primary,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    terms: {
        flexDirection: "row",
        alignItems: "center",
        gap: spacing.md,
        paddingHorizontal:
            metrics.locationConsentSheet.termsPaddingHorizontal - stroke.thin,
        paddingVertical:
            metrics.locationConsentSheet.termsPaddingVertical - stroke.thin,
        borderWidth: stroke.thin,
        borderColor: colors.border.default,
        borderRadius: radius.md,
    },
});
