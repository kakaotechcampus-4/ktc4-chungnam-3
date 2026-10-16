// 아래에서 올라오는 확인 시트의 틀. Figma C/LocationConsentSheet 2201:963 · 11d 2202:1102 와 같은 바탕 · 손잡이 · 여백.
// 내용(children)과 버튼 묶음(ModalSheetActions)만 시트마다 다르다. 앱 루트의 BottomSheetModalProvider 위에 뜬다(하단 바까지 덮는다).
// 바깥 닫힘(손잡이 내리기 · 배경 탭 · 시스템 뒤로 가기)은 시트를 닫고 onDismiss 로 알린다. locked 면 바깥 닫힘을 막는다(저장 중).
import {
    BottomSheetBackdrop,
    type BottomSheetBackdropProps,
    BottomSheetModal,
    BottomSheetView,
} from "@gorhom/bottom-sheet";
import { type ReactNode, useCallback, useEffect, useRef } from "react";
import { BackHandler, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Button from "./Button";
import { colors, metrics, radius, spacing, typography } from "./theme";

// 저장을 띄운 화면이 하는 시트의 상태. saving 은 바깥 닫힘과 두 버튼을 막고, error 는 버튼 위에 오류 문구를 보인다.
export type ModalSheetStatus = "default" | "saving" | "error";

type Props = {
    visible: boolean;
    locked: boolean;
    // 시트가 다 닫히면 부른다. 바깥 닫힘뿐 아니라 visible 을 false 로 바꿔 닫은 때도 온다.
    onDismiss: () => void;
    children: ReactNode;
};

export default function ModalSheet({
    visible,
    locked,
    onDismiss,
    children,
}: Props) {
    const sheetRef = useRef<BottomSheetModal>(null);
    const insets = useSafeAreaInsets();
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

    // 열려 있으면 시스템 뒤로 가기는 시트만 닫는다. locked 면 아무것도 하지 않는다.
    useEffect(() => {
        if (!visible) return;
        const sub = BackHandler.addEventListener("hardwareBackPress", () => {
            if (!locked) sheetRef.current?.dismiss();
            return true;
        });
        return () => sub.remove();
    }, [visible, locked]);

    const renderBackdrop = useCallback(
        (props: BottomSheetBackdropProps) => (
            <BottomSheetBackdrop
                {...props}
                appearsOnIndex={0}
                disappearsOnIndex={-1}
                opacity={1}
                pressBehavior={locked ? "none" : "close"}
                style={[props.style, styles.backdrop]}
            />
        ),
        [locked],
    );

    return (
        <BottomSheetModal
            ref={sheetRef}
            enablePanDownToClose={!locked}
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
                {children}
            </BottomSheetView>
        </BottomSheetModal>
    );
}

type Action = { label: string; onPress: () => void };

type ActionsProps = {
    primary: Action;
    secondary: Action;
    // 있으면 버튼 위에 보인다(Error).
    error?: string;
    disabled: boolean;
};

// 버튼 묶음. 위 xl, 간격 xs. Primary + Text. 오류 문구는 맨 위(아래 sm).
export function ModalSheetActions({
    primary,
    secondary,
    error,
    disabled,
}: ActionsProps) {
    return (
        <View style={styles.actions}>
            {error != null && <Text style={styles.error}>{error}</Text>}
            <View style={styles.buttons}>
                <Button
                    kind="primary"
                    label={primary.label}
                    disabled={disabled}
                    onPress={primary.onPress}
                />
                <Button
                    kind="text"
                    label={secondary.label}
                    disabled={disabled}
                    onPress={secondary.onPress}
                />
            </View>
        </View>
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
