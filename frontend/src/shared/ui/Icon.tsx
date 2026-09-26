// 아이콘. Figma C/Icon/* 은 lucide 경로다. 번들 크기 때문에 아이콘별 경로로 import 한다.
import type { LucideIcon } from 'lucide-react-native';
import ArrowLeft from 'lucide-react-native/icons/arrow-left';
import Check from 'lucide-react-native/icons/check';
import ChevronRight from 'lucide-react-native/icons/chevron-right';
import MapPin from 'lucide-react-native/icons/map-pin';
import Pencil from 'lucide-react-native/icons/pencil';
import X from 'lucide-react-native/icons/x';
import Svg, { Path } from 'react-native-svg';

import { colors } from './theme';

export type IconName = 'pin' | 'arrowLeft' | 'close' | 'check' | 'chevronRight' | 'pencil' | 'play';

const LUCIDE: Record<Exclude<IconName, 'play'>, LucideIcon> = {
  pin: MapPin,
  arrowLeft: ArrowLeft,
  close: X,
  check: Check,
  chevronRight: ChevronRight,
  pencil: Pencil,
};

const STROKE_WIDTH = 1.75;

type Props = {
  name: IconName;
  size: number;
  color?: string;
};

export default function Icon({ name, size, color = colors.icon.default }: Props) {
  if (name === 'play') {
    // Figma 전용 경로, lucide 아님. C/Icon/Play 2054:101 은 선 없는 채운 삼각형이다.
    return (
      <Svg width={size} height={size} viewBox="0 0 24 24">
        <Path d="M8 5.5V18.5L18.5 12L8 5.5Z" fill={color} />
      </Svg>
    );
  }
  const Glyph = LUCIDE[name];
  return <Glyph size={size} color={color} strokeWidth={STROKE_WIDTH} />;
}
