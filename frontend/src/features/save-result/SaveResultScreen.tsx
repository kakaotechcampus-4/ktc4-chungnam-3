// 저장 결과 모달. 분석 중(03) / 확인 필요(02 · 02b) / 장소 없음(04) / 실패(05) 상태별로 렌더한다.
// 저장물 상세 API 를 읽는다(usePersonalSaveDetail). 분석 중이면 5초마다 다시 읽고, 끝나면 그 상태로 바뀐다.
// ready 가 되면 이 모달을 장소 상세(08)로 바꾼다(뒤로 가면 기억 탭). 404 는 12, 읽기 실패는 Figma 05 문구 + 다시 읽기.
// 05 문구와 "다시 시도"는 실패 종류로 가른다(failedMessage). 다시 시도는 같은 링크를 다시 저장하고 상세를 다시 읽는다.
// 개발 모드에서 personalSaveId 가 화면 목(saveResults) 키면 그 목을 그린다(개발용 딥링크 · 02 · 02b). 이때 버튼은 목 동작이다.
// "직접 찾을게요" · "장소 직접 붙이기" 는 02c 로 간다. 저장 · 보관 · 나중에 계열 버튼은 모두 모달을 닫는다.
import {
    type RouteProp,
    StackActions,
    useNavigation,
    useRoute,
} from "@react-navigation/native";
import { type ReactNode, useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { saveResultsMock } from "../../shared/api/mock";
import { savePersonalSave } from "../../shared/api/personalSaves";
import { usePersonalSaveDetail } from "../../shared/api/usePersonalSaveDetail";
import {
    youtubeShortsUrl,
    youtubeThumbnailUrl,
} from "../../shared/external-links/youtubeLinks";
import type { BadgeStatus } from "../../shared/ui/Badge";
import Button from "../../shared/ui/Button";
import Icon from "../../shared/ui/Icon";
import NotFound from "../../shared/ui/NotFound";
import { savedAgo } from "../../shared/ui/savedAt";
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
import { failedMessage } from "./failedMessage";
import { withRo } from "./particle";

// UI 타입이다. 서버 상태와의 대응은 mappers/personalSaveMapper 의 SaveState 다.
type SaveResultState = "analyzing" | "needsConfirmation" | "noPlace" | "failed";

type Source = Omit<SourceHeaderProps, "status">;
type Candidate = { id: string; name: string; address: string };

type MockResult =
    | {
          state: "needsConfirmation";
          source: Source;
          candidates: readonly Candidate[];
      }
    | { state: Exclude<SaveResultState, "needsConfirmation">; source: Source };

const mockResults: Readonly<Record<string, MockResult>> = saveResultsMock;

// 02 에는 배지가 없다 (Figma).
const BADGE: Record<SaveResultState, BadgeStatus | undefined> = {
    analyzing: "analyzing",
    needsConfirmation: undefined,
    noPlace: "noPlace",
    failed: "failed",
};

// 03 본문은 실측 평균(59초)에 맞춘다(디자인 세션 결정).
const ANALYZING_COPY = {
    heading: "영상 속 장소를 찾고 있어요",
    body: "보통 1분쯤 걸려요. 이 화면을 닫아도 계속 찾아요.",
};

const NO_PLACE_COPY = {
    heading: "이 영상에선 장소를 못 찾았어요",
    body: "장소 이름이 나오지 않는 영상이었어요. 어딘지 알고 있다면 직접 붙여둘 수 있어요.",
};

// 05 링크 줄은 스킴과 www 를 뺀다(Figma 05 "youtube.com/shorts/…").
const SHORTS_URL_PREFIX = "https://www.";

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
    // SaveResult 모달 안 중첩 스택의 첫 화면(Result)이다.
    const route =
        useRoute<RouteProp<{ Result: { personalSaveId: string } }, "Result">>();
    const { personalSaveId } = route.params;
    const mock = __DEV__ ? mockResults[personalSaveId] : undefined;
    return mock != null ? (
        <MockSaveResult personalSaveId={personalSaveId} result={mock} />
    ) : (
        <SavedResult personalSaveId={personalSaveId} />
    );
}

