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
  // C/Chip 2054:139. 높이 34 → 상하 8 씩 50dp.
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    hitSlop: { top: 8, bottom: 8 },
  },
  // C/Card/Nearby 2054:120.
  nearbyCard: {
    thumbWidth: 112,
    headGap: 2,
    nowPaddingHorizontal: 10,
    nowPaddingVertical: 3,
    summaryMaxLines: 3,
  },
  // C/Card/Memory 2054:107.
  memoryCard: {
    thumbWidth: 72,
    headGap: 2,
    summaryMaxLines: 2,
  },
  // 01 근처 2061:760.
  proposal: {
    introPaddingTop: 24,
    introGap: 6,
    locationIconSize: 16,
    listPaddingTop: 16,
  },
  // 06 근처 빈 상태 2061:881. 링크 높이 20 → 상하 14 씩 48dp.
  // stack: Figma 360 폭 절대좌표를 썸네일 중심 기준으로 바꾼 값. dx 는 화면 가운데로부터의 거리.
  nearbyEmpty: {
    linkGap: 2,
    linkIconSize: 16,
    linkHitSlop: { top: 14, bottom: 14 },
    stackHeight: 300,
    stackThumbWidth: 104,
    stack: [
      { dx: -47.1, top: 63, rotate: '9deg' },
      { dx: 50.9, top: 41, rotate: '-7deg' },
      { dx: 2.4, top: 40.9, rotate: '1deg' },
    ],
  },
} as const;
