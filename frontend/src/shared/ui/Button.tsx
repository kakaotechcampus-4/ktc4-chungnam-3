// 버튼. Figma C/Button (Primary / Secondary / Text).
import { Pressable, type StyleProp, StyleSheet, Text, type ViewStyle } from 'react-native';

import { colors, metrics, radius, spacing, stroke, typography } from './theme';

export type ButtonKind = 'primary' | 'secondary' | 'text';

type Props = {
  kind: ButtonKind;
  label: string;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
};

export default function Button({ kind, label, onPress, style }: Props) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        containerStyles[kind],
        pressed && kind === 'primary' && styles.primaryPressed,
        style,
      ]}
    >
      <Text style={[typography.labelButton, labelStyles[kind]]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: metrics.button.paddingVertical,
    borderRadius: radius.md,
  },
  primaryPressed: {
    backgroundColor: colors.brand.primaryPressed,
  },
});

const containerStyles = StyleSheet.create({
  primary: {
    backgroundColor: colors.brand.primary,
  },
  secondary: {
    backgroundColor: colors.bg.surface,
    borderWidth: stroke.thin,
    borderColor: colors.border.default,
  },
  text: {},
});

const labelStyles = StyleSheet.create({
  primary: {
    color: colors.text.onPrimary,
  },
  secondary: {
    color: colors.text.primary,
  },
  text: {
    color: colors.text.onSubtle,
  },
});
