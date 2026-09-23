// 07b 전체 기억 빈 상태. 첫 공유를 안내하고 빈 점선 액자를 보여준다. 문구는 Figma 고정.
import { StyleSheet, Text, View } from 'react-native';

import Icon from '../../../shared/ui/Icon';
import { colors, metrics, radius, size, spacing, stroke, typography } from '../../../shared/ui/theme';

export default function ArchiveEmpty() {
  return (
    <View>
      <View style={styles.intro}>
        <Text style={styles.heading}>아직 저장한 곳이 없어요</Text>
        <Text style={styles.body}>
          Shorts에서 공유를 누르고 REMEMBRALL을 고르면, 영상 속 장소를 찾아 여기에 사진처럼 쌓아둘게요.
        </Text>
      </View>

      <View style={styles.frames}>
        {metrics.archiveEmpty.frames.map((frame, index) => (
          <View
            key={frame.dx}
            style={[
              styles.frame,
              { top: frame.top, marginLeft: frame.dx - metrics.archiveEmpty.frameWidth / 2 },
            ]}
          >
            {index === 1 && <Icon name="pin" size={size.iconMd} color={colors.text.secondary} />}
          </View>
        ))}
      </View>

      <View style={styles.footer}>
        <Text style={styles.caption}>처음 저장한 곳부터 여기서 시간이 흐르기 시작해요</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  intro: {
    paddingTop: metrics.archiveEmpty.introPaddingTop,
    paddingHorizontal: spacing.lg,
    gap: spacing.sm,
  },
  heading: {
    ...typography.headingScreen,
    color: colors.text.primary,
  },
  body: {
    ...typography.bodyDefault,
    color: colors.text.secondary,
  },
  frames: {
    height: metrics.archiveEmpty.framesHeight,
    overflow: 'hidden',
  },
  frame: {
    position: 'absolute',
    left: '50%',
    width: metrics.archiveEmpty.frameWidth,
    height: metrics.archiveEmpty.frameHeight,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: stroke.medium,
    borderStyle: 'dashed',
    borderColor: colors.border.default,
    borderRadius: radius.md,
  },
  footer: {
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
  },
  caption: {
    ...typography.captionMeta,
    color: colors.text.secondary,
    textAlign: 'center',
  },
});
