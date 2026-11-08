// 기억 탭. 저장물을 시간순 앨범으로 보여준다. 오래 잊은 것일수록 바래 있다.
// 헤더는 스크롤 영역 밖에 고정한다. 하단 inset 은 하단 바가 맡는다.
// 데이터는 저장물 목록 API(useArchivePages)다. 섹션 · 바램 · 이름은 albumSections 가 정한다.
// 첫 로딩은 헤더만 보인다. 처음 읽기가 실패하면 캡션 자리에 다시 불러오기 문구를 보인다. 목록이 비면 07b.
// 끝 근처(한 화면 이내)까지 내리면 다음 페이지를 읽는다. "모두 N개"(N 은 저장물 수)는 마지막 페이지까지 읽었을 때만 보인다.
// 셀 이름이 장소명이라 "곳"이면 장소 수로 오해되어 단위는 "개"다(디자인 결정).
// 미확정 셀은 저장 결과(02~05), ready 셀은 장소 상세(08)를 personalSaveId 로 연다.
// 셀을 길게 누르면 지우기 확인 시트(07c)를 띄운다. 시트는 모든 분기에 그려 마지막 하나를 지워 07b 로 바뀌어도 닫힘까지 이어진다.
import { useNavigation } from "@react-navigation/native";
import { type ReactNode, useRef, useState } from "react";
import {
    type NativeScrollEvent,
    type NativeSyntheticEvent,
    Pressable,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";

import { ApiError } from "../../shared/api/client";
import { deletePersonalSave } from "../../shared/api/personalSaves";
import type { ModalSheetStatus } from "../../shared/ui/ModalSheet";
import ScreenHeader from "../../shared/ui/ScreenHeader";
import { colors, spacing, typography } from "../../shared/ui/theme";
import { type AlbumItem, toAlbumSections } from "./albumSections";
import AlbumCell from "./components/AlbumCell";
import ArchiveEmpty from "./components/ArchiveEmpty";
import DeleteSaveSheet from "./components/DeleteSaveSheet";
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

// 07c 지우기 확인 시트 상태. 지우는 중에 다시 눌러도 한 번만 보낸다.
// 성공(204)과 404(이미 없음)는 지운 것으로 보고 시트 닫기와 목록에서 빼기(onDeleted)를 함께 한다.
// 그 밖의 실패는 시트를 두고 오류 문구를 보인다. 다시 누르면 다시 보낸다. 대상은 시트가 다 닫힐 때 지운다.
function useDeleteSheet(onDeleted: (personalSaveId: string) => void) {
    const [visible, setVisible] = useState(false);
    const [status, setStatus] = useState<ModalSheetStatus>("default");
    const [target, setTarget] = useState<AlbumItem | null>(null);
    const deleting = useRef(false);

    const open = (item: AlbumItem) => {
        setTarget(item);
        setStatus("default");
        setVisible(true);
    };
    const close = () => setVisible(false);
    const onDismiss = () => {
        setVisible(false);
        setStatus("default");
        setTarget(null);
    };
    const submit = async () => {
        if (deleting.current || target == null) return;
        const { personalSaveId } = target;
        deleting.current = true;
        setStatus("saving");
        const deleted = await deletePersonalSave(personalSaveId).then(
            () => true,
            (error: unknown) =>
                error instanceof ApiError && error.status === 404,
        );
        deleting.current = false;
        if (!deleted) {
            setStatus("error");
            return;
        }
        setVisible(false);
        onDeleted(personalSaveId);
    };
    return { visible, status, target, open, close, onDismiss, submit };
}

export default function ArchiveScreen() {
    const navigation = useNavigation();
    const { status, items, hasMore, loadMore, reload, remove } =
        useArchivePages();
    const deleteSheet = useDeleteSheet(remove);
    const header = (
        <ScreenHeader
            title="기억"
            onSettingsPress={() => navigation.navigate("Settings")}
        />
    );
    // 화면 뒤에 시트를 함께 그린다. 시트는 앱 루트 위에 떠서 화면 배치에 끼어들지 않는다.
    const withSheet = (screen: ReactNode) => (
        <>
            {screen}
            <DeleteSaveSheet
                visible={deleteSheet.visible}
                status={deleteSheet.status}
                target={deleteSheet.target}
                onConfirm={deleteSheet.submit}
                onCancel={deleteSheet.close}
                onDismiss={deleteSheet.onDismiss}
            />
        </>
    );

    if (status === "loading") {
        return withSheet(<View style={styles.screen}>{header}</View>);
    }

    if (status === "failed") {
        return withSheet(
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
            </View>,
        );
    }

    if (items.length === 0) {
        return withSheet(
            <View style={styles.screen}>
                {header}
                <ScrollView contentContainerStyle={styles.content}>
                    <ArchiveEmpty />
                </ScrollView>
            </View>,
        );
    }

    const sections = toAlbumSections(items, new Date());

    const open = ({ personalSaveId, opens }: AlbumItem) =>
        opens === "result"
            ? navigation.navigate("SaveResult", {
                  screen: "Result",
                  params: { personalSaveId },
              })
            : navigation.navigate("ContentDetail", { personalSaveId });

    return withSheet(
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
                            : `모두 ${items.length}개 · 오래 잊은 것일수록 바래 있어요`}
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
                                                    labelSuffix={
                                                        item.labelSuffix
                                                    }
                                                    thumbUri={item.thumbUri}
                                                    fade={item.fade}
                                                    status={item.status}
                                                    onPress={() => open(item)}
                                                    onLongPress={() =>
                                                        deleteSheet.open(item)
                                                    }
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
        </View>,
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
