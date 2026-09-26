// Figma `C 바랜기억/` effect 스타일. RN boxShadow (New Architecture).
import type { ViewStyle } from 'react-native';

export const effects = {
  elevationLow: [
    { offsetX: 0, offsetY: 2, blurRadius: 12, spreadDistance: 0, color: '#2a212612' },
  ],
} as const satisfies Record<string, ViewStyle['boxShadow']>;
