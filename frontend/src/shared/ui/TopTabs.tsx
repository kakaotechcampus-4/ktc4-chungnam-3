// 상단 "근처 / 전체 기억" 탭. Figma C/TopTabs. 내비게이션과 무관하게 active·onChange 만 받는다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, metrics, spacing, typography } from './theme';

export type TopTabKey = 'nearby' | 'archive';

const TABS: { key: TopTabKey; label: string }[] = [
  { key: 'nearby', label: '근처' },
  { key: 'archive', label: '전체 기억' },
];

type Props = {
  active: TopTabKey;
  onChange: (key: TopTabKey) => void;
};

export default function TopTabs({ active, onChange }: Props) {
  return (
    <View style={styles.row} accessibilityRole="tablist">
      {TABS.map(({ key, label }) => {
        const selected = key === active;
        return (
          <Pressable
            key={key}
            style={styles.tab}
            hitSlop={metrics.topTabs.hitSlop}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => onChange(key)}
          >
            <Text style={[styles.label, selected ? styles.labelActive : styles.labelInactive]}>
              {label}
            </Text>
            {selected && <View style={styles.underline} />}
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.lg,
  },
  tab: {
    alignItems: 'center',
    gap: metrics.topTabs.labelGap,
  },
  label: typography.labelButton,
  labelActive: {
    color: colors.text.primary,
  },
  labelInactive: {
    color: colors.text.secondary,
  },
  underline: {
    width: metrics.topTabs.underlineWidth,
    height: metrics.topTabs.underlineHeight,
    backgroundColor: colors.text.primary,
  },
});
