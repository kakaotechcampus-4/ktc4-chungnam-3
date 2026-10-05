// 12 저장물 없음. 삭제·만료된 저장 결과나 장소 id 로 들어왔을 때 보여준다.
// 모달(저장 결과)은 X, push(장소 상세)는 ← 를 쓴다. 상태바 inset 은 TopBar 가, 제스처 inset 은 여기서 처리한다.
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Button from "./Button";
import TopBar from "./TopBar";
import { colors, metrics, radius, spacing, stroke, typography } from "./theme";

type Props = {
    topIcon: "close" | "arrowLeft";
    heading: string;
    body: string;
    onClose: () => void;
    onGoArchive: () => void;
};

export default function NotFound({
    topIcon,
    heading,
    body,
    onClose,
    onGoArchive,
}: Props) {
    const insets = useSafeAreaInsets();
    return (
        <ScrollView
            contentContainerStyle={[
                styles.container,
                { paddingBottom: insets.bottom + spacing.xl },
            ]}
        >
            <TopBar icon={topIcon} onIconPress={onClose} />

            <View style={styles.source}>
                <View style={styles.emptyThumb} />
            </View>

            <View style={styles.message}>
                <Text style={styles.heading}>{heading}</Text>
                <Text style={styles.body}>{body}</Text>
            </View>

            <View style={styles.spacer} />

            <View style={styles.actions}>
                <Button
                    kind="secondary"
                    label="기억 목록으로"
                    onPress={onGoArchive}
                />
            </View>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
    },
    source: {
        paddingTop: spacing.sm,
        paddingHorizontal: spacing.lg,
    },
    emptyThumb: {
        width: metrics.notFound.thumbWidth,
        aspectRatio: metrics.thumb.aspectRatio,
        borderWidth: stroke.medium,
        borderStyle: "dashed",
        borderColor: colors.border.default,
        borderRadius: radius.md,
    },
    message: {
        gap: spacing.sm,
        paddingTop: spacing.xl,
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
        paddingHorizontal: spacing.lg,
    },
});
