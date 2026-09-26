// 저장한 원본 영상. 썸네일 + (배지) + 메타 + 제목 또는 링크 + 계정.
import { StyleSheet, Text, View } from 'react-native';

import Badge, { type BadgeStatus } from '../../../shared/ui/Badge';
import Thumb from '../../../shared/ui/Thumb';
import { colors, metrics, spacing, typography } from '../../../shared/ui/theme';

export type SourceHeaderProps = {
  thumbUri?: string;
  status?: BadgeStatus;
  meta: string;
  title?: string;
  url?: string;
  handle?: string;
};

export default function SourceHeader({ thumbUri, status, meta, title, url, handle }: SourceHeaderProps) {
  return (
    <View style={styles.row}>
      <Thumb fade="recent" uri={thumbUri} style={styles.thumb} />
      <View style={styles.info}>
        {status && <Badge status={status} />}
        <Text style={styles.caption}>{meta}</Text>
        {title != null ? (
          <Text style={styles.title}>“{title}”</Text>
        ) : (
          <Text numberOfLines={1} style={styles.title}>
            {url}
          </Text>
        )}
        {handle != null && <Text style={styles.caption}>{handle}</Text>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: metrics.saveResult.sourceGap,
    paddingTop: spacing.sm,
    paddingHorizontal: spacing.lg,
  },
  thumb: {
    width: metrics.saveResult.thumbWidth,
  },
  info: {
    flex: 1,
    gap: metrics.saveResult.infoGap,
  },
  caption: {
    ...typography.captionMeta,
    color: colors.text.secondary,
  },
  title: {
    ...typography.bodyDefault,
    color: colors.text.primary,
  },
});
