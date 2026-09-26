// 03 분석 중 자리표시. Figma 에 움직임이 없어 정지 상태로 둔다.
import { StyleSheet, View } from 'react-native';

import { colors, metrics, radius, spacing } from '../../../shared/ui/theme';

export default function AnalyzingSkeleton() {
  return (
    <View style={styles.list}>
      {metrics.skeleton.cards.map((bars, cardIndex) => (
        <View key={cardIndex} style={styles.card}>
          {bars.map((bar, barIndex) => (
            <View
              key={barIndex}
              style={[styles.bar, { width: bar.width, height: bar.height, borderRadius: bar.height / 2 }]}
            />
          ))}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: {
    gap: spacing.sm,
    paddingTop: spacing.lg,
    paddingHorizontal: spacing.lg,
  },
  card: {
    gap: spacing.sm,
    padding: metrics.skeleton.padding,
    borderRadius: radius.md,
    backgroundColor: colors.bg.surface,
  },
  bar: {
    backgroundColor: colors.bg.subtle,
  },
});
