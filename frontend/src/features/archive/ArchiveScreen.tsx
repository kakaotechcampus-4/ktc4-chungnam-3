// 기억 탭. 저장물을 시간순 앨범으로 보여준다. 오래 잊은 것일수록 바래 있다.
// 헤더는 스크롤 영역 밖에 고정한다. 하단 inset 은 하단 바가 맡는다.
// 데이터는 저장물 목록 API(useArchivePages)다. 섹션 · 바램 · 이름은 albumSections 가 정한다.
// 첫 로딩은 헤더만 보인다. 처음 읽기가 실패하면 캡션 자리에 다시 불러오기 문구를 보인다. 목록이 비면 07b.
// 끝 근처(한 화면 이내)까지 내리면 다음 페이지를 읽는다. "모두 N곳"은 마지막 페이지까지 읽었을 때만 보인다.
// 미확정 셀은 저장 결과(02~05), ready 셀은 장소 상세(08)를 personalSaveId 로 연다(mockKey 는 임시, albumSections).
import { useNavigation } from "@react-navigation/native";
import {
    type NativeScrollEvent,
    type NativeSyntheticEvent,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";

import ScreenHeader from "../../shared/ui/ScreenHeader";
import { colors, spacing, typography } from "../../shared/ui/theme";
import { type AlbumItem, toAlbumSections } from "./albumSections";
import AlbumCell from "./components/AlbumCell";
import ArchiveEmpty from "./components/ArchiveEmpty";
import { useArchivePages } from "./useArchivePages";

const COLUMNS = 3;
// 끝 근처 확인은 자주 할 필요가 없다.
const SCROLL_CHECK_MS = 200;

// 마지막 줄이 모자라면 빈 자리(null)로 채워 셀이 1/3 폭을 유지하게 한다.
function toRows(items: readonly AlbumItem[]): (AlbumItem | null)[][] {
    const rows: (AlbumItem | null)[][] = [];
    for (let i = 0; i < items.length; i += COLUMNS) {
        const row: (AlbumItem | null)[] = items.slice(i, i + COLUMNS);
        while (row.length < COLUMNS) row.push(null);
        rows.push(row);
    }
    return rows;
}

// 남은 내용이 한 화면 이내면 끝 근처로 본다.
function nearEnd({ nativeEvent }: NativeSyntheticEvent<NativeScrollEvent>) {
    const { layoutMeasurement, contentOffset, contentSize } = nativeEvent;
    return contentOffset.y + layoutMeasurement.height * 2 >= contentSize.height;
}

export default function ArchiveScreen() {
    const navigation = useNavigation();
    const { status, items, hasMore, loadMore, reload } = useArchivePages();
    const header = (
        <ScreenHeader
            title="기억"
            onSettingsPress={() => navigation.navigate("Settings")}
        />
    );

    if (status === "loading") {
        return <View style={styles.screen}>{header}</View>;
    }

    if (status === "failed") {
        return (
            <View style={styles.screen}>
                {header}
                <Pressable
                    accessibilityRole="button"
                    onPress={reload}
                    style={styles.intro}
                >
                    <Text style={styles.caption}>
                        확인하지 못했어요. 눌러서 다시 불러와요
                    </Text>
                </Pressable>
            </View>
        );
    }

    if (items.length === 0) {
        return (
            <View style={styles.screen}>
                {header}
                <ScrollView contentContainerStyle={styles.content}>
                    <ArchiveEmpty />
                </ScrollView>
            </View>
        );
    }

    const sections = toAlbumSections(items, new Date());

    const open = ({ personalSaveId, open: { screen, mockKey } }: AlbumItem) =>
        screen === "result"
            ? navigation.navigate("SaveResult", {
                  screen: "Result",
                  params: { personalSaveId, mockKey },
              })
            : navigation.navigate("ContentDetail", { personalSaveId, mockKey });

    return (
        <View style={styles.screen}>
            {header}
            <ScrollView
                contentContainerStyle={styles.content}
                scrollEventThrottle={SCROLL_CHECK_MS}
                onScroll={(event) => {
                    if (hasMore && nearEnd(event)) loadMore();
                }}
            >
                <View style={styles.intro}>
                    <Text style={styles.caption}>
                        {hasMore
                            ? "오래 잊은 것일수록 바래 있어요"
                            : `모두 ${items.length}곳 · 오래 잊은 것일수록 바래 있어요`}
                    </Text>
                </View>

                <View style={styles.album}>
                    {sections.map((section) => (
                        <View key={section.key} style={styles.section}>
                            <Text style={styles.sectionTitle}>
                                {section.title}
                            </Text>
                            <View style={styles.grid}>
                                {toRows(section.items).map((row) => (
                                    <View
                                        key={row[0]?.personalSaveId}
                                        style={styles.row}
                                    >
                                        {row.map((item, index) =>
                                            item ? (
                                                <AlbumCell
                                                    key={item.personalSaveId}
                                                    label={item.label}
                                                    thumbUri={item.thumbUri}
                                                    fade={item.fade}
                                                    status={item.status}
                                                    onPress={() => open(item)}
                                                />
                                            ) : (
                                                <View
                                                    key={`empty-${index}`}
                                                    style={styles.emptyCell}
                                                />
                                            ),
                                        )}
                                    </View>
                                ))}
                            </View>
                        </View>
                    ))}
                </View>
            </ScrollView>
        </View>
    );
}

const styles = StyleSheet.create({
    screen: {
        flex: 1,
    },
    // 목록 끝이 하단 바에 붙지 않게 한다. Figma 는 목록이 잘려 있어 값이 없다.
    content: {
        paddingBottom: spacing.lg,
    },
    intro: {
        paddingTop: spacing.lg,
        paddingHorizontal: spacing.lg,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    album: {
        gap: spacing.xl,
        paddingTop: spacing.lg,
        paddingHorizontal: spacing.lg,
    },
    section: {
        gap: spacing.md,
    },
    sectionTitle: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    // 줄 사이 space/md, 칸 사이 space/sm (Figma 2061:910).
    grid: {
        gap: spacing.md,
    },
    row: {
        flexDirection: "row",
        gap: spacing.sm,
    },
    emptyCell: {
        flex: 1,
    },
});
