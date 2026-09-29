// 근처 탭. 지금 위치 근처의 저장물을 꺼내 보여준다. 저장물이 없으면 06 빈 상태.
// 헤더는 스크롤 영역 밖에 고정한다. 하단 inset 은 하단 바가 맡는다.
import { useNavigation } from "@react-navigation/native";
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";

import {
    MOCK_SCENARIO,
    nearbyEmptyMock,
    nearbyMock,
} from "../../shared/api/mock";
import ScreenHeader from "../../shared/ui/ScreenHeader";
import { colors, metrics, spacing, typography } from "../../shared/ui/theme";
import Chip from "./components/Chip";
import MemoryCard from "./components/MemoryCard";
import NearbyCard from "./components/NearbyCard";
import NearbyEmpty from "./components/NearbyEmpty";

// 카테고리 enum 확정 전까지 목 데이터 한정. 선택은 표시만 바꾸고 목록을 거르지 않는다.
const FILTERS = ["전체", "식당", "카페", "가볼 곳"] as const;

export default function ProposalScreen() {
    const navigation = useNavigation();
    const [filter, setFilter] = useState<(typeof FILTERS)[number]>("전체");

    const openDetail = (placeId: string) =>
        navigation.navigate("ContentDetail", { placeId });
    const openSettings = () => navigation.navigate("Settings");

    if (MOCK_SCENARIO.nearbyEmpty) {
        return (
            <View style={styles.screen}>
                <ScreenHeader
                    location={nearbyEmptyMock.area}
                    onSettingsPress={openSettings}
                />
                <ScrollView contentContainerStyle={styles.content}>
                    <NearbyEmpty {...nearbyEmptyMock} />
                </ScrollView>
            </View>
        );
    }

    // 제목 숫자는 카드 수가 아니라 근처 전체 수(total)다. 필터 칩과 무관하다.
    const { area, total, nearby, memoryGroups } = nearbyMock;

    return (
        <View style={styles.screen}>
            <ScreenHeader location={area} onSettingsPress={openSettings} />
            <ScrollView contentContainerStyle={styles.content}>
                <View style={styles.intro}>
                    <Text style={styles.heading}>
                        잊고 있던 곳 {total}개를 찾았어요
                    </Text>
                    <Text style={styles.caption}>
                        가까이 갈수록 사진에 색이 돌아와요
                    </Text>
                </View>

                <View style={styles.filters}>
                    {FILTERS.map((label) => (
                        <Chip
                            key={label}
                            label={label}
                            selected={label === filter}
                            onPress={() => setFilter(label)}
                        />
                    ))}
                </View>

                <View style={styles.list}>
                    <View>
                        {nearby.map(({ id, detailId, ...card }) => (
                            <NearbyCard
                                key={id}
                                {...card}
                                onPress={() => openDetail(detailId)}
                            />
                        ))}
                    </View>
                    {memoryGroups.map((group) => (
                        <View key={group[0].id} style={styles.group}>
                            {group.map((item) => {
                                const { id, ...card } = item;
                                const detailId =
                                    "detailId" in item
                                        ? item.detailId
                                        : undefined;
                                return (
                                    <MemoryCard
                                        key={id}
                                        {...card}
                                        onPress={
                                            detailId
                                                ? () => openDetail(detailId)
                                                : undefined
                                        }
                                    />
                                );
                            })}
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
        paddingTop: metrics.proposal.introPaddingTop,
        paddingHorizontal: spacing.lg,
        gap: metrics.proposal.introGap,
    },
    caption: {
        ...typography.captionMeta,
        color: colors.text.secondary,
    },
    heading: {
        ...typography.headingScreen,
        color: colors.text.primary,
    },
    filters: {
        flexDirection: "row",
        gap: spacing.sm,
        paddingTop: spacing.lg,
        paddingBottom: spacing.xs,
        paddingHorizontal: spacing.lg,
    },
    list: {
        gap: spacing.xl,
        paddingTop: metrics.proposal.listPaddingTop,
        paddingHorizontal: spacing.lg,
    },
    group: {
        gap: spacing.md,
    },
});
