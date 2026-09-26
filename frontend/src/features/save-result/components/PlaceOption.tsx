// 02 장소 후보 한 개. 선택되면 1.5 테두리 + Check.
// 선택 안 된 후보는 테두리가 얇은 만큼 padding 을 늘려 두 상태 크기를 맞춘다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import Icon from '../../../shared/ui/Icon';
import { colors, metrics, radius, spacing, stroke, typography } from '../../../shared/ui/theme';

const STROKE_DIFF = stroke.medium - stroke.thin;

type Props = {
  name: string;
  address: string;
  selected: boolean;
  onPress: () => void;
};

export default function PlaceOption({ name, address, selected, onPress }: Props) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={[styles.option, selected ? styles.selected : styles.unselected]}
    >
      <View style={styles.text}>
        <Text numberOfLines={1} style={styles.name}>
          {name}
        </Text>
        <Text numberOfLines={1} style={styles.address}>
          {address}
        </Text>
      </View>
      {selected && <Icon name="check" size={metrics.placeOption.checkSize} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.bg.surface,
  },
  selected: {
    paddingHorizontal: metrics.placeOption.paddingHorizontal,
    paddingVertical: metrics.placeOption.paddingVertical,
    borderWidth: stroke.medium,
    borderColor: colors.text.primary,
  },
  unselected: {
    paddingHorizontal: metrics.placeOption.paddingHorizontal + STROKE_DIFF,
    paddingVertical: metrics.placeOption.paddingVertical + STROKE_DIFF,
    borderWidth: stroke.thin,
    borderColor: colors.border.default,
  },
  text: {
    flex: 1,
    gap: metrics.placeOption.textGap,
  },
  name: {
    ...typography.titleCard,
    color: colors.text.primary,
  },
  address: {
    ...typography.captionMeta,
    color: colors.text.secondary,
  },
});
