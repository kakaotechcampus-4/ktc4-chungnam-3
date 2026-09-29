// 저장 결과 모달. 분석 중 / 확인 필요 / 장소 없음 / 실패 상태별로 렌더한다.
// "직접 찾을게요" · "장소 직접 붙이기" 는 02c 로, "장소 없이 보관할게요" 는 모달 닫기로 간다.
// "다시 시도" 는 목에서 화면 안의 상태만 분석 중으로 바꾼다. 저장 · 보관 · 재시도 서버 호출은 runtime 작업 때 붙인다.
// 02 의 "{장소}으로 저장" 은 아직 동작하지 않는다.
import {
    type RouteProp,
    StackActions,
    useNavigation,
    useRoute,
} from "@react-navigation/native";
import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { saveResultsMock } from "../../shared/api/mock";
import type { BadgeStatus } from "../../shared/ui/Badge";
import Button from "../../shared/ui/Button";
import Icon from "../../shared/ui/Icon";
import NotFound from "../../shared/ui/NotFound";
import {
    colors,
    metrics,
    size,
    spacing,
    typography,
} from "../../shared/ui/theme";
import AnalyzingSkeleton from "./components/AnalyzingSkeleton";
import PlaceOption from "./components/PlaceOption";
import SourceHeader, {
    type SourceHeaderProps,
} from "./components/SourceHeader";
import { withRo } from "./particle";

// UI 타입이다. 서버 이름과의 대응은 백엔드 계약이 생기면 mappers 에서 한다.
type SaveResultState = "analyzing" | "needsConfirmation" | "noPlace" | "failed";

type Source = Omit<SourceHeaderProps, "status">;
type Candidate = { id: string; name: string; address: string };

type SaveResult =
    | {
          state: "needsConfirmation";
          source: Source;
          candidates: readonly Candidate[];
      }
    | { state: Exclude<SaveResultState, "needsConfirmation">; source: Source };

const results: Readonly<Record<string, SaveResult>> = saveResultsMock;

// 02 에는 배지가 없다 (Figma).
const BADGE: Record<SaveResultState, BadgeStatus | undefined> = {
    analyzing: "analyzing",
    needsConfirmation: undefined,
    noPlace: "noPlace",
    failed: "failed",
};

// 02 확인 필요 문구는 후보 수로 가른다. 1곳이면 02b (Figma 2094:303).
function confirmCopy(count: number) {
    if (count === 1) {
        return {
            heading: "이 영상, 여기가 맞나요?",
            body: "영상에서 1곳을 찾았어요. 여기가 맞는지 확인해주세요.",
            later: "나중에 확인할게요",
        };
    }
    return {
        heading: "이 영상, 어디였을까요?",
        body: `영상에서 ${count}곳을 찾았어요. 맞는 곳을 골라주세요.`,
        later: "나중에 고를게요",
    };
}

