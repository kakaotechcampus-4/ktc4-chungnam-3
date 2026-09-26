// 근처 탭. 지금 위치 근처의 저장물을 꺼내 보여준다. 저장물이 없으면 06 빈 상태.
import { useNavigation } from '@react-navigation/native';
import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MOCK_SCENARIO, nearbyEmptyMock, nearbyMock } from '../../shared/api/mock';
import Icon from '../../shared/ui/Icon';
import { colors, metrics, spacing, typography } from '../../shared/ui/theme';
import Chip from './components/Chip';
import MemoryCard from './components/MemoryCard';
import NearbyCard from './components/NearbyCard';
import NearbyEmpty from './components/NearbyEmpty';

// 카테고리 enum 확정 전까지 목 데이터 한정. 선택은 표시만 바꾸고 목록을 거르지 않는다.
const FILTERS = ['전체', '식당', '카페', '가볼 곳'] as const;

export default function ProposalScreen() {
  const navigation = useNavigation();
  const insets = useSafeAreaInsets();
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>('전체');

  const openDetail = (placeId: string) => navigation.navigate('ContentDetail', { placeId });

  if (MOCK_SCENARIO.nearbyEmpty) {
    return (
      <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom }}>
        <NearbyEmpty {...nearbyEmptyMock} />
      </ScrollView>
    );
  }

  const { area, nearby, memoryGroups } = nearbyMock;
  const count = nearby.length + memoryGroups.reduce((sum, group) => sum + group.length, 0);

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom }}>
      <View style={styles.intro}>
        <View style={styles.location}>
          <Icon name="pin" size={metrics.proposal.locationIconSize} />
          <Text style={styles.caption}>지금 {area}</Text>
        </View>
        <Text style={styles.heading}>잊고 있던 곳 {count}개를 찾았어요</Text>
        <Text style={styles.caption}>가까이 갈수록 사진에 색이 돌아와요</Text>
      </View>

      <View style={styles.filters}>
        {FILTERS.map((label) => (
          <Chip key={label} label={label} selected={label === filter} onPress={() => setFilter(label)} />
        ))}
      </View>

      <View style={styles.list}>
        <View>
          {nearby.map(({ id, detailId, ...card }) => (
            <NearbyCard key={id} {...card} onPress={() => openDetail(detailId)} />
          ))}
        </View>
        {memoryGroups.map((group) => (
          <View key={group[0].id} style={styles.group}>
            {group.map(({ id, ...card }) => (
              <MemoryCard key={id} {...card} />
            ))}
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  intro: {
    paddingTop: metrics.proposal.introPaddingTop,
    paddingHorizontal: spacing.lg,
    gap: metrics.proposal.introGap,
  },
  location: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
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
    flexDirection: 'row',
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
