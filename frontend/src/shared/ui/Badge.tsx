// 저장물 상태 배지. Figma C/Badge. 상태 → 라벨·색 매핑은 이 파일에만 둔다.
// UI 타입이다. 서버 이름과의 대응은 백엔드 계약이 생기면 mappers 에서 한다.
import { type StyleProp, StyleSheet, Text, View, type ViewStyle } from 'react-native';

import { colors, metrics, radius, spacing, typography } from './theme';

export type BadgeStatus = 'analyzing' | 'needsConfirmation' | 'noPlace' | 'partial' | 'failed';

const BADGE: Record<BadgeStatus, { label: string; fg: string; bg: string }> = {
  analyzing: { label: '분석 중', fg: colors.status.infoFg, bg: colors.status.infoBg },
  needsConfirmation: { label: '확인 필요', fg: colors.status.warningFg, bg: colors.status.warningBg },
  noPlace: { label: '장소 없음', fg: colors.status.neutralFg, bg: colors.status.neutralBg },
  partial: { label: '부분 성공', fg: colors.status.neutralFg, bg: colors.status.neutralBg },
  failed: { label: '실패', fg: colors.status.dangerFg, bg: colors.status.dangerBg },
};

type Props = {
  status: BadgeStatus;
  style?: StyleProp<ViewStyle>;
};

export default function Badge({ status, style }: Props) {
  const { label, fg, bg } = BADGE[status];
  return (
    <View style={[styles.badge, { backgroundColor: bg }, style]}>
      <Text style={[typography.captionMeta, { color: fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing.sm,
    paddingVertical: metrics.badge.paddingVertical,
    borderRadius: radius.sm,
  },
});