export default function SaveResultScreen() {
    const navigation = useNavigation();
    // SaveResult 모달 안 중첩 스택의 첫 화면(Result)이다.
    const route =
        useRoute<RouteProp<{ Result: { resultId: string } }, "Result">>();
    const insets = useSafeAreaInsets();

    const result = results[route.params.resultId];
    const candidates =
        result?.state === "needsConfirmation" ? result.candidates : [];
    const [selectedId, setSelectedId] = useState(candidates[0]?.id);
    // 05 "다시 시도" 는 같은 저장물을 다시 분석한다. 원본 영상 영역은 그대로 두고 상태만 03 으로 바꾼다.
    const [retrying, setRetrying] = useState(false);

    // 삭제·만료된 저장물 (12). "기억 목록으로" 는 모달을 닫고 기억 탭으로 간다.
    if (!result) {
        return (
            <NotFound
                topIcon="close"
                heading="이 저장물을 찾을 수 없어요"
                body="삭제됐거나 기간이 지난 저장이에요."
                onClose={() => navigation.goBack()}
                onGoArchive={() =>
                    navigation.dispatch(
                        StackActions.popTo("Main", { screen: "Archive" }),
                    )
                }
            />
        );
    }

    const state: SaveResultState = retrying ? "analyzing" : result.state;
    const close = () => navigation.goBack();
    // 02 · 02b "직접 찾을게요" 와 04 "장소 직접 붙이기" → 02c (모달 안에 push).
    const openSearch = () =>
        navigation.navigate("SaveResult", {
            screen: "PlaceSearch",
            params: { resultId: route.params.resultId },
        });
    const selected = candidates.find(
        (candidate) => candidate.id === selectedId,
    );
    const copy = confirmCopy(candidates.length);

    return (
        <ScrollView
            contentContainerStyle={[
                styles.container,
                {
                    paddingTop: insets.top,
                    paddingBottom: insets.bottom + spacing.xl,
                },
            ]}
        >
            <View style={styles.topBar}>
                <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="닫기"
                    hitSlop={metrics.saveResult.closeHitSlop}
                    onPress={close}
                >
                    <Icon name="close" size={size.iconMd} />
                </Pressable>
            </View>

            <SourceHeader {...result.source} status={BADGE[state]} />

            {state === "needsConfirmation" && (
                <>
                    <Message heading={copy.heading} body={copy.body} />
                    <View style={styles.options} accessibilityRole="radiogroup">
                        {candidates.map((candidate) => (
                            <PlaceOption
                                key={candidate.id}
                                name={candidate.name}
                                address={candidate.address}
                                selected={candidate.id === selectedId}
                                onPress={() => setSelectedId(candidate.id)}
                            />
                        ))}
                        <Pressable
                            accessibilityRole="button"
                            hitSlop={metrics.directOption.hitSlop}
                            onPress={openSearch}
                            style={styles.direct}
                        >
                            <Icon name="pencil" size={size.iconSm} />
                            <Text style={styles.directLabel}>
                                여기 없어요, 직접 찾을게요
                            </Text>
                        </Pressable>
                    </View>
                </>
            )}

            {state === "analyzing" && (
                <>
                    <Message
                        heading="영상 속 장소를 찾고 있어요"
                        body="보통 10초 안에 끝나요. 이 화면을 닫아도 계속 찾아요."
                    />
                    <AnalyzingSkeleton />
                </>
            )}

            {state === "noPlace" && (
                <Message
                    heading="이 영상에선 장소를 못 찾았어요"
                    body="장소 이름이 나오지 않는 영상이었어요. 어딘지 알고 있다면 직접 붙여둘 수 있어요."
                />
            )}

            {state === "failed" && (
                <Message
                    heading="영상을 불러오지 못했어요"
                    body="비공개로 바뀌었거나 연결이 잠깐 불안정했어요. 링크는 그대로 보관해둘게요."
                />
            )}

            <View style={styles.spacer} />

            <View style={styles.actions}>
                {state === "needsConfirmation" && (
                    <>
                        {selected && (
                            <Button
                                kind="primary"
                                label={`${withRo(selected.name)} 저장`}
                            />
                        )}
                        <Button
                            kind="text"
                            label={copy.later}
                            onPress={close}
                        />
                    </>
                )}
                {state === "analyzing" && (
                    <Button
                        kind="secondary"
                        label="닫고 기다릴게요"
                        onPress={close}
                    />
                )}
                {state === "noPlace" && (
                    <>
                        <Button
                            kind="primary"
                            label="장소 직접 붙이기"
                            onPress={openSearch}
                        />
                        <Button
                            kind="text"
                            label="장소 없이 보관할게요"
                            onPress={close}
                        />
                    </>
                )}
                {state === "failed" && (
                    <>
                        <Button
                            kind="secondary"
                            label="다시 시도"
                            icon="refresh"
                            onPress={() => setRetrying(true)}
                        />
                        <Button
                            kind="text"
                            label="나중에 할게요"
                            onPress={close}
                        />
                    </>
                )}
            </View>
        </ScrollView>
    );
}

function Message({ heading, body }: { heading: string; body: string }) {
    return (
        <View style={styles.message}>
            <Text style={styles.heading}>{heading}</Text>
            <Text style={styles.body}>{body}</Text>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
    },
    topBar: {
        flexDirection: "row",
        padding: spacing.md,
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
    options: {
        gap: spacing.sm,
        paddingTop: spacing.lg,
        paddingHorizontal: spacing.lg,
    },
    direct: {
        flexDirection: "row",
        alignItems: "center",
        alignSelf: "flex-start",
        gap: spacing.sm,
        paddingHorizontal: metrics.directOption.paddingHorizontal,
        paddingVertical: spacing.md,
    },
    directLabel: {
        ...typography.labelButton,
        color: colors.text.onSubtle,
    },
    spacer: {
        flex: 1,
    },
    actions: {
        gap: spacing.xs,
        paddingHorizontal: spacing.lg,
    },
});
