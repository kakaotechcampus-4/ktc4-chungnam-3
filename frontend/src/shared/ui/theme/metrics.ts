// Figma 변수 아님, 노드 실측값. 변수에 바인딩되지 않은 치수를 컴포넌트별로 둔다.
export const metrics = {
  // C/TopTabs 2054:172. 탭 높이 28(글자 20 + 간격 6 + 밑줄 2) → 상하 10 씩 48dp. 좌우 10 은 탭 간격 20 의 절반.
  topTabs: {
    labelGap: 6,
    underlineWidth: 24,
    underlineHeight: 2,
    hitSlop: { top: 10, bottom: 10, left: 10, right: 10 },
  },
  // C/Thumb 2054:102. 9:16 세로. 재생 배지(source/Shorts 2054:104)와 그 안의 Play 위치.
  thumb: {
    aspectRatio: 9 / 16,
    sourceSize: 20,
    sourceInset: 6,
    playSize: 12,
    playOffsetLeft: 4.5,
    playOffsetTop: 4,
  },
  // C/Badge 2054:150.
  badge: {
    paddingVertical: 3,
  },
  // C/Button 2054:157. 14 + 글자 20 + 14 = 높이 48.
  button: {
    paddingVertical: 14,
  },
} as const;
