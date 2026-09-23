// Figma `C 바랜기억/` 텍스트 스타일. 커스텀 폰트는 굵기마다 fontFamily 를 따로 쓰고 fontWeight 를 지정하지 않는다.
import type { TextStyle } from 'react-native';

// useFonts 등록 키와 같아야 한다.
export const fontFamily = {
  gowunDodumRegular: 'GowunDodum_400Regular',
  notoSansKrRegular: 'NotoSansKR_400Regular',
  notoSansKrMedium: 'NotoSansKR_500Medium',
} as const;

export const typography = {
  headingScreen: {
    fontFamily: fontFamily.gowunDodumRegular,
    fontSize: 22,
    lineHeight: 30,
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  titleCard: {
    fontFamily: fontFamily.notoSansKrMedium,
    fontSize: 16,
    lineHeight: 22,
    letterSpacing: -0.1,
    includeFontPadding: false,
  },
  bodyDefault: {
    fontFamily: fontFamily.notoSansKrRegular,
    fontSize: 14,
    lineHeight: 22,
    letterSpacing: 0,
    includeFontPadding: false,
  },
  labelButton: {
    fontFamily: fontFamily.notoSansKrMedium,
    fontSize: 14,
    lineHeight: 20,
    letterSpacing: 0,
    includeFontPadding: false,
  },
  captionMeta: {
    fontFamily: fontFamily.notoSansKrRegular,
    fontSize: 12,
    lineHeight: 16,
    letterSpacing: 0,
    includeFontPadding: false,
  },
} as const satisfies Record<string, TextStyle>;
