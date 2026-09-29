// 기억 탭. 저장물을 시간순 앨범으로 보여준다. 오래 잊은 것일수록 바래 있다.
// 헤더는 스크롤 영역 밖에 고정한다. 하단 inset 은 하단 바가 맡는다.
import { useNavigation } from "@react-navigation/native";
import { ScrollView, StyleSheet, Text, View } from "react-native";

import { archiveMock, MOCK_SCENARIO } from "../../shared/api/mock";
import type { BadgeStatus } from "../../shared/ui/Badge";
import ScreenHeader from "../../shared/ui/ScreenHeader";
import type { ThumbFade } from "../../shared/ui/Thumb";
import { colors, spacing, typography } from "../../shared/ui/theme";
import AlbumCell from "./components/AlbumCell";
import ArchiveEmpty from "./components/ArchiveEmpty";

const COLUMNS = 3;

type ArchiveItem = {
    id: string;
    placeName?: string;
    videoTitle?: string;
    thumbUri?: string;
    fade: ThumbFade;
    status?: BadgeStatus;
    resultId?: string;
    detailId?: string;
};

type ArchiveSection = {
    title: string;
    items: readonly ArchiveItem[];
};

// 마지막 줄이 모자라면 빈 자리(null)로 채워 셀이 1/3 폭을 유지하게 한다.
function toRows(items: readonly ArchiveItem[]): (ArchiveItem | null)[][] {
    const rows: (ArchiveItem | null)[][] = [];
    for (let i = 0; i < items.length; i += COLUMNS) {
        const row: (ArchiveItem | null)[] = items.slice(i, i + COLUMNS);
        while (row.length < COLUMNS) row.push(null);
        rows.push(row);
    }
    return rows;
}

export default function ArchiveScreen() {
    const navigation = useNavigation();
    const header = (
        <ScreenHeader
            title="기억"
            onSettingsPress={() => navigation.navigate("Settings")}
        />
    );

    if (MOCK_SCENARIO.archiveEmpty) {
        return (
            <View style={styles.screen}>
                {header}
                <ScrollView contentContainerStyle={styles.content}>
                    <ArchiveEmpty />
                </ScrollView>
            </View>
        );
    }

    const sections: readonly ArchiveSection[] = archiveMock.sections;
    const count = sections.reduce(
        (sum, section) => sum + section.items.length,
        0,
    );

    const pressHandler = ({ resultId, detailId }: ArchiveItem) => {
        if (resultId)
            return () => navigation.navigate("SaveResult", { resultId });
        if (detailId)
            return () =>
                navigation.navigate("ContentDetail", { placeId: detailId });
        return undefined;
    };

    return (
        <View style={styles.screen}>
            {header}
            <ScrollView contentContainerStyle={styles.content}>
                <View style={styles.intro}>
                    <Text style={styles.caption}>
                        모두 {count}곳 · 오래 잊은 것일수록 바래 있어요
                    </Text>
                </View>

                <View style={styles.album}>
                    {sections.map((section) => (
                        <View key={section.title} style={styles.section}>
                            <Text style={styles.sectionTitle}>{section.title}</Text>
                            <View style={styles.grid}>
                                {toRows(section.items).map((row) => (
                                    <View key={row[0]?.id} style={styles.row}>
                                        {row.map((item, index) =>
                                            item ? (
                                                <AlbumCell
                                                    key={item.id}
                                                    placeName={item.placeName}
                                                    videoTitle={item.videoTitle}
                                                    thumbUri={item.thumbUri}
                                                    fade={item.fade}
                                                    status={item.status}
                                                    onPress={pressHandler(item)}
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
