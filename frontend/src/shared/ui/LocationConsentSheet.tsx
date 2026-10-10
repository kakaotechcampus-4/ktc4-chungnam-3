// 위치 정보 이용 동의 시트. Figma C/LocationConsentSheet 2201:963 (Default 2201:803 · Saving 2201:861 · Error 2201:911).
// 00d · 06b · 설정에서 띄운다. 동의 저장은 띄운 화면이 하고 상태(status)만 넘긴다.
// 바깥 닫힘(손잡이 내리기 · 배경 탭 · 시스템 뒤로 가기)도 시트를 닫고 onDismiss 로 알린다. saving 중에는 바깥 닫힘과 두 버튼을 막는다.
// 앱 루트의 BottomSheetModalProvider 위에 뜬다(하단 바까지 덮는다).
import {
    BottomSheetBackdrop,
    type BottomSheetBackdropProps,
    BottomSheetModal,
    BottomSheetView,
} from "@gorhom/bottom-sheet";
import { useCallback, useEffect, useRef } from "react";
import { BackHandler, Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { TERMS_URLS } from "../api/consentTerms";
import { openExternalUrl } from "../external-links/openExternalUrl";
import Button from "./Button";
import Icon, { type IconName } from "./Icon";
import {
    colors,
    metrics,
    radius,
    size,
    spacing,
    stroke,
    typography,
} from "./theme";

export type LocationConsentSheetStatus = "default" | "saving" | "error";

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
    const sheetRef = useRef<BottomSheetModal>(null);
    const insets = useSafeAreaInsets();
    const saving = status === "saving";
    // 띄운 적 없는 모달에 dismiss 를 부르지 않는다(라이브러리 상태가 닫는 중으로 남는다).
    const presented = useRef(false);

    useEffect(() => {
        if (visible) {
            presented.current = true;
            sheetRef.current?.present();
        } else if (presented.current) {
            sheetRef.current?.dismiss();
        }
    }, [visible]);

    const handleDismiss = useCallback(() => {
        presented.current = false;
        onDismiss();
    }, [onDismiss]);

    // 열려 있으면 시스템 뒤로 가기는 시트만 닫는다. 저장 중에는 아무것도 하지 않는다.
    useEffect(() => {
        if (!visible) return;
        const sub = BackHandler.addEventListener("hardwareBackPress", () => {
            if (!saving) sheetRef.current?.dismiss();
            return true;
        });
        return () => sub.remove();
    }, [visible, saving]);

    const renderBackdrop = useCallback(
        (props: BottomSheetBackdropProps) => (
            <BottomSheetBackdrop
                {...props}
                appearsOnIndex={0}
                disappearsOnIndex={-1}
                opacity={1}
                pressBehavior={saving ? "none" : "close"}
                style={[props.style, styles.backdrop]}
            />
        ),
        [saving],
    );

    return (
        <BottomSheetModal
            ref={sheetRef}
            enablePanDownToClose={!saving}
            backdropComponent={renderBackdrop}
            handleComponent={SheetGrip}
            backgroundStyle={styles.background}
            onDismiss={handleDismiss}
        >
            <BottomSheetView
                style={[
                    styles.content,
                    { paddingBottom: spacing.xl + insets.bottom },
                ]}
            >
                <View style={styles.body}>
                    <Text accessibilityRole="header" style={styles.heading}>
                        위치는 이렇게만 써요
                    </Text>
                    <View style={styles.facts}>
                        {FACTS.map((fact) => (
                            <View key={fact.icon} style={styles.fact}>
                                <Icon name={fact.icon} size={size.iconMd} />
                                <View style={styles.text}>
                                    <Text style={styles.title}>
                                        {fact.title}
                                    </Text>
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

                <View style={styles.actions}>
                    {status === "error" && (
                        <Text style={styles.error}>
                            동의를 저장하지 못했어요. 연결을 확인하고 다시
                            눌러주세요.
                        </Text>
                    )}
                    <View style={styles.buttons}>
                        <Button
                            kind="primary"
                            label={saving ? "저장하고 있어요" : "동의하고 계속"}
                            disabled={saving}
                            onPress={onAgree}
                        />
                        <Button
                            kind="text"
                            label="나중에 할게요"
                            disabled={saving}
                            onPress={onLater}
                        />
                    </View>
                </View>
            </BottomSheetView>
        </BottomSheetModal>
    );
}

// 손잡이 36 × 4 (border/default). 위 sm · 아래 md. 이 영역을 끌어 내리면 닫힌다.
function SheetGrip() {
    return (
        <View style={styles.gripRow}>
            <View style={styles.grip} />
        </View>
    );
}

const styles = StyleSheet.create({
    backdrop: {
        backgroundColor: colors.bg.overlay,
    },
    background: {
        backgroundColor: colors.bg.surface,
        borderTopLeftRadius: radius.lg,
        borderTopRightRadius: radius.lg,
    },
    gripRow: {
        alignItems: "center",
        paddingTop: spacing.sm,
        paddingBottom: spacing.md,
    },
    grip: {
        width: metrics.sheetHandle.width,
        height: metrics.sheetHandle.height,
        borderRadius: metrics.sheetHandle.height / 2,
        backgroundColor: colors.border.default,
    },
    content: {
        paddingTop: spacing.sm,
        paddingHorizontal: spacing.lg,
    },
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
    actions: {
        paddingTop: spacing.xl,
    },
    error: {
        ...typography.captionMeta,
        color: colors.status.dangerFg,
        paddingBottom: spacing.sm,
    },
    buttons: {
        gap: spacing.xs,
    },
});
