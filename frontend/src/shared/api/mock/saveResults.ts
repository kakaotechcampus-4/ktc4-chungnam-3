// 저장 결과 목 데이터. resultId 로 찾는다. 화면 props 모양 그대로이며 서버 계약이 아니다.
// 문구는 Figma 02~05 그대로. 제목의 따옴표는 컴포넌트가 붙인다.
const img = (id: number) => `https://picsum.photos/id/${id}/360/640`;

const breadSource = {
  thumbUri: img(431),
  meta: '방금 저장 · YouTube Shorts',
  title: '대전 가면 꼭 들르는 빵집 3곳',
  handle: '@daejeon.bread',
};

export const saveResultsMock = {
  // 02 저장 · 확인 필요 (2061:784)
  'needs-confirmation': {
    state: 'needsConfirmation',
    source: breadSource,
    candidates: [
      { id: 'sungsimdang-main', name: '성심당 본점', address: '대전 중구 은행동' },
      { id: 'sungsimdang-dcc', name: '성심당 DCC점', address: '대전 유성구 도룡동' },
    ],
  },
  // 03 저장 · 분석 중 (2061:816)
  analyzing: {
    state: 'analyzing',
    source: breadSource,
  },
  // 04 저장 · 장소 없음 (2061:842)
  'no-place': {
    state: 'noPlace',
    source: {
      thumbUri: img(1036),
      meta: '방금 저장 · YouTube Shorts',
      title: '비 오는 날 틀어두기 좋은 카페 플레이리스트',
      handle: '@rainy.cafe.mood',
    },
  },
  // 05 저장 · 분석 실패 (2061:862). 영상을 불러오지 못해 이미지와 제목이 없다.
  failed: {
    state: 'failed',
    source: {
      meta: '방금 저장 · YouTube Shorts',
      url: 'youtube.com/shorts/k3Qx…',
    },
  },
} as const;
