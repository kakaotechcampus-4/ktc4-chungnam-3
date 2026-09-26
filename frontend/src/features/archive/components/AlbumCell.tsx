// 앨범 셀. 썸네일 + 상태 배지 + 한 줄 라벨. 폭은 부모가 정한다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import Badge, { type BadgeStatus } from '../../../shared/ui/Badge';
import Thumb, { type ThumbFade } from '../../../shared/ui/Thumb';
import { colors, metrics, typography } from '../../../shared/ui/theme';

export type AlbumCellProps = {
  label: string;
  thumbUri?: string;
  fade: ThumbFade;
  status?: BadgeStatus;
  onPress?: () => void;
};

export default function AlbumCell({ label, thumbUri, fade, status, onPress }: AlbumCellProps) {
  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      disabled={!onPress}
      onPress={onPress}
      style={styles.cell}
    >
      <View>
        <Thumb fade={fade} uri={thumbUri} />
        {status && <Badge status={status} style={styles.badge} />}
      </View>
      <Text numberOfLines={1} style={styles.label}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  cell: {
    flex: 1,
    gap: metrics.albumCell.labelGap,
  },
  badge: {
    position: 'absolute',
    top: metrics.albumCell.badgeInset,
    left: metrics.albumCell.badgeInset,
  },
  label: {
    ...typography.captionMeta,
    color: colors.text.onSubtle,
  },
});
