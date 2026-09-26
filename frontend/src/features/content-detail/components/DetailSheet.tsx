// 08 장소 상세 시트 내용. 핸들은 모양만 있고 끌 수 없다.
// 원본 영상·길 안내·"이 장소가 아니에요" 는 아직 동작하지 않는다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import Button from '../../../shared/ui/Button';
import Icon from '../../../shared/ui/Icon';
import Thumb, { type ThumbFade } from '../../../shared/ui/Thumb';
import { colors, metrics, radius, size, spacing, stroke, typography } from '../../../shared/ui/theme';

export type DetailSheetProps = {
  time: string;
  place: string;
  meta: string;
  thumbUri?: string;
  fade: ThumbFade;
  note: string;
  tags: readonly string[];
  sourceMeta: string;
};

export default function DetailSheet({ time, place, meta, thumbUri, fade, note, tags, sourceMeta }: DetailSheetProps) {
  return (
    <>
      <View style={styles.handleRow}>
        <View style={styles.handle} />
      </View>

      <View style={styles.head}>
        <Thumb fade={fade} uri={thumbUri} style={styles.thumb} />
        <View style={styles.info}>
          <Text numberOfLines={1} style={styles.time}>
            {time}
          </Text>
          <Text numberOfLines={1} style={styles.place}>
            {place}
          </Text>
          <Text numberOfLines={1} style={styles.caption}>
            {meta}
          </Text>
        </View>
      </View>

      <View style={styles.note}>
        <Text style={styles.caption}>영상에서 저장해둔 것</Text>
        <Text style={styles.noteBody}>{note}</Text>
        <View style={styles.tags}>
          {tags.map((tag) => (
            <View key={tag} style={styles.tag}>
              <Text style={styles.tagLabel}>#{tag}</Text>
            </View>
          ))}
        </View>
      </View>

      {/* 동작이 연결되면 disabled 를 뺀다. */}
      <Pressable accessibilityRole="link" accessibilityState={{ disabled: true }} style={styles.source}>
        <View style={styles.sourceText}>
          <Text numberOfLines={1} style={styles.sourceTitle}>
            원본 영상 다시 보기
          </Text>
          <Text numberOfLines={1} style={styles.caption}>
            {sourceMeta}
          </Text>
        </View>
        <Icon name="chevronRight" size={size.iconSm} />
      </Pressable>

      <View style={styles.spacer} />

      <View style={styles.actions}>
        <Button kind="primary" label="길 안내 시작" />
        <Button kind="text" label="이 장소가 아니에요" />
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  handleRow: {
    alignItems: 'center',
  },
  handle: {
    width: metrics.contentDetail.handleWidth,
    height: metrics.contentDetail.handleHeight,
    borderRadius: metrics.contentDetail.handleHeight / 2,
    backgroundColor: colors.bg.placeholder,
  },
  head: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: metrics.contentDetail.headGap,
  },
  thumb: {
    width: metrics.contentDetail.thumbWidth,
  },
  info: {
    flex: 1,
    gap: metrics.contentDetail.infoGap,
  },
  time: {
    ...typography.headingScreen,
    color: colors.text.primary,
  },
  place: {
    ...typography.titleCard,
    color: colors.text.primary,
  },
  caption: {
    ...typography.captionMeta,
    color: colors.text.secondary,
  },
  note: {
    gap: metrics.contentDetail.noteGap,
  },
  noteBody: {
    ...typography.bodyDefault,
    color: colors.text.primary,
  },
  tags: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: metrics.contentDetail.tagGap,
  },
  tag: {
    paddingHorizontal: spacing.sm,
    paddingVertical: metrics.contentDetail.tagPaddingVertical,
    borderRadius: radius.sm,
    backgroundColor: colors.bg.subtle,
  },
  tagLabel: {
    ...typography.captionMeta,
    color: colors.text.onSubtle,
  },
  source: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingLeft: metrics.contentDetail.sourcePaddingLeft,
    paddingRight: spacing.md,
    paddingVertical: spacing.md,
    borderWidth: stroke.thin,
    borderColor: colors.border.default,
    borderRadius: radius.md,
  },
  sourceText: {
    flex: 1,
    gap: metrics.contentDetail.sourceTextGap,
  },
  sourceTitle: {
    ...typography.labelButton,
    color: colors.text.primary,
  },
  spacer: {
    flex: 1,
  },
  actions: {
    gap: spacing.xs,
  },
});
