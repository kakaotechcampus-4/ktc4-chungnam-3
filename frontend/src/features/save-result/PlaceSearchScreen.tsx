// 02c 장소 직접 찾기 / 02d 결과 없음. 저장 결과 모달 안에 push 된다.
// 저장하면 모달 하나만 닫아 흐름을 끝낸다. 뒤로 가기는 02 로 간다. 검색은 목 데이터로 거른다.
// 08 "이 장소가 아니에요"에서 열면 바꾸기 모드다. 모달에 이 화면만 있고, 바꾸면 모달을 닫아 08 로 돌아간다.
import {
    type RouteProp,
    StackActions,
    useNavigation,
    useRoute,
} from "@react-navigation/native";
import { useMemo, useState } from "react";
import {
    KeyboardAvoidingView,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    placeDetailsMock,
    placeSearchMock,
    saveResultsMock,
} from "../../shared/api/mock";
import Button from "../../shared/ui/Button";
import NotFound from "../../shared/ui/NotFound";
import {
    colors,
    metrics,
    spacing,
    typography,
} from "../../shared/ui/theme";
import TopBar from "../../shared/ui/TopBar";
import PlaceOption from "./components/PlaceOption";
import SearchField from "./components/SearchField";
import { withRo } from "./particle";

type Place = { id: string; name: string; address: string };
type Saved = { source: { meta: string; title?: string } };
type Detail = { place: string; sourceTitle?: string };

// resultId 로 들어오면 저장 모드(02 · 02b · 04 에서), placeId 로 들어오면 바꾸기 모드(08 에서).
type Params = { resultId: string } | { placeId: string };

const places: readonly Place[] = placeSearchMock;
const sources: Readonly<Record<string, Saved>> = saveResultsMock;
const details: Readonly<Record<string, Detail>> = placeDetailsMock;

function search(query: string): readonly Place[] {
    const q = query.trim();
    if (!q) return [];
    return places.filter(
        (place) => place.name.includes(q) || place.address.includes(q),
    );
}

export default function PlaceSearchScreen() {
    const navigation = useNavigation();
    const route =
        useRoute<RouteProp<{ PlaceSearch: Params }, "PlaceSearch">>();
    const insets = useSafeAreaInsets();

    const [query, setQuery] = useState("");
    const results = useMemo(() => search(query), [query]);
    const [selectedId, setSelectedId] = useState<string>();

    const params = route.params;
    const replacing = "placeId" in params;
    const title = replacing
        ? details[params.placeId]?.sourceTitle
        : sources[params.resultId]?.source.title;
    const found = replacing
        ? details[params.placeId] != null
        : sources[params.resultId] != null;

    if (!found) {
        return (
            <NotFound
                topIcon="arrowLeft"
                heading={
                    replacing
                        ? "이 장소를 찾을 수 없어요"
                        : "이 저장물을 찾을 수 없어요"
                }
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

    // 검색어가 바뀌면 첫 결과를 다시 고른다.
    const changeQuery = (text: string) => {
        setQuery(text);
        setSelectedId(search(text)[0]?.id);
    };
    // 모달 하나만 닫는다(SaveResult). 목에서는 저장 · 보관 · 바꾸기를 성공으로 본다.
    // 바꾸기 모드는 중첩 스택에 PlaceSearch 만 있어 뒤로 가기도 모달을 닫고 08 로 돌아간다.
    const closeFlow = () => navigation.getParent()?.goBack();

    const selected = results.find((place) => place.id === selectedId);
    const noResult = query.trim().length > 0 && results.length === 0;
    // 바꾸기 모드의 결과 없음(02d)에는 하단 버튼이 없다.
    const showActions = selected != null || (noResult && !replacing);
    const verb = replacing ? "바꾸기" : "저장";

    return (
        <KeyboardAvoidingView behavior="padding" style={styles.screen}>
            <TopBar title="장소 직접 찾기" onBack={() => navigation.goBack()} />

            <View style={styles.search}>
                {title != null && (
                    <Text numberOfLines={1} style={styles.caption}>
                        “{title}”에 붙일 장소
                    </Text>
                )}
                <SearchField
                    value={query}
                    onChangeText={changeQuery}
                    autoFocus
                />
            </View>

            <ScrollView
                keyboardShouldPersistTaps="handled"
                contentContainerStyle={styles.content}
            >
                {results.length > 0 && (
                    <View style={styles.results} accessibilityRole="radiogroup">
                        {results.map((place) => (
                            <PlaceOption
                                key={place.id}
                                name={place.name}
                                address={place.address}
                                selected={place.id === selectedId}
                                onPress={() => setSelectedId(place.id)}
                            />
                        ))}
                    </View>
                )}
                {noResult && (
                    <View style={styles.message}>
                        <Text style={styles.heading}>찾은 곳이 없어요</Text>
                        <Text style={styles.body}>
                            가게 이름이나 동네 이름으로 찾으면 더 잘 나와요.
                        </Text>
                    </View>
                )}
            </ScrollView>

            {showActions && (
                <View
                    style={[
                        styles.actions,
                        { paddingBottom: insets.bottom + spacing.xl },
                    ]}
                >
                    {selected && (
                        <Button
                            kind="primary"
                            label={`${withRo(selected.name)} ${verb}`}
                            onPress={closeFlow}
                        />
                    )}
                    {noResult && !replacing && (
                        <Button
                            kind="text"
                            label="장소 없이 보관할게요"
                            onPress={closeFlow}
                        />
                    )}
                </View>
            )}
        </KeyboardAvoidingView>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
    search: {
        gap: spacing.md,
        paddingTop: spacing.xs,
        paddingHorizontal: spacing.lg,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    content: {
        flexGrow: 1,
    },
    results: {
        gap: spacing.sm,
        paddingTop: metrics.placeSearch.resultsPaddingTop,
        paddingHorizontal: spacing.lg,
    },
    message: {
        gap: spacing.sm,
        paddingTop: metrics.placeSearch.emptyPaddingTop,
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
    actions: {
        paddingHorizontal: spacing.lg,
    },
});
