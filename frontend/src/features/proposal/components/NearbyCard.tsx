// 지금 가까운 저장물 카드. Figma C/Card/Nearby. 자두색 now pill 과 그림자로 다시 꺼낸 순간을 표시한다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import Thumb, { type ThumbFade } from '../../../shared/ui/Thumb';
import { colors, effects, metrics, radius, spacing, typography } from '../../../shared/ui/theme';

export type NearbyCardProps = {
  now: string;
  time: string;
  place: string;
  summary: string;
  tags: readonly string[];
  thumbUri?: string;
  fade: ThumbFade;
  onPress?: () => void;
};

export default function NearbyCard({ now, time, place, summary, tags, thumbUri, fade, onPress }: NearbyCardProps) {
  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      disabled={!onPress}
      onPress={onPress}
      style={styles.card}
    >
      <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
      <View style={styles.content}>
        <View style={styles.now}>
          <Text numberOfLines={1} style={styles.nowLabel}>
            {now}
          </Text>
        </View>
        <View style={styles.head}>
          <Text numberOfLines={1} style={styles.time}>
            {time}
          </Text>
          <Text numberOfLines={1} style={styles.place}>
            {place}
          </Text>
        </View>
        <Text numberOfLines={metrics.nearbyCard.summaryMaxLines} style={styles.summary}>
          {summary}
        </Text>
        <Text numberOfLines={1} style={styles.tags}>
          {tags.map((tag) => `#${tag}`).join('  ')}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: radius.lg,
    backgroundColor: colors.bg.surface,
    boxShadow: effects.elevationLow,
  },
  thumb: {
    width: metrics.nearbyCard.thumbWidth,
  },
  content: {
    flex: 1,
    gap: spacing.sm,
  },
  now: {
    alignSelf: 'flex-start',
    paddingHorizontal: metrics.nearbyCard.nowPaddingHorizontal,
    paddingVertical: metrics.nearbyCard.nowPaddingVertical,
    borderRadius: radius.full,
    backgroundColor: colors.brand.primary,
  },
  nowLabel: {
    ...typography.captionMeta,
    color: colors.text.onPrimary,
  },
  head: {
    gap: metrics.nearbyCard.headGap,
  },
  time: {
    ...typography.headingScreen,
    color: colors.text.primary,
  },
  place: {
    ...typography.titleCard,
    color: colors.text.primary,
  },
  summary: {
    ...typography.bodyDefault,
    color: colors.text.onSubtle,
  },
  tags: {
    ...typography.captionMeta,
    color: colors.text.secondary,
  },
});