// 저장물 상세 API 로 그린다.
function SavedResult({ personalSaveId }: { personalSaveId: string }) {
    const navigation = useNavigation();
    const { view, reload } = usePersonalSaveDetail(personalSaveId, {
        pollWhileAnalyzing: true,
    });
    const [retrying, setRetrying] = useState(false);
    const ready = view.status === "loaded" && view.state.kind === "ready";

    // 분석이 끝나 장소가 있으면 모달을 08 로 바꾼다. 뒤로 가면 모달 아래의 기억 탭이다.
    useEffect(() => {
        if (!ready) return;
        navigation
            .getParent()
            ?.dispatch(
                StackActions.replace("ContentDetail", { personalSaveId }),
            );
    }, [ready, navigation, personalSaveId]);

    const close = () => navigation.goBack();

    if (view.status === "notFound") return <SaveNotFound />;
    if (view.status === "loading") return <ResultFrame onClose={close} />;
    if (view.status === "failed") {
        const message = failedMessage("readFailed");
        return (
            <ResultFrame
                onClose={close}
                actions={<FailedActions onRetry={reload} onLater={close} />}
            >
                <Message heading={message.heading} body={message.body} />
            </ResultFrame>
        );
    }

    const { detail, state } = view;
    const source: Source = {
        thumbUri: youtubeThumbnailUrl(detail.videoId),
        meta: `${savedAgo(new Date(detail.savedAt), new Date())} 저장 · YouTube Shorts`,
        title: detail.title ?? undefined,
        url: youtubeShortsUrl(detail.videoId).replace(SHORTS_URL_PREFIX, ""),
    };
    // 02 · 02b 는 상세 응답에 없어 이 경로에서는 나오지 않는다(되묻기 연결 전).
    const openSearch = () =>
        navigation.navigate("SaveResult", {
            screen: "PlaceSearch",
            params: {
                personalSaveId,
                sourceTitle: detail.title,
            },
        });
    // 같은 링크를 다시 저장한다. 서버가 다시 분석하면 다시 읽을 때 03 으로 바뀐다. 실패하면 05 에 머문다.
    const retrySave = () => {
        setRetrying(true);
        savePersonalSave(detail.sourceUrl)
            .then(() => reload())
            .catch(() => {})
            .finally(() => setRetrying(false));
    };

    // ready 는 08 로 바뀌기 전 잠깐이다. 03 을 그대로 둔다.
    if (state.kind === "analyzing" || state.kind === "ready") {
        return (
            <ResultFrame
                onClose={close}
                source={{ ...source, status: BADGE.analyzing }}
                actions={
                    <Button
                        kind="secondary"
                        label="닫고 기다릴게요"
                        onPress={close}
                    />
                }
            >
                <Message {...ANALYZING_COPY} />
                <AnalyzingSkeleton />
            </ResultFrame>
        );
    }
    if (state.kind === "noPlace") {
        return (
            <ResultFrame
                onClose={close}
                source={{ ...source, status: BADGE.noPlace }}
                actions={
                    <NoPlaceActions onSearch={openSearch} onKeep={close} />
                }
            >
                <Message {...NO_PLACE_COPY} />
            </ResultFrame>
        );
    }
    const message = failedMessage(state.failure);
    return (
        <ResultFrame
            onClose={close}
            source={{ ...source, status: BADGE.failed }}
            actions={
                <FailedActions
                    onRetry={message.retry == null ? undefined : retrySave}
                    retrying={retrying}
                    onLater={close}
                />
            }
        >
            <Message heading={message.heading} body={message.body} />
        </ResultFrame>
    );
}

// 개발용 화면 목. 다시 시도는 화면 안의 상태만 03 으로 바꾼다.
function MockSaveResult({
    personalSaveId,
    result,
}: {
    personalSaveId: string;
    result: MockResult;
}) {
    const navigation = useNavigation();
    const candidates =
        result.state === "needsConfirmation" ? result.candidates : [];
    const [selectedId, setSelectedId] = useState(candidates[0]?.id);
    const [retrying, setRetrying] = useState(false);

    const state: SaveResultState = retrying ? "analyzing" : result.state;
    const close = () => navigation.goBack();
    // 02 · 02b "직접 찾을게요" 와 04 "장소 직접 붙이기" → 02c (모달 안에 push).
    const openSearch = () =>
        navigation.navigate("SaveResult", {
            screen: "PlaceSearch",
            params: { personalSaveId },
        });
    const selected = candidates.find(
        (candidate) => candidate.id === selectedId,
    );
    const copy = confirmCopy(candidates.length);
    const failed = failedMessage(null);

    return (
        <ResultFrame
            onClose={close}
            source={{ ...result.source, status: BADGE[state] }}
            actions={
                <>
                    {state === "needsConfirmation" && (
                        <>
                            {selected && (
                                <Button
                                    kind="primary"
                                    label={`${withRo(selected.name)} 저장`}
                                    onPress={close}
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
                        <NoPlaceActions onSearch={openSearch} onKeep={close} />
                    )}
                    {state === "failed" && (
                        <FailedActions
                            onRetry={() => setRetrying(true)}
                            onLater={close}
                        />
                    )}
                </>
            }
        >
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
                    <Message {...ANALYZING_COPY} />
                    <AnalyzingSkeleton />
                </>
            )}
            {state === "noPlace" && <Message {...NO_PLACE_COPY} />}
            {state === "failed" && (
                <Message heading={failed.heading} body={failed.body} />
            )}
        </ResultFrame>
    );
}

// 삭제·만료된 저장물 (12). "기억 목록으로" 는 모달을 닫고 기억 탭으로 간다.
function SaveNotFound() {
    const navigation = useNavigation();
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

// 닫기 줄 · 원본 영상 · 본문 · 아래 버튼. 읽는 중에는 닫기 줄만 보인다.
function ResultFrame({
    onClose,
    source,
    children,
    actions,
}: {
    onClose: () => void;
    source?: SourceHeaderProps;
    children?: ReactNode;
    actions?: ReactNode;
}) {
    const insets = useSafeAreaInsets();
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
                    onPress={onClose}
                >
                    <Icon name="close" size={size.iconMd} />
                </Pressable>
            </View>

            {source != null && <SourceHeader {...source} />}
            {children}

            <View style={styles.spacer} />

            {actions != null && <View style={styles.actions}>{actions}</View>}
        </ScrollView>
    );
}

function NoPlaceActions({
    onSearch,
    onKeep,
}: {
    onSearch: () => void;
    onKeep: () => void;
}) {
    return (
        <>
            <Button
                kind="primary"
                label="장소 직접 붙이기"
                onPress={onSearch}
            />
            <Button kind="text" label="장소 없이 보관할게요" onPress={onKeep} />
        </>
    );
}

// onRetry 가 없으면 "다시 시도"를 숨긴다. 다시 저장하는 동안은 막는다.
function FailedActions({
    onRetry,
    retrying = false,
    onLater,
}: {
    onRetry?: () => void;
    retrying?: boolean;
    onLater: () => void;
}) {
    return (
        <>
            {onRetry != null && (
                <Button
                    kind="secondary"
                    label="다시 시도"
                    icon="refresh"
                    disabled={retrying}
                    onPress={onRetry}
                />
            )}
            <Button kind="text" label="나중에 할게요" onPress={onLater} />
        </>
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
