// 06 근처 빈 상태. 가장 가까운 기억을 안내하고, 바랜 썸네일 스택을 보여준다.
import { Pressable, StyleSheet, Text, View } from 'react-native';

import Icon from '../../../shared/ui/Icon';
import Thumb from '../../../shared/ui/Thumb';
import { colors, metrics, spacing, typography } from '../../../shared/ui/theme';

export type NearbyEmptyProps = {
  area: string;
  nearestDistance: string;
  nearestArea: string;
  stackThumbUris: readonly string[];
};

export default function NearbyEmpty({ area, nearestDistance, nearestArea, stackThumbUris }: NearbyEmptyProps) {
  return (
    <View>
      <View style={styles.intro}>
        <View style={styles.location}>
          <Icon name="pin" size={metrics.proposal.locationIconSize} />
          <Text style={styles.caption}>지금 {area}</Text>
        </View>
        <Text style={styles.heading}>여기선 저장해둔 곳이 없어요</Text>
        <Text style={styles.body}>
          가장 가까운 기억은 {nearestDistance} 떨어진 {nearestArea}에 있어요.
        </Text>
        <Pressable accessibilityRole="link" hitSlop={metrics.nearbyEmpty.linkHitSlop} style={styles.link}>
          <Text style={styles.linkLabel}>{nearestArea} 기억 보기</Text>
          <Icon name="chevronRight" size={metrics.nearbyEmpty.linkIconSize} />
        </Pressable>
      </View>

      <View style={styles.stack}>
        {metrics.nearbyEmpty.stack.map((item, index) => (
          <Thumb
            key={item.rotate}
            fade="months"
            uri={stackThumbUris[index]}
            style={[
              styles.stackThumb,
              {
                top: item.top,
                marginLeft: item.dx - metrics.nearbyEmpty.stackThumbWidth / 2,
                transform: [{ rotate: item.rotate }],
              },
            ]}
          />
        ))}
      </View>

      <View style={styles.footer}>
        <Text style={[styles.caption, styles.centered]}>저장한 곳 근처에 가면 그때 조용히 꺼내드릴게요</Text>
      </View>
    </View>
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
  centered: {
    textAlign: 'center',
  },
  heading: {
    ...typography.headingScreen,
    color: colors.text.primary,
  },
  body: {
    ...typography.bodyDefault,
    color: colors.text.secondary,
  },
  link: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    gap: metrics.nearbyEmpty.linkGap,
    paddingTop: spacing.sm,
  },
  linkLabel: {
    ...typography.labelButton,
    color: colors.text.onSubtle,
  },
  stack: {
    height: metrics.nearbyEmpty.stackHeight,
    overflow: 'hidden',
  },
  stackThumb: {
    position: 'absolute',
    left: '50%',
    width: metrics.nearbyEmpty.stackThumbWidth,
  },
  footer: {
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
  },
});
