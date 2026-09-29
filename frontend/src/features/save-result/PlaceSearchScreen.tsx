// 02c 장소 직접 찾기 / 02d 결과 없음. 저장 결과 모달 안에 push 된다.
// 저장하면 모달 하나만 닫아 흐름을 끝낸다. 뒤로 가기는 02 로 간다. 검색은 목 데이터로 거른다.
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

import { placeSearchMock, saveResultsMock } from "../../shared/api/mock";
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

const places: readonly Place[] = placeSearchMock;
type Saved = { source: { meta: string; title?: string } };

const sources: Readonly<Record<string, Saved>> = saveResultsMock;

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
        useRoute<
            RouteProp<{ PlaceSearch: { resultId: string } }, "PlaceSearch">
        >();
    const insets = useSafeAreaInsets();

    const [query, setQuery] = useState("");
    const results = useMemo(() => search(query), [query]);
    const [selectedId, setSelectedId] = useState<string>();

    const saved = sources[route.params.resultId];
    if (!saved) {
        return (
            <NotFound
                topIcon="arrowLeft"
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

    // 검색어가 바뀌면 첫 결과를 다시 고른다.
    const changeQuery = (text: string) => {
        setQuery(text);
        setSelectedId(search(text)[0]?.id);
    };
    // 모달 하나만 닫는다(SaveResult). 목에서는 저장 · 보관을 성공으로 본다.
    const closeFlow = () => navigation.getParent()?.goBack();

    const selected = results.find((place) => place.id === selectedId);
    const noResult = query.trim().length > 0 && results.length === 0;
    const title = saved.source.title;

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

            {(selected || noResult) && (
                <View
                    style={[
                        styles.actions,
                        { paddingBottom: insets.bottom + spacing.xl },
                    ]}
                >
                    {selected && (
                        <Button
                            kind="primary"
                            label={`${withRo(selected.name)} 저장`}
                            onPress={closeFlow}
                        />
                    )}
                    {noResult && (
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
