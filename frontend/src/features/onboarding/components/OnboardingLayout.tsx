// 온보딩 화면 공통 틀. 위 영역(일러스트 · 미리보기) · 제목 · 본문 · 아래 내용 · 빈 공간 · 버튼 영역.
// 상태바 · 제스처 inset 을 직접 처리한다. 작은 화면에서는 스크롤된다.
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors, metrics, spacing, typography } from "../../../shared/ui/theme";

type Props = {
    heading: string;
    body: string;
    // 제목 위 여백(상태바 아래부터). 화면마다 다르다.
    messageTop: number;
    header?: ReactNode;
    children?: ReactNode;
    footer: ReactNode;
};

export default function OnboardingLayout({
    heading,
    body,
    messageTop,
    header,
    children,
    footer,
}: Props) {
    const insets = useSafeAreaInsets();
    return (
        <ScrollView
            contentContainerStyle={[
                styles.container,
                { paddingTop: insets.top, paddingBottom: insets.bottom },
            ]}
        >
            {header}
            <View style={[styles.message, { paddingTop: messageTop }]}>
                <Text style={styles.heading}>{heading}</Text>
                <Text style={styles.body}>{body}</Text>
            </View>
            {children}
            <View style={styles.spacer} />
            {footer}
        </ScrollView>
    );
}

// 아래쪽 버튼 묶음. Primary 와 Text 버튼 사이 4.
export function OnboardingActions({ children }: { children: ReactNode }) {
    return <View style={styles.actions}>{children}</View>;
}

// 제목 아래 행 목록(00d · 00e 단계, 00f 안내). 행 사이 8.
export function OnboardingList({ children }: { children: ReactNode }) {
    return <View style={styles.list}>{children}</View>;
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
    },
    message: {
        gap: spacing.sm,
        paddingHorizontal: spacing.lg,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    body: {
        ...typography.bodyDefault,
        color: colors.text.secondary,
    },
    spacer: {
        flex: 1,
    },
    actions: {
        gap: spacing.xs,
        paddingHorizontal: spacing.lg,
        paddingBottom: spacing.xl,
    },
    list: {
        gap: spacing.sm,
        paddingTop: metrics.onboarding.listTop,
        paddingHorizontal: spacing.lg,
    },
});
